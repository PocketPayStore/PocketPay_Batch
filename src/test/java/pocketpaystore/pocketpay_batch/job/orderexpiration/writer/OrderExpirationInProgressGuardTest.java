package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderExpirationInProgressGuardTest extends ExpirationTestSupport {

	@Autowired
	private OrderExpirationStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
	}

	@Test
	@DisplayName("IN_PROGRESS 결제가 있는 주문은 status='PAYMENT_PENDING'이어도 만료시키지 않는다")
	void doesNotExpire_whenInProgressPaymentExists() {
		long orderId = seedStaleOrder();
		seedPayment(orderId, "IN_PROGRESS");

		boolean expired = stateService.markExpiredIfStillPending(orderId);

		assertThat(expired).isFalse();
		assertThat(statusOf(orderId)).isEqualTo("PAYMENT_PENDING");
	}

	@Test
	@DisplayName("IN_PROGRESS 결제가 없으면(TIMEOUT_UNKNOWN만 있거나 아예 없거나) 정상적으로 만료된다")
	void expires_whenNoInProgressPayment() {
		long orderIdWithTimeoutUnknown = seedStaleOrder();
		seedPayment(orderIdWithTimeoutUnknown, "TIMEOUT_UNKNOWN");
		long orderIdWithNoPayment = seedStaleOrder();

		assertThat(stateService.markExpiredIfStillPending(orderIdWithTimeoutUnknown)).isTrue();
		assertThat(stateService.markExpiredIfStillPending(orderIdWithNoPayment)).isTrue();

		assertThat(statusOf(orderIdWithTimeoutUnknown)).isEqualTo("EXPIRED");
		assertThat(statusOf(orderIdWithNoPayment)).isEqualTo("EXPIRED");
	}

	private String statusOf(long orderId) {
		return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
	}

	private void seedPayment(long orderId, String status) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update(
				"INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, "
						+ "amount, status, created_at, updated_at) "
						+ "VALUES (?, 'CARD', 'mock-pg', ?, ?, 10000, ?, NOW(6), NOW(6))",
				orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix, status);
	}

	private long seedStaleOrder() {
		String suffix = UUID.randomUUID().toString();

		jdbcTemplate.update(
				"INSERT INTO member (email, password, name, role, created_at, updated_at) "
						+ "VALUES (?, 'test1234', '가드테스트', 'USER', NOW(6), NOW(6))",
				"guard-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('가드업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update(
				"INSERT INTO product (vendor_id, name, price, created_at, updated_at) "
						+ "VALUES (?, '가드카드', 10000, NOW(6), NOW(6))",
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

}
