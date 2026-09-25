package pocketpaystore.pocketpay_batch.job.paymentalertretry.writer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.mapper.business.PaymentAlertRetryMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAlertRetryStateService {

	private final PaymentAlertRetryMapper mapper;
	private final RestClient restClient = RestClient.create();

	@Value("${slack.webhook-url:}")
	private String webhookUrl;

	public void retry(long alertId, String message) {
		if (webhookUrl == null || webhookUrl.isBlank()) {
			log.info("[PaymentAlertRetry] webhook URL이 없어 재시도를 건너뜀: alertId={}", alertId);
			return;
		}
		try {
			restClient.post().uri(webhookUrl).body(new SlackMessage(message)).retrieve().toBodilessEntity();
			markResolved(alertId);
		} catch (Exception e) {
			log.error("[PaymentAlertRetry] 재시도 실패, 다음 실행에서 다시 시도: alertId={}", alertId, e);
			markFailed(alertId);
		}
	}

	@Transactional("businessTransactionManager")
	public void markResolved(long alertId) {
		mapper.markResolved(alertId);
	}

	@Transactional("businessTransactionManager")
	public void markFailed(long alertId) {
		mapper.markFailed(alertId);
	}

	private record SlackMessage(String text) { }
}
