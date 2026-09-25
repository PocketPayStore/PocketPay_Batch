package pocketpaystore.pocketpay_batch.job.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
class SettlementJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("settlementJob")
	private Job settlementJob;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(settlementJob);
	}

	@Test
	@DisplayName("PENDING 정산을 가맹점별로 집계해 SETTLED로 확정한다")
	void run_settlesPendingRecords() throws Exception {
		Fixture fixture = seed();
		LocalDate today = jdbcTemplate.queryForObject("SELECT CURRENT_DATE()", LocalDate.class);

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.addLocalDate("startDate", today)
				.addLocalDate("endDate", today)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(settlementStatus(fixture.settlementId())).isEqualTo("SETTLED");
	}

	private Fixture seed() {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '정산집계테스트', 'USER', NOW(6), NOW(6))",
				"settlerun-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('정산집계업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, 'PAID', ?, NOW(6), NOW(6))",
				"ORDER-" + suffix, memberId, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, status, created_at, updated_at) "
						+ "VALUES (?, 'CARD', 'mock-pg', ?, ?, 10000, 0, 'DONE', NOW(6), NOW(6))",
				orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix);
		Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO settlement (payment_id, vendor_id, amount, pg_fee_amount, platform_fee_amount, net_amount, status, created_at, updated_at) "
						+ "VALUES (?, ?, 10000, 290, 500, 9210, 'PENDING', NOW(6), NOW(6))",
				paymentId, vendorId);
		Long settlementId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		return new Fixture(settlementId);
	}

	private String settlementStatus(long settlementId) {
		return jdbcTemplate.queryForObject("SELECT status FROM settlement WHERE id = ?", String.class, settlementId);
	}

	private record Fixture(long settlementId) { }

}
