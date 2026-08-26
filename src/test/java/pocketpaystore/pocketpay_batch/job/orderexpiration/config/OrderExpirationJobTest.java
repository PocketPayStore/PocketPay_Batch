package pocketpaystore.pocketpay_batch.job.orderexpiration.config;

import static org.assertj.core.api.Assertions.assertThat;

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

import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderExpirationJobTest extends ExpirationTestSupport {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier("orderExpirationJob")
	private Job orderExpirationJob;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		jobLauncherTestUtils.setJob(orderExpirationJob);
	}

	@Test
	@DisplayName("threshold를 지난 STOCK_RESERVED 주문만 EXPIRED + 재고 원복되고, 나머지는 그대로다")
	void run_expiresOnlyStaleOrders() throws Exception {
		long staleOrder1 = seedOrder("STOCK_RESERVED", -20, 5, 2);
		long staleOrder2 = seedOrder("STOCK_RESERVED", -30, 3, 1);
		long staleStockReservedOrder = seedOrder("STOCK_RESERVED", -20, 4, 3);
		long freshPendingOrder = seedOrder("PAYMENT_PENDING", -1, 5, 2);
		long freshStockReservedOrder = seedOrder("STOCK_RESERVED", -1, 4, 3);
		long paidOrder = seedOrder("PAID", -60, 5, 2);

		JobParametersBuilder parametersBuilder = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L);
		JobParameters jobParameters = parametersBuilder.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

		assertThat(statusOf(staleOrder1)).isEqualTo("EXPIRED");
		assertThat(statusOf(staleOrder2)).isEqualTo("EXPIRED");
		assertThat(statusOf(staleStockReservedOrder)).isEqualTo("EXPIRED");
		assertThat(statusOf(freshPendingOrder)).isEqualTo("PAYMENT_PENDING");
		assertThat(statusOf(freshStockReservedOrder)).isEqualTo("STOCK_RESERVED");
		assertThat(statusOf(paidOrder)).isEqualTo("PAID");

		assertThat(reservedQuantityOf(staleOrder1)).isEqualTo(3);
		assertThat(reservedQuantityOf(staleOrder2)).isEqualTo(2);
		assertThat(reservedQuantityOf(staleStockReservedOrder)).isEqualTo(1);
		assertThat(reservedQuantityOf(freshPendingOrder)).isEqualTo(5);
		assertThat(reservedQuantityOf(freshStockReservedOrder)).isEqualTo(4);
		assertThat(reservedQuantityOf(paidOrder)).isEqualTo(5);
	}

	@Test
	@DisplayName("만료 후보가 chunkSize보다 많아도 한 번의 실행에서 전부 만료된다")
	void run_expiresAllStaleOrdersAcrossMultiplePages() throws Exception {
		long[] staleOrders = new long[5];
		for (int i = 0; i < staleOrders.length; i++) {
			staleOrders[i] = seedOrder("STOCK_RESERVED", -20, 5, 2);
		}

		JobParametersBuilder parametersBuilder = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 2L);
		JobParameters jobParameters = parametersBuilder.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		for (long staleOrder : staleOrders) {
			assertThat(statusOf(staleOrder)).isEqualTo("EXPIRED");
		}
	}

	@Test
	@DisplayName("startDate/endDate 범위를 벗어난 후보는 만료 대상에서 제외된다")
	void run_excludesOrdersOutsideDateRange() throws Exception {
		java.time.LocalDate today = jdbcTemplate.queryForObject("SELECT CURRENT_DATE()", java.time.LocalDate.class);
		long inRangeOrder = seedOrder("STOCK_RESERVED", -2 * 24 * 60, 5, 2);
		long outOfRangeOrder = seedOrder("STOCK_RESERVED", -10 * 24 * 60, 5, 2);

		JobParametersBuilder parametersBuilder = jobLauncherTestUtils.getUniqueJobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.addLocalDate("startDate", today.minusDays(3))
				.addLocalDate("endDate", today.minusDays(1));
		JobParameters jobParameters = parametersBuilder.toJobParameters();
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
		assertThat(statusOf(inRangeOrder)).isEqualTo("EXPIRED");
		assertThat(statusOf(outOfRangeOrder)).isEqualTo("STOCK_RESERVED");
	}

	private String statusOf(long orderId) {
		return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
	}

	private int reservedQuantityOf(long orderId) {
		Long productId = jdbcTemplate.queryForObject(
				"SELECT product_id FROM order_item WHERE order_id = ?", Long.class, orderId);
		return jdbcTemplate.queryForObject(
				"SELECT reserved_quantity FROM stock WHERE product_id = ?", Integer.class, productId);
	}

	private long seedOrder(String status, int updatedAtOffsetMinutes, int reservedQuantity, int orderQuantity) {
		String suffix = UUID.randomUUID().toString();

		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) "
						+ "VALUES (?, 'test1234', '잡테스트', 'USER', NOW(6), NOW(6))",
				"job-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('잡업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO product (vendor_id, name, price, created_at, updated_at) "
						+ "VALUES (?, '잡카드', 10000, NOW(6), NOW(6))",
				vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at) "
						+ "VALUES (?, 100, ?, 0, NOW(6), NOW(6))",
				productId, reservedQuantity);

		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) "
						+ "VALUES (?, ?, 10000, ?, ?, NOW(6), TIMESTAMPADD(MINUTE, ?, NOW(6)))",
				"ORDER-" + suffix, memberId, status, "IDEM-" + suffix, updatedAtOffsetMinutes);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) "
						+ "VALUES (?, ?, ?, 10000, NOW(6), NOW(6))",
				orderId, productId, orderQuantity);

		return orderId;
	}

}
