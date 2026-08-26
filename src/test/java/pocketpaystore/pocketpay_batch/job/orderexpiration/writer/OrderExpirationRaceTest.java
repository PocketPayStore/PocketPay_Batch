package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderExpirationRaceTest extends ExpirationTestSupport {

	private static final int TRIALS = 30;

	@Autowired
	private OrderExpirationStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
		executor = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("만료 확정과 결제 승인(PAID)이 동시에 경합해도 정확히 하나만 반영된다")
	void expireVsPay_exactlyOneWins() throws InterruptedException {
		AtomicInteger expiredWins = new AtomicInteger();
		AtomicInteger paidWins = new AtomicInteger();

		for (int i = 0; i < TRIALS; i++) {
			Long orderId = seedPendingOrder();

			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(2);

			executor.submit(() -> {
				await(startLatch);
				jitter();
				stateService.markExpiredIfStillPending(orderId);
				doneLatch.countDown();
			});
			executor.submit(() -> {
				await(startLatch);
				jitter();
				simulateCorePaymentApproval(orderId);
				doneLatch.countDown();
			});

			startLatch.countDown();
			boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
			assertThat(finished).as("두 스레드가 제시간에 끝나야 한다(교착 없음)").isTrue();

			String finalStatus = jdbcTemplate.queryForObject(
					"SELECT status FROM orders WHERE id = ?", String.class, orderId);
			assertThat(finalStatus)
					.as("PAYMENT_PENDING으로 남아있으면 안 되고(둘 다 실패), 정확히 하나만 반영돼야 한다")
					.isIn("EXPIRED", "PAID");

			if ("EXPIRED".equals(finalStatus)) {
				expiredWins.incrementAndGet();
			} else {
				paidWins.incrementAndGet();
			}
		}

		assertThat(expiredWins.get() + paidWins.get()).isEqualTo(TRIALS);
		assertThat(expiredWins.get())
				.as("두 인터리빙이 실제로 다 나와야 진짜 경쟁을 재현한 것 — 만료만 계속 이기면 테스트가 무의미함")
				.isGreaterThan(0);
		assertThat(paidWins.get())
				.as("두 인터리빙이 실제로 다 나와야 진짜 경쟁을 재현한 것 — 승인만 계속 이기면 테스트가 무의미함")
				.isGreaterThan(0);
	}

	private void simulateCorePaymentApproval(Long orderId) {
		jdbcTemplate.update(
				"UPDATE orders SET status = 'PAID', updated_at = NOW(6) WHERE id = ? AND status = 'PAYMENT_PENDING'",
				orderId);
	}

	private Long seedPendingOrder() {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) "
						+ "VALUES (?, 'test1234', '레이스테스트', 'USER', NOW(6), NOW(6))",
				"race-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO vendor (name, created_at, updated_at) VALUES ('레이스업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO product (vendor_id, name, price, created_at, updated_at) "
						+ "VALUES (?, '레이스카드', 10000, NOW(6), NOW(6))",
				vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at) "
						+ "VALUES (?, 100, 1, 0, NOW(6), NOW(6))",
				productId);

		jdbcTemplate.update(
				"INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) "
						+ "VALUES (?, ?, 10000, 'PAYMENT_PENDING', ?, NOW(6), TIMESTAMPADD(MINUTE, -20, NOW(6)))",
				"ORDER-" + suffix, memberId, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) "
						+ "VALUES (?, ?, 1, 10000, NOW(6), NOW(6))",
				orderId, productId);

		return orderId;
	}

	private void jitter() {
		try {
			Thread.sleep(ThreadLocalRandom.current().nextInt(0, 5));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
