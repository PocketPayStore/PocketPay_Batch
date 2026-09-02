package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.writer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryContext;
import pocketpaystore.pocketpay_batch.mapper.business.PointRecoveryMapper;
import pocketpaystore.pocketpay_batch.mapper.business.dto.PointReservationContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointRecoveryStateService {

	private static final String POINT_USE = "POINT_USE";
	private static final String POINT_EARN = "POINT_EARN";

	private final PointRecoveryMapper mapper;

	@Value("${payment-completion.point-earn-rate}")
	private double pointEarnRate;

	@Transactional("businessTransactionManager")
	public boolean recover(long alertId, long paymentId, long orderId, String completionStep, long staleMinutes) {
		if (mapper.claim(alertId, staleMinutes) == 0) {
			return false;
		}

		String ledgerType = POINT_USE.equals(completionStep) ? "USE" : "EARN";
		if (mapper.existsLedgerEntry(orderId, ledgerType)) {
			log.info("[PointRecovery] 이미 반영된 포인트 원장으로 판단해 재처리를 건너뜁니다: orderId={}, type={}", orderId, ledgerType);
			mapper.markResolved(alertId);
			return true;
		}

		PointRecoveryContext context = mapper.findContext(paymentId);
		if (context == null) {
			throw new IllegalStateException("[PointRecovery] payment/orders 조회 실패: paymentId=" + paymentId);
		}

		long amount = POINT_USE.equals(completionStep)
				? context.getUsedPointAmount()
				: Math.round(context.getAmount() * pointEarnRate);
		if (amount <= 0) {
			log.info("[PointRecovery] 반영할 포인트가 0 이하라 재처리를 건너뜁니다: orderId={}, type={}", orderId, ledgerType);
			mapper.markResolved(alertId);
			return true;
		}
		if (POINT_USE.equals(completionStep)) {
			PointReservationContext reservation = mapper.findPointReservationForUpdate(paymentId);
			if (reservation != null) {
				confirmReservation(alertId, paymentId, orderId, reservation);
				return true;
			}
		}

		long delta = POINT_USE.equals(completionStep) ? -amount : amount;
		Long currentBalance = mapper.findBalanceForUpdate(context.getMemberId());
		if (currentBalance == null) {
			throw new IllegalStateException("[PointRecovery] point_balance가 없습니다: memberId=" + context.getMemberId());
		}
		long balanceAfter = currentBalance + delta;
		if (balanceAfter < 0) {
			throw new IllegalStateException("[PointRecovery] 잔액 부족으로 복구 불가: memberId=" + context.getMemberId()
					+ ", currentBalance=" + currentBalance + ", delta=" + delta);
		}

		mapper.updateBalance(context.getMemberId(), balanceAfter);
		mapper.insertLedger(context.getMemberId(), orderId, ledgerType, delta, balanceAfter);
		mapper.markResolved(alertId);
		return true;
	}

	private void confirmReservation(long alertId, long paymentId, long orderId,
			PointReservationContext reservation) {
		if (!"RESERVED".equals(reservation.getStatus())) {
			mapper.markResolved(alertId);
			return;
		}
		long balanceAfter = reservation.getBalance() - reservation.getAmount();
		if (balanceAfter < 0 || reservation.getReservedAmount() < reservation.getAmount()) {
			throw new IllegalStateException("[PointRecovery] 포인트 예약 확정 불가: paymentId=" + paymentId);
		}
		if (mapper.updateBalanceForReservationConfirmation(
				reservation.getMemberId(), reservation.getAmount()) != 1
				|| mapper.confirmPointReservation(paymentId) != 1) {
			throw new IllegalStateException("[PointRecovery] 포인트 예약 확정 결과 불일치: paymentId=" + paymentId);
		}
		mapper.insertLedger(reservation.getMemberId(), orderId, "USE",
				-reservation.getAmount(), balanceAfter);
		mapper.markResolved(alertId);
	}

	@Transactional("businessTransactionManager")
	public void markFailed(long alertId) {
		mapper.markFailed(alertId);
	}
}
