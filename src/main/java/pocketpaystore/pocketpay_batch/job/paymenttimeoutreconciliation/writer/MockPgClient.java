package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.MockPgTransactionResponse;

@Component
public class MockPgClient {

	private final RestClient restClient;

	public MockPgClient(@Value("${mock-pg.base-url}") String baseUrl) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(2000);
		requestFactory.setReadTimeout(3000);
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
	}

	public MockPgTransactionResponse getTransaction(String pgTransactionId) {
		return restClient.get()
				.uri("/mock-pg/transactions/{txId}", pgTransactionId)
				.retrieve()
				.body(MockPgTransactionResponse.class);
	}
}
