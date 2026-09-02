package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementCreationCandidate;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCreationItemWriter implements ItemWriter<SettlementCreationCandidate> {

	private final SettlementCreationStateService stateService;

	@Override
	public void write(Chunk<? extends SettlementCreationCandidate> chunk) {
		for (SettlementCreationCandidate candidate : chunk.getItems()) {
			try {
				if (!stateService.create(candidate)) {
					log.info("[SettlementCreation] 이미 생성된 정산: paymentId={}", candidate.getPaymentId());
				}
			} catch (Exception e) {
				log.error("[SettlementCreation] 정산 생성 실패, 다음 실행에서 재시도: paymentId={}",
						candidate.getPaymentId(), e);
			}
		}
	}
}
