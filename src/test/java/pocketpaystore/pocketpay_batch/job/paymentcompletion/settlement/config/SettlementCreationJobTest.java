package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

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

import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SettlementCreationJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("settlementCreationJob")
	private Job settlementCreationJob;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(settlementCreationJob);
	}

	@Test
	@DisplayName("정산이 아직 없는 완료 결제에 대해 정산 레코드를 새로 생성한다")
	void run_createsSettlementForCompletedPayment() throws Exception {
		long paymentId = seed(10_000L, 10_000L);

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(settlementAmount(paymentId)).isEqualTo(10_000L);
	}

	@Test
	@DisplayName("환불로 refundable_amount가 원래 amount보다 줄어든 결제는 그 줄어든 금액 기준으로 정산이 생성된다")
	void run_createsSettlementUsingRefundableAmount_whenAlreadyPartiallyRefunded() throws Exception {
		long paymentId = seed(10_000L, 6_000L);

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(settlementAmount(paymentId)).isEqualTo(6_000L);
	}

	@Test
	@DisplayName("정산 생성 후 환불이 들어와 refundable_amount가 줄면, 다음 실행에서 PENDING 정산이 그 값으로 재계산된다")
	void run_recalculatesExistingPendingSettlement_afterLaterRefund() throws Exception {
		long paymentId = seed(10_000L, 10_000L);
		JobParameters firstRun = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		jobLauncherTestUtils.launchJob(firstRun);
		assertThat(settlementAmount(paymentId)).isEqualTo(10_000L);

		jdbcTemplate.update("UPDATE payment SET refundable_amount = 4000 WHERE id = ?", paymentId);

		JobParameters secondRun = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(secondRun);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(settlementAmount(paymentId)).isEqualTo(4_000L);
	}

	@Test
	@DisplayName("이미 SETTLED로 확정된 정산은 그 뒤 refundable_amount가 바뀌어도 재계산 대상에서 제외된다")
	void run_doesNotRecalculateAlreadySettledSettlement() throws Exception {
		long paymentId = seed(10_000L, 10_000L);
		jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L).toJobParameters());
		jdbcTemplate.update("UPDATE settlement SET status = 'SETTLED', settled_at = NOW(6) WHERE payment_id = ?", paymentId);

		jdbcTemplate.update("UPDATE payment SET refundable_amount = 0 WHERE id = ?", paymentId);
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L).toJobParameters());

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(settlementAmount(paymentId)).isEqualTo(10_000L);
	}

	private long settlementAmount(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT amount FROM settlement WHERE payment_id = ?", Long.class, paymentId);
	}

	private long seed(long amount, long refundableAmount) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '정산작업테스트', 'USER', NOW(6), NOW(6))",
				"settlejob-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('정산업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '정산카드', 10000, NOW(6), NOW(6))", vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, 'PAID', ?, NOW(6), NOW(6))",
				"ORDER-" + suffix, memberId, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?, ?, 1, 10000, NOW(6), NOW(6))",
				orderId, productId);

		jdbcTemplate.update(
				"INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, refundable_amount, status, created_at, updated_at) "
						+ "VALUES (?, 'CARD', 'mock-pg', ?, ?, ?, 0, ?, 'DONE', NOW(6), NOW(6))",
				orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix, amount, refundableAmount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

}
