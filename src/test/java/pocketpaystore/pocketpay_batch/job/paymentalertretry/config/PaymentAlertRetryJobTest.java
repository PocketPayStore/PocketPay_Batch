package pocketpaystore.pocketpay_batch.job.paymentalertretry.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import pocketpaystore.pocketpay_batch.job.paymentalertretry.writer.PaymentAlertRetryStateService;
import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentAlertRetryJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("paymentAlertRetryJob")
	private Job paymentAlertRetryJob;

	@Autowired
	private PaymentAlertRetryStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;
	private HttpServer server;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(paymentAlertRetryJob);
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	@DisplayName("웹훅이 성공하면 PENDING 알림을 RESOLVED로 표시한다")
	void run_webhookSucceeds_marksResolved() throws Exception {
		startWebhookServer(200);
		long id = seedAlert("PENDING", 0);

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(statusOf(id)).isEqualTo("RESOLVED");
	}

	@Test
	@DisplayName("웹훅이 실패하면 FAILED로 표시하고 retry_count를 늘린다")
	void run_webhookFails_marksFailedAndIncrementsRetryCount() throws Exception {
		startWebhookServer(500);
		long id = seedAlert("FAILED", 2);

		JobParameters jobParameters = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("chunkSize", 20L)
				.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(statusOf(id)).isEqualTo("FAILED");
		assertThat(retryCountOf(id)).isEqualTo(3);
	}

	private void startWebhookServer(int statusCode) throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/", exchange -> {
			exchange.sendResponseHeaders(statusCode, -1);
			exchange.close();
		});
		server.start();
		ReflectionTestUtils.setField(stateService, "webhookUrl", "http://localhost:" + server.getAddress().getPort() + "/");
	}

	private long seedAlert(String status, int retryCount) {
		jdbcTemplate.update(
				"INSERT INTO payment_alert_log "
						+ "(alert_type, severity, payment_id, order_id, message, status, retry_count, created_at, updated_at) "
						+ "VALUES ('STOCK_CONFIRMATION_FAILED', 'CRITICAL', 1, 2, '재고 확정 실패', ?, ?, NOW(6), NOW(6))",
				status, retryCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String statusOf(long id) {
		return jdbcTemplate.queryForObject("SELECT status FROM payment_alert_log WHERE id = ?", String.class, id);
	}

	private int retryCountOf(long id) {
		return jdbcTemplate.queryForObject("SELECT retry_count FROM payment_alert_log WHERE id = ?", Integer.class, id);
	}

}
