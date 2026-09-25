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
		long paymentId = seed();

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM settlement WHERE payment_id = ?", Integer.class, paymentId))
				.isEqualTo(1);
	}

	private long seed() {
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
				"INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, status, created_at, updated_at) "
						+ "VALUES (?, 'CARD', 'mock-pg', ?, ?, 10000, 0, 'DONE', NOW(6), NOW(6))",
				orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

}
