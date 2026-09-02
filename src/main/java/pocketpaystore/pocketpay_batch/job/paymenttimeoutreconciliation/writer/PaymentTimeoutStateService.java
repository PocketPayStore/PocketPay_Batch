package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.event.PaymentStatusChangedEvent;
import pocketpaystore.pocketpay_batch.mapper.business.PaymentTimeoutReconciliationMapper;
import pocketpaystore.pocketpay_batch.mapper.business.dto.PointReservationContext;

@Service
@RequiredArgsConstructor
public class PaymentTimeoutStateService {
	private final PaymentTimeoutReconciliationMapper mapper;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional("businessTransactionManager")
	public boolean markPaidIfStillTimeoutUnknown(Long paymentId, Long orderId, String orderNumber) {
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
}
