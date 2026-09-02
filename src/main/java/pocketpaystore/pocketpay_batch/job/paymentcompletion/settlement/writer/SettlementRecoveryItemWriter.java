package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.parameter.SettlementRecoveryJobParameter;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementRecoveryItemWriter implements ItemWriter<SettlementRecoveryCandidate> {

	private final SettlementRecoveryStateService stateService;
	private final SettlementRecoveryJobParameter parameter;

	@Override
	public void write(Chunk<? extends SettlementRecoveryCandidate> chunk) {
		for (SettlementRecoveryCandidate candidate : chunk.getItems()) recover(candidate);
	}

	private void recover(SettlementRecoveryCandidate candidate) {
		try {
			boolean claimed = stateService.recover(candidate.getAlertId(), candidate.getPaymentId(), parameter.getStaleMinutes());
			if (!claimed) {
				log.info("[SettlementRecovery] 다른 인스턴스가 처리 중이거나 이미 종결된 알림: alertId={}", candidate.getAlertId());
			}
		} catch (Exception e) {
			log.error("[SettlementRecovery] 정산 재처리 실패, 다음 회차에 재시도: alertId={}, paymentId={}",
					candidate.getAlertId(), candidate.getPaymentId(), e);
			stateService.markFailed(candidate.getAlertId());
		}
	}
}
