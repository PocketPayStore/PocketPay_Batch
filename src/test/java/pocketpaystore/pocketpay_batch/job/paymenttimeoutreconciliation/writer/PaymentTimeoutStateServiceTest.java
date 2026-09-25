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
				fixture.paymentId(), fixture.orderId(), fixture.orderNumber(), fixture.amount(), fixture.memberId())).isTrue();

		assertThat(statusOf("payment", fixture.paymentId())).isEqualTo("DONE");
		assertThat(statusOf("orders", fixture.orderId())).isEqualTo("PAID");
		assertThat(paymentStatusHistoryCount(fixture.paymentId())).isEqualTo(1);
		assertThat(pointBalance(fixture.memberId())).isEqualTo(2_000L);
		assertThat(reservedPointBalance(fixture.memberId())).isZero();
		assertThat(pointReservationStatus(fixture.paymentId())).isEqualTo("USED");
		assertThat(pointLedgerCount(fixture.orderId(), "USE")).isEqualTo(1);
		assertThat(pointLedgerCount(fixture.orderId(), "EARN")).isZero();
		assertThat(pointEarnLogAmount(fixture.paymentId())).isEqualTo(90L);
		assertThat(pointEarnLogStatus(fixture.paymentId())).isEqualTo("PENDING");
		assertThat(soldQuantity(fixture.productId())).isEqualTo(1);
		assertThat(reservedQuantity(fixture.productId())).isZero();
	}

	@Test
	void rollsBackPaymentUpdate_whenOrderIsNoLongerPaymentPending() {
		Fixture fixture = seed("PAID");

		assertThatThrownBy(() -> stateService.markPaidIfStillTimeoutUnknown(
				fixture.paymentId(), fixture.orderId(), fixture.orderNumber(), fixture.amount(), fixture.memberId()))
				.isInstanceOf(IllegalStateException.class);

		assertThat(statusOf("payment", fixture.paymentId())).isEqualTo("TIMEOUT_UNKNOWN");
		assertThat(statusOf("orders", fixture.orderId())).isEqualTo("PAID");
		assertThat(paymentStatusHistoryCount(fixture.paymentId())).isZero();
		assertThat(pointBalance(fixture.memberId())).isEqualTo(3_000L);
		assertThat(reservedPointBalance(fixture.memberId())).isEqualTo(1_000L);
		assertThat(pointReservationStatus(fixture.paymentId())).isEqualTo("RESERVED");
		assertThat(soldQuantity(fixture.productId())).isZero();
		assertThat(reservedQuantity(fixture.productId())).isEqualTo(1);
		assertThat(pointEarnLogCount(fixture.paymentId())).isZero();
	}

	@Test
	void releasesPointReservation_whenPgFailureIsConfirmed() {
		Fixture fixture = seed("PAYMENT_PENDING");

		assertThat(stateService.markFailedIfStillTimeoutUnknown(
				fixture.paymentId(), fixture.orderId(), fixture.orderNumber())).isTrue();

		assertThat(statusOf("payment", fixture.paymentId())).isEqualTo("FAILED");
		assertThat(statusOf("orders", fixture.orderId())).isEqualTo("PAYMENT_PENDING");
		assertThat(pointBalance(fixture.memberId())).isEqualTo(3_000L);
		assertThat(reservedPointBalance(fixture.memberId())).isZero();
		assertThat(pointReservationStatus(fixture.paymentId())).isEqualTo("RELEASED");
		assertThat(paymentStatusHistoryCount(fixture.paymentId())).isEqualTo(1);
	}

	private Fixture seed(String orderStatus) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update("INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '대사테스트', 'USER', NOW(6), NOW(6))", "timeout-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO point_balance (member_id, balance, reserved_amount, created_at, updated_at) VALUES (?, 3000, 1000, NOW(6), NOW(6))", memberId);
		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('대사업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '대사카드', 10000, NOW(6), NOW(6))", vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at) VALUES (?, 100, 1, 0, NOW(6), NOW(6))", productId);
		jdbcTemplate.update("INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 10000, ?, ?, NOW(6), NOW(6))", "ORDER-" + suffix, memberId, orderStatus, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?, ?, 1, 10000, NOW(6), NOW(6))", orderId, productId);
		jdbcTemplate.update("INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, status, created_at, updated_at) VALUES (?, 'CARD', 'mock-pg', ?, ?, 9000, 1000, 'TIMEOUT_UNKNOWN', NOW(6), NOW(6))", orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix);
		Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO point_reservation (payment_id, member_id, amount, status, created_at, updated_at) VALUES (?, ?, 1000, 'RESERVED', NOW(6), NOW(6))", paymentId, memberId);
		return new Fixture(orderId, paymentId, memberId, "ORDER-" + suffix, 9000L, productId);
	}

	private String statusOf(String table, long id) {
		return jdbcTemplate.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
	}

	private int paymentStatusHistoryCount(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payment_status_history WHERE payment_id = ?", Integer.class, paymentId);
	}

	private Long pointBalance(long memberId) {
		return jdbcTemplate.queryForObject(
				"SELECT balance FROM point_balance WHERE member_id = ?", Long.class, memberId);
	}

	private Long reservedPointBalance(long memberId) {
		return jdbcTemplate.queryForObject(
				"SELECT reserved_amount FROM point_balance WHERE member_id = ?", Long.class, memberId);
	}

	private String pointReservationStatus(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM point_reservation WHERE payment_id = ?", String.class, paymentId);
	}

	private int pointLedgerCount(long orderId, String type) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM point_ledger WHERE order_id = ? AND type = ?", Integer.class, orderId, type);
	}

	private int soldQuantity(long productId) {
		return jdbcTemplate.queryForObject(
				"SELECT sold_quantity FROM stock WHERE product_id = ?", Integer.class, productId);
	}

	private int reservedQuantity(long productId) {
		return jdbcTemplate.queryForObject(
				"SELECT reserved_quantity FROM stock WHERE product_id = ?", Integer.class, productId);
	}

	private int pointEarnLogCount(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM point_earn_log WHERE payment_id = ?", Integer.class, paymentId);
	}

	private Long pointEarnLogAmount(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT amount FROM point_earn_log WHERE payment_id = ?", Long.class, paymentId);
	}

	private String pointEarnLogStatus(long paymentId) {
		return jdbcTemplate.queryForObject(
				"SELECT status FROM point_earn_log WHERE payment_id = ?", String.class, paymentId);
	}

	private record Fixture(long orderId, long paymentId, long memberId, String orderNumber, long amount, long productId) { }
}
