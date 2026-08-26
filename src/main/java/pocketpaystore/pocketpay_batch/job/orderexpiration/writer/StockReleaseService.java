package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
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

	@Value("${lock.default-wait-time-seconds:5}")
	private long waitTimeSeconds;

	@Value("${lock.default-lease-time-seconds:3}")
	private long leaseTimeSeconds;

	@Transactional("businessTransactionManager")
	public void expireAndRelease(List<Long> orderIds) {
		if (orderIds.isEmpty() || mapper.markExpiredIfStillStockReserved(orderIds) == 0) {
			return;
		}

		Map<Long, Integer> quantitiesByProduct = new TreeMap<>();
		for (OrderItemRow item : mapper.findOrderItemsByExpiredOrderIds(orderIds)) {
			quantitiesByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
		}
		if (quantitiesByProduct.isEmpty()) {
			throw new IllegalStateException("[Expiration] 만료 주문의 order_item이 없습니다: orderIds=" + orderIds);
		}

		List<RLock> acquiredLocks = new ArrayList<>();
		try {
			for (Long productId : quantitiesByProduct.keySet()) {
				RLock lock = redissonClient.getLock("lock:stock:" + productId);
				if (!lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS)) {
					throw new IllegalStateException("[Expiration] 재고 락 획득 실패: productId=" + productId);
				}
				acquiredLocks.add(lock);
			}
			for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
				if (mapper.releaseStock(entry.getKey(), entry.getValue()) == 0) {
					throw new IllegalStateException("[Expiration] 재고 원복 UPDATE 0건: productId=" + entry.getKey());
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("[Expiration] 재고 락 대기 중 인터럽트", e);
		} finally {
			for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
				RLock lock = acquiredLocks.get(i);
				if (lock.isHeldByCurrentThread()) lock.unlock();
			}
		}
	}

}
