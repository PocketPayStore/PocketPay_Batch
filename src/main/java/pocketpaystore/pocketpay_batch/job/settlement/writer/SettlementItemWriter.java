package pocketpaystore.pocketpay_batch.job.settlement.writer;

import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.settlement.dto.SettlementCandidate;
import pocketpaystore.pocketpay_batch.job.settlement.parameter.SettlementJobParameter;

@Slf4j
@Component
@RequiredArgsConstructor
@StepScope
public class SettlementItemWriter implements ItemWriter<SettlementCandidate> {
	private final SettlementStateService stateService;
	private final SettlementJobParameter parameter;

	@Override
	public void write(Chunk<? extends SettlementCandidate> chunk) {
		int savedCount = stateService.saveSummaries(chunk.getItems(),
				parameter.getStartDate(), parameter.getEndDate());
		List<Long> vendorIds = chunk.getItems().stream()
				.map(SettlementCandidate::getVendorId)
				.toList();
		int settledCount = stateService.markSourceSettled(vendorIds,
				parameter.getStartDate(), parameter.getEndDate());
		log.info("[Settlement] 가맹점별 정산 집계 저장: 가맹점={}, 집계={}, 원천 정산 완료={}",
				vendorIds.size(), savedCount, settledCount);
	}

}
