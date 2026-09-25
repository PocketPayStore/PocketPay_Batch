package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossCancelRequest;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossCancelResponse;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossPaymentResponse;

@Component
public class TossPgClient {

	private final RestClient restClient;

	public TossPgClient(@Value("${toss.base-url}") String baseUrl, @Value("${toss.secret-key}") String secretKey) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(2000);
		requestFactory.setReadTimeout(3000);
		String credentials = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.defaultHeader("Authorization", "Basic " + credentials)
				.build();
	}

	public TossPaymentResponse getPayment(String paymentKey) {
		return restClient.get()
				.uri("/v1/payments/{paymentKey}", paymentKey)
				.retrieve()
				.body(TossPaymentResponse.class);
	}

	public TossCancelResponse cancel(String paymentKey, String idempotencyKey, TossCancelRequest request) {
		return restClient.post()
				.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
				.header("Idempotency-Key", idempotencyKey)
				.body(request)
				.retrieve()
				.body(TossCancelResponse.class);
	}
}
