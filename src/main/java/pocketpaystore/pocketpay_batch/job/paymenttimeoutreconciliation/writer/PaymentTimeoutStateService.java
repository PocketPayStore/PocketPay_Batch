package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.event.PaymentStatusChangedEvent;
import pocketpaystore.pocketpay_batch.mapper.business.OrderItemRow;
import pocketpaystore.pocketpay_batch.mapper.business.PaymentTimeoutReconciliationMapper;
import pocketpaystore.pocketpay_batch.mapper.business.dto.PointReservationContext;

@Service
@RequiredArgsConstructor
public class PaymentTimeoutStateService {
	private final PaymentTimeoutReconciliationMapper mapper;
	private final ApplicationEventPublisher eventPublisher;
	private final RedissonClient redissonClient;

	@Value("${payment-completion.point-earn-rate}")
	private double pointEarnRate;

	@Value("${lock.default-wait-time-seconds:5}")
	private long waitTimeSeconds;

	@Transactional("businessTransactionManager")
	public boolean markPaidIfStillTimeoutUnknown(Long paymentId, Long orderId, String orderNumber, Long amount, Long memberId) {
		int paymentUpdated = mapper.markPaymentDone(paymentId);
		if (paymentUpdated == 0) {
			return false;
		}
		confirmPointReservation(paymentId, orderId);
		int orderUpdated = mapper.markOrderPaid(orderId);
		if (orderUpdated != 1) {
			throw new IllegalStateException("[PaymentTimeout] payment/order 상태 보정 결과 불일치: paymentId="
					+ paymentId + ", orderId=" + orderId);
		}
		mapper.savePaymentStatusHistory(paymentId);

		earnPoints(memberId, orderId, paymentId, amount);
		confirmStock(orderId);

		eventPublisher.publishEvent(PaymentStatusChangedEvent.create(paymentId, orderId, orderNumber));
		return true;
	}

	@Transactional("businessTransactionManager")
	public boolean markFailedIfStillTimeoutUnknown(Long paymentId, Long orderId, String orderNumber) {
		if (mapper.markPaymentFailed(paymentId) == 0) {
			return false;
		}
		releasePointReservation(paymentId);
		mapper.saveFailedPaymentStatusHistory(paymentId);
		eventPublisher.publishEvent(PaymentStatusChangedEvent.create(paymentId, orderId, orderNumber));
		return true;
	}

	private void confirmPointReservation(Long paymentId, Long orderId) {
		PointReservationContext reservation = mapper.findPointReservationForUpdate(paymentId);
		long usedPointAmount = mapper.findUsedPointAmount(paymentId);
		if (usedPointAmount == 0) {
			return;
		}
		validateReservedPointReservation(paymentId, reservation, usedPointAmount);
		long balanceAfter = reservation.getBalance() - reservation.getAmount();
		if (balanceAfter < 0 || reservation.getReservedAmount() < reservation.getAmount()) {
			throw new IllegalStateException("[PaymentTimeout] 포인트 예약 확정 불가: paymentId=" + paymentId);
		}
		if (mapper.updatePointBalanceForConfirmation(reservation.getMemberId(), reservation.getAmount()) != 1
				|| mapper.confirmPointReservation(paymentId) != 1) {
			throw new IllegalStateException("[PaymentTimeout] 포인트 예약 확정 결과 불일치: paymentId=" + paymentId);
		}
		mapper.insertPointUseLedger(
				reservation.getMemberId(), orderId, reservation.getAmount(), balanceAfter);
	}

	private void releasePointReservation(Long paymentId) {
		PointReservationContext reservation = mapper.findPointReservationForUpdate(paymentId);
		long usedPointAmount = mapper.findUsedPointAmount(paymentId);
		if (usedPointAmount == 0) {
			return;
		}
		validateReservedPointReservation(paymentId, reservation, usedPointAmount);
		if (mapper.updatePointBalanceForRelease(reservation.getMemberId(), reservation.getAmount()) != 1
				|| mapper.releasePointReservation(paymentId) != 1) {
			throw new IllegalStateException("[PaymentTimeout] 포인트 예약 해제 결과 불일치: paymentId=" + paymentId);
		}
	}

	private void validateReservedPointReservation(Long paymentId, PointReservationContext reservation,
			long usedPointAmount) {
		if (reservation == null || !"RESERVED".equals(reservation.getStatus())
				|| reservation.getAmount() != usedPointAmount) {
			throw new IllegalStateException("[PaymentTimeout] 유효한 포인트 예약이 없습니다: paymentId=" + paymentId);
		}
	}

	private void earnPoints(Long memberId, Long orderId, Long paymentId, Long amount) {
		long earnAmount = Math.round(amount * pointEarnRate);
		mapper.insertPointEarnLog(memberId, orderId, paymentId, earnAmount);
	}

	private void confirmStock(Long orderId) {
		OrderItemRow item = mapper.findOrderItem(orderId);
		if (item == null) {
			throw new IllegalStateException("[PaymentTimeout] 주문 상품을 찾지 못함: orderId=" + orderId);
		}
		RLock lock = redissonClient.getLock("lock:stock:" + item.getProductId());
		boolean locked;
		try {
			locked = lock.tryLock(waitTimeSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("[PaymentTimeout] 재고 확정 락 대기 중 인터럽트: orderId=" + orderId, e);
		}
		if (!locked) {
			throw new IllegalStateException("[PaymentTimeout] 재고 확정 락 획득 실패: orderId=" + orderId);
		}
		try {
			if (mapper.confirmStock(item.getProductId(), item.getQuantity()) == 0) {
				throw new IllegalStateException("[PaymentTimeout] 재고 확정 UPDATE 0건: orderId=" + orderId);
			}
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}
}
