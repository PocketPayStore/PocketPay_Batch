package pocketpaystore.pocketpay_batch.job.pointearnretry.config;

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
class PointEarnRetryJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("pointEarnRetryJob")
	private Job pointEarnRetryJob;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(pointEarnRetryJob);
	}

	@Test
	@DisplayName("PENDING 적립 로그를 처리하면 포인트 잔액과 원장이 반영되고 RESOLVED로 표시된다")
	void run_pendingLog_earnsPointsAndResolves() throws Exception {
		long memberId = seedMemberWithBalance(1_000L);
		long orderId = seedOrder(memberId);
		long logId = seedPointEarnLog(memberId, orderId, "PENDING");

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(statusOf(logId)).isEqualTo("RESOLVED");
		assertThat(balanceOf(memberId)).isEqualTo(1_100L);
		assertThat(earnLedgerCount(memberId, orderId)).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 RESOLVED된 로그는 다시 처리하지 않아 잔액이 중복 반영되지 않는다")
	void run_resolvedLog_isSkipped() throws Exception {
		long memberId = seedMemberWithBalance(1_000L);
		long orderId = seedOrder(memberId);
		long logId = seedPointEarnLog(memberId, orderId, "RESOLVED");

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(balanceOf(memberId)).isEqualTo(1_000L);
	}

	private long seedMemberWithBalance(long balance) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '적립테스트', 'USER', NOW(6), NOW(6))",
				"earn-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update(
				"INSERT INTO point_balance (member_id, balance, reserved_amount, created_at, updated_at) VALUES (?, ?, 0, NOW(6), NOW(6))",
				memberId, balance);
		return memberId;
	}

	private long seedOrder(long memberId) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, 'PAID', ?, NOW(6), NOW(6))",
				"ORDER-" + suffix, memberId, "IDEM-" + suffix);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long seedPointEarnLog(long memberId, long orderId, String status) {
		jdbcTemplate.update(
				"INSERT INTO point_earn_log (member_id, order_id, payment_id, amount, status, created_at, updated_at) "
						+ "VALUES (?, ?, 1, 100, ?, NOW(6), NOW(6))",
				memberId, orderId, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String statusOf(long id) {
		return jdbcTemplate.queryForObject("SELECT status FROM point_earn_log WHERE id = ?", String.class, id);
	}

	private long balanceOf(long memberId) {
		return jdbcTemplate.queryForObject("SELECT balance FROM point_balance WHERE member_id = ?", Long.class, memberId);
	}

	private int earnLedgerCount(long memberId, long orderId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM point_ledger WHERE member_id = ? AND order_id = ? AND type = 'EARN'",
				Integer.class, memberId, orderId);
	}

}
