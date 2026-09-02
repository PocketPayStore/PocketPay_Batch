package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.mapper.business.SettlementRecoveryMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementRecoveryStateService {

	private final SettlementRecoveryMapper mapper;

	@Value("${settlement.pg-fee-rate}")
	private double pgFeeRate;

	@Value("${settlement.platform-fee-rate}")
	private double platformFeeRate;

	@Transactional("businessTransactionManager")
	public boolean recover(long alertId, long paymentId, long staleMinutes) {
		if (mapper.claim(alertId, staleMinutes) == 0) {
			return false;
		}
		int inserted = mapper.insertSettlement(paymentId, pgFeeRate, platformFeeRate);
		if (inserted == 0) {
			log.info("[SettlementRecovery] 이미 생성된 정산 행으로 판단해 재처리를 건너뜁니다: paymentId={}", paymentId);
		}
		mapper.markResolved(alertId);
		return true;
	}

	@Transactional("businessTransactionManager")
	public void markFailed(long alertId) {
		mapper.markFailed(alertId);
	}
}
