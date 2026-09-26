package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.mapper.business.OrderExpirationMapper;
import pocketpaystore.pocketpay_batch.mapper.business.OrderItemRow;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReleaseService {

	private final RedissonClient redissonClient;
	private final OrderExpirationMapper mapper;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${lock.default-wait-time-seconds:5}")
	private long waitTimeSeconds;

	@Value("${lock.default-lease-time-seconds:3}")
	private long leaseTimeSeconds;

	/**
	 * 주문 하나 단위로 독립된 트랜잭션에서 처리한다. 여러 주문을 한 트랜잭션으로 묶으면, 그중 한 주문의 상품 락 획득이
	 * 실패했을 때 같은 청크에 있던 다른(무관한) 상품의 주문 만료까지 전부 롤백되는 도미노 효과가 생긴다.
	 */
	@Transactional("businessTransactionManager")
	public void expireAndReleaseOrder(Long orderId) {
		List<Long> orderIds = List.of(orderId);
		if (mapper.markExpiredIfStillStockReserved(orderIds) == 0) {
			return;
		}

		Map<Long, Integer> quantitiesByProduct = new TreeMap<>();
		for (OrderItemRow item : mapper.findOrderItemsByExpiredOrderIds(orderIds)) {
			quantitiesByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
		}
		if (quantitiesByProduct.isEmpty()) {
			throw new IllegalStateException("[Expiration] 만료 주문의 order_item이 없습니다: orderId=" + orderId);
		}

		List<RLock> acquiredLocks = new ArrayList<>();
		try {
			for (Long productId : quantitiesByProduct.keySet()) {
				RLock lock = redissonClient.getLock("lock:stock:" + productId);
				log.info("[배치] 재고 락 대기 시작: 주문 ID={}, 상품 ID={}", orderId, productId);
				if (!lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS)) {
					throw new IllegalStateException(
							"[Expiration] 재고 락 획득 실패: orderId=" + orderId + ", productId=" + productId);
				}
				log.info("[배치] 재고 락 획득: 주문 ID={}, 상품 ID={}", orderId, productId);
				acquiredLocks.add(lock);
			}
			eventPublisher.publishEvent(new StockReleaseLocksAcquiredEvent(new ArrayList<>(quantitiesByProduct.keySet())));
			for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
				if (mapper.releaseStock(entry.getKey(), entry.getValue()) == 0) {
					throw new IllegalStateException(
							"[Expiration] 재고 원복 UPDATE 0건: orderId=" + orderId + ", productId=" + entry.getKey());
				}
				log.info("[배치] 예약 재고 복구 완료: 주문 ID={}, 상품 ID={}, 복구 수량={}", orderId, entry.getKey(), entry.getValue());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("[Expiration] 재고 락 대기 중 인터럽트: orderId=" + orderId, e);
		} finally {
			for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
				RLock lock = acquiredLocks.get(i);
				if (lock.isHeldByCurrentThread()) {
					lock.unlock();
					log.info("[배치] 재고 락 해제: 주문 ID={}, 상품 ID={}", orderId, quantitiesByProduct.keySet().toArray()[i]);
				}
			}
		}
	}

}
