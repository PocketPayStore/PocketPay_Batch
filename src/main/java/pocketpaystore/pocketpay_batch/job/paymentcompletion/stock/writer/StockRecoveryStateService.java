package pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.writer;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.mapper.business.StockRecoveryMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockRecoveryStateService {

	private final RedissonClient redissonClient;
	private final StockRecoveryMapper mapper;

	@Value("${lock.default-wait-time-seconds:5}")
	private long waitTimeSeconds;

	@Value("${lock.default-lease-time-seconds:3}")
	private long leaseTimeSeconds;

	@Transactional("businessTransactionManager")
	public boolean recover(long alertId, long orderId, long staleMinutes) {
		if (mapper.claim(alertId, staleMinutes) == 0) {
			return false;
		}
		Long productId = mapper.findProductIdByOrderId(orderId);
		if (productId == null) {
			throw new IllegalStateException("[StockRecovery] 주문의 order_item을 찾을 수 없습니다: orderId=" + orderId);
		}

		RLock lock = redissonClient.getLock("lock:stock:" + productId);
		try {
			if (!lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS)) {
				throw new IllegalStateException("[StockRecovery] 재고 락 획득 실패: productId=" + productId);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("[StockRecovery] 재고 락 대기 중 인터럽트: productId=" + productId, e);
		}
		try {
			int updated = mapper.confirmStock(orderId);
			if (updated == 0) {
				log.info("[StockRecovery] 이미 확정된 재고로 판단해 재처리를 건너뜁니다: orderId={}, productId={}", orderId, productId);
			}
			mapper.markResolved(alertId);
			return true;
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	@Transactional("businessTransactionManager")
	public void markFailed(long alertId) {
		mapper.markFailed(alertId);
	}
}
