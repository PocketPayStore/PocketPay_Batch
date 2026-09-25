package pocketpaystore.pocketpay_batch.job.pointearnretry.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.pointearnretry.dto.PointEarnCandidate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointEarnRetryItemWriter implements ItemWriter<PointEarnCandidate> {

	private final PointEarnRetryStateService stateService;

	@Override
	public void write(Chunk<? extends PointEarnCandidate> chunk) {
		for (PointEarnCandidate candidate : chunk.getItems()) {
			try {
				stateService.retry(candidate);
			} catch (Exception e) {
				log.error("[PointEarnRetry] 적립 재시도 처리 중 예상치 못한 실패: id={}", candidate.getId(), e);
			}
		}
	}
}
