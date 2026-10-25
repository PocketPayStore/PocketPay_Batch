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
			retry(candidate);
		}
	}

	private void retry(PointEarnCandidate candidate) {
		try {
			if (!stateService.apply(candidate)) {
				log.info("[PointEarnRetry] 이미 처리된 적립 건, 스킵: id={}", candidate.getId());
			}
		} catch (Exception e) {
			log.error("[PointEarnRetry] 재시도 실패, 다음 실행에서 다시 시도: id={}", candidate.getId(), e);
			markFailed(candidate.getId());
		}
	}

	private void markFailed(long id) {
		try {
			stateService.markFailed(id);
		} catch (Exception e) {
			log.error("[PointEarnRetry] 실패 표시 중 예상치 못한 실패: id={}", id, e);
		}
	}
}
