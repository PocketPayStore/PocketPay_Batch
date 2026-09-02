package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.parameter.PointRecoveryJobParameter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointRecoveryItemWriter implements ItemWriter<PointRecoveryCandidate> {

	private final PointRecoveryStateService stateService;
	private final PointRecoveryJobParameter parameter;

	@Override
	public void write(Chunk<? extends PointRecoveryCandidate> chunk) {
		for (PointRecoveryCandidate candidate : chunk.getItems()) recover(candidate);
	}

	private void recover(PointRecoveryCandidate candidate) {
		try {
			boolean claimed = stateService.recover(candidate.getAlertId(), candidate.getPaymentId(),
					candidate.getOrderId(), candidate.getCompletionStep(), parameter.getStaleMinutes());
			if (!claimed) {
				log.info("[PointRecovery] 다른 인스턴스가 처리 중이거나 이미 종결된 알림: alertId={}", candidate.getAlertId());
			}
		} catch (Exception e) {
			log.error("[PointRecovery] 포인트 재처리 실패, 다음 회차에 재시도: alertId={}, orderId={}, step={}",
					candidate.getAlertId(), candidate.getOrderId(), candidate.getCompletionStep(), e);
			stateService.markFailed(candidate.getAlertId());
		}
	}
}
