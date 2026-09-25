package pocketpaystore.pocketpay_batch.job.paymentalertretry.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymentalertretry.dto.PaymentAlertCandidate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAlertRetryItemWriter implements ItemWriter<PaymentAlertCandidate> {

	private final PaymentAlertRetryStateService stateService;

	@Override
	public void write(Chunk<? extends PaymentAlertCandidate> chunk) {
		for (PaymentAlertCandidate candidate : chunk.getItems()) {
			try {
				stateService.retry(candidate.getId(), candidate.formattedMessage());
			} catch (Exception e) {
				log.error("[PaymentAlertRetry] 알림 재시도 처리 중 예상치 못한 실패: alertId={}", candidate.getId(), e);
			}
		}
	}
}
