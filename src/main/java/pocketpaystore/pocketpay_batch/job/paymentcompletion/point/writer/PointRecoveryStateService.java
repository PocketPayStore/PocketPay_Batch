package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.writer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryContext;
import pocketpaystore.pocketpay_batch.mapper.business.PointRecoveryMapper;

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

	@Transactional("businessTransactionManager")
	public void markFailed(long alertId) {
		mapper.markFailed(alertId);
	}
}
