package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecalculationCandidate;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementRecalculationItemWriter implements ItemWriter<SettlementRecalculationCandidate> {

	private final SettlementRecalculationStateService stateService;

	@Override
	public void write(Chunk<? extends SettlementRecalculationCandidate> chunk) {
		for (SettlementRecalculationCandidate candidate : chunk.getItems()) {
			try {
				if (stateService.recalculate(candidate)) {
					log.warn("[SettlementRecalculation] 정산 생성 후 환불 반영해 재계산: settlementId={}, paymentId={}, newAmount={}",
							candidate.getSettlementId(), candidate.getPaymentId(), candidate.getNewAmount());
				}
			} catch (Exception e) {
				log.error("[SettlementRecalculation] 정산 재계산 실패, 다음 실행에서 재시도: settlementId={}",
						candidate.getSettlementId(), e);
			}
		}
	}
}
