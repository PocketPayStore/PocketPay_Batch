package pocketpaystore.pocketpay_batch.job.refundreconciliation.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossCancelResponse;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer.TossPgClient;
import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RefundReconciliationJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("refundReconciliationJob")
	private Job refundReconciliationJob;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	@MockitoBean
	private TossPgClient tossPgClient;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(refundReconciliationJob);
	}

	@Test
	@DisplayName("PROCESSING에 멈춰있던 환불은 PG 취소 재시도 성공 시 완료 처리되고 감사 기록도 남는다")
	void run_completesStuckProcessingRefund() throws Exception {
		long paymentId = seedPayment();
		long refundId = seedRefund(paymentId, "PROCESSING", false, -30);
		when(tossPgClient.cancel(any(), any(), any())).thenReturn(new TossCancelResponse("PG-TX-1", "CANCELED"));

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(refundStatus(refundId)).isEqualTo("COMPLETED");
		assertThat(pgCancelConfirmed(refundId)).isTrue();
		assertThat(paymentCancelCount(refundId)).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 COMPLETED인데 PG 취소 확인만 안 된 환불은, 취소 재시도 성공 시 확인 플래그만 갱신되고 감사 기록은 중복 생성되지 않는다")
	void run_confirmsPgCancelForAlreadyCompletedRefund() throws Exception {
		long paymentId = seedPayment();
		long refundId = seedRefund(paymentId, "COMPLETED", false, -30);
		when(tossPgClient.cancel(any(), any(), any())).thenReturn(new TossCancelResponse("PG-TX-1", "CANCELED"));

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(refundStatus(refundId)).isEqualTo("COMPLETED");
		assertThat(pgCancelConfirmed(refundId)).isTrue();
		assertThat(paymentCancelCount(refundId)).isEqualTo(0);
	}

	@Test
	@DisplayName("PG 취소 재시도가 다시 실패하면 로컬 완료 처리는 진행되지만 확인 플래그는 그대로 남아 다음 회차에 재시도된다")
	void run_pgCancelRetryFails_leavesConfirmationFlagForNextRun() throws Exception {
		long paymentId = seedPayment();
		long refundId = seedRefund(paymentId, "PROCESSING", false, -30);
		when(tossPgClient.cancel(any(), any(), any())).thenThrow(new RuntimeException("connection refused"));

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(refundStatus(refundId)).isEqualTo("COMPLETED");
		assertThat(pgCancelConfirmed(refundId)).isFalse();
		assertThat(paymentCancelCount(refundId)).isEqualTo(1);
	}

	private long seedPayment() {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '환불대사테스트', 'USER', NOW(6), NOW(6))",
				"refund-reconcile-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('환불대사업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '환불대사카드', 10000, NOW(6), NOW(6))",
				vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, 'PAID', ?, NOW(6), NOW(6))",
				"ORDER-" + suffix, memberId, "IDEM-ORDER-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?, ?, 1, 10000, NOW(6), NOW(6))",
				orderId, productId);

		jdbcTemplate.update(
				"INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, refundable_amount, status, created_at, updated_at) "
						+ "VALUES (?, 'CARD', 'mock-pg', 'PG-TX-1', ?, 10000, 0, 0, 'CANCELED', NOW(6), NOW(6))",
				orderId, "IDEM-PAY-" + suffix);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long seedRefund(long paymentId, String status, boolean pgCancelConfirmed, int requestedAtOffsetMinutes) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO refund (payment_id, request_amount, status, pg_cancel_confirmed, idempotency_key, requested_at, created_at, updated_at) "
						+ "VALUES (?, 10000, ?, ?, ?, TIMESTAMPADD(MINUTE, ?, NOW(6)), NOW(6), NOW(6))",
				paymentId, status, pgCancelConfirmed, "IDEM-REFUND-" + suffix, requestedAtOffsetMinutes);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String refundStatus(long refundId) {
		return jdbcTemplate.queryForObject("SELECT status FROM refund WHERE id = ?", String.class, refundId);
	}

	private boolean pgCancelConfirmed(long refundId) {
		return jdbcTemplate.queryForObject("SELECT pg_cancel_confirmed FROM refund WHERE id = ?", Boolean.class, refundId);
	}

	private int paymentCancelCount(long refundId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payment_cancel WHERE refund_id = ?", Integer.class, refundId);
	}

}
