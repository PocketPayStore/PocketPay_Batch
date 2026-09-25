package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossPaymentResponse;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer.TossPgClient;
import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentTimeoutReconciliationJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("paymentTimeoutReconciliationJob")
	private Job paymentTimeoutReconciliationJob;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	@MockitoBean
	private TossPgClient tossPgClient;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(paymentTimeoutReconciliationJob);
	}

	@Test
	@DisplayName("PG가 APPROVED로 확인해주면 TIMEOUT_UNKNOWN 결제를 DONE으로, 주문을 PAID로 정정한다")
	void run_confirmsApprovedPayment() throws Exception {
		long paymentId = seed("TIMEOUT_UNKNOWN", "PAYMENT_PENDING", -30);
		when(tossPgClient.getPayment(any()))
				.thenReturn(new TossPaymentResponse("PG-TX-1", "ORDER-TEST", "DONE", LocalDateTime.now()));

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(paymentStatus(paymentId)).isEqualTo("DONE");
	}

	private long seed(String paymentStatus, String orderStatus, int updatedAtOffsetMinutes) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '대사테스트', 'USER', NOW(6), NOW(6))",
				"reconcile-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO point_balance (member_id, balance, reserved_amount, created_at, updated_at) VALUES (?, 0, 0, NOW(6), NOW(6))",
				memberId);

		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('대사업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '대사카드', 10000, NOW(6), NOW(6))",
				vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at) VALUES (?, 10, 1, 0, NOW(6), NOW(6))",
				productId);

		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, ?, ?, NOW(6), NOW(6))",
				"ORDER-" + suffix, memberId, orderStatus, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?, ?, 1, 10000, NOW(6), NOW(6))",
				orderId, productId);

		jdbcTemplate.update(
				"INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, status, created_at, updated_at) "
						+ "VALUES (?, 'CARD', 'mock-pg', 'PG-TX-1', ?, 10000, 0, ?, NOW(6), TIMESTAMPADD(MINUTE, ?, NOW(6)))",
				orderId, "IDEM-PAY-" + suffix, paymentStatus, updatedAtOffsetMinutes);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String paymentStatus(long paymentId) {
		return jdbcTemplate.queryForObject("SELECT status FROM payment WHERE id = ?", String.class, paymentId);
	}

}
