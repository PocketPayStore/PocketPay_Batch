package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentTimeoutStateServiceTest extends ExpirationTestSupport {

	@Autowired
	private PaymentTimeoutStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
	}

	@Test
	void marksPaymentAndOrderPaidTogether_whenBothAreStillEligible() {
		Fixture fixture = seed("PAYMENT_PENDING");

		assertThat(stateService.markPaidIfStillTimeoutUnknown(
				fixture.paymentId(), fixture.orderId(), fixture.orderNumber())).isTrue();

		assertThat(statusOf("payment", fixture.paymentId())).isEqualTo("DONE");
		assertThat(statusOf("orders", fixture.orderId())).isEqualTo("PAID");
		assertThat(paymentStatusHistoryCount(fixture.paymentId())).isEqualTo(1);
	}

	@Test
	void rollsBackPaymentUpdate_whenOrderIsNoLongerPaymentPending() {
		Fixture fixture = seed("PAID");

		assertThatThrownBy(() -> stateService.markPaidIfStillTimeoutUnknown(
				fixture.paymentId(), fixture.orderId(), fixture.orderNumber()))
				.isInstanceOf(IllegalStateException.class);

		assertThat(statusOf("payment", fixture.paymentId())).isEqualTo("TIMEOUT_UNKNOWN");
		assertThat(statusOf("orders", fixture.orderId())).isEqualTo("PAID");
		assertThat(paymentStatusHistoryCount(fixture.paymentId())).isZero();
	}

	private Fixture seed(String orderStatus) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update("INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '대사테스트', 'USER', NOW(6), NOW(6))", "timeout-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('대사업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '대사카드', 10000, NOW(6), NOW(6))", vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at) VALUES (?, 100, 1, 0, NOW(6), NOW(6))", productId);
		jdbcTemplate.update("INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, ?, ?, NOW(6), NOW(6))", "ORDER-" + suffix, memberId, orderStatus, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, status, created_at, updated_at) VALUES (?, 'CARD', 'mock-pg', ?, ?, 10000, 'TIMEOUT_UNKNOWN', NOW(6), NOW(6))", orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix);
		Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return new Fixture(orderId, paymentId, "ORDER-" + suffix);
	}

	private String statusOf(String table, long id) {
		return jdbcTemplate.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
	}

	private int paymentStatusHistoryCount(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payment_status_history WHERE payment_id = ?", Integer.class, paymentId);
	}

	private record Fixture(long orderId, long paymentId, String orderNumber) { }
}
