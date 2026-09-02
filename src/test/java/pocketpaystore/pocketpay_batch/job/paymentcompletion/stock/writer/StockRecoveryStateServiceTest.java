package pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.writer;

import static org.assertj.core.api.Assertions.assertThat;

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
class StockRecoveryStateServiceTest extends ExpirationTestSupport {

	@Autowired
	private StockRecoveryStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
	}

	@Test
	void confirmsStockAndResolvesAlert() {
		Fixture fixture = seed(10, 2, 0);

		assertThat(stateService.recover(fixture.alertId(), fixture.orderId(), 10)).isTrue();

		assertThat(reservedQuantity(fixture.productId())).isEqualTo(0);
		assertThat(soldQuantity(fixture.productId())).isEqualTo(2);
		assertThat(alertStatus(fixture.alertId())).isEqualTo("RESOLVED");
	}

	@Test
	void doesNotDoubleConfirmStock_whenAlertAlreadyResolved() {
		Fixture fixture = seed(10, 2, 0);
		assertThat(stateService.recover(fixture.alertId(), fixture.orderId(), 10)).isTrue();

		boolean secondAttempt = stateService.recover(fixture.alertId(), fixture.orderId(), 10);

		assertThat(secondAttempt).isFalse();
		assertThat(reservedQuantity(fixture.productId())).isEqualTo(0);
		assertThat(soldQuantity(fixture.productId())).isEqualTo(2);
	}

	private Fixture seed(int totalQuantity, int orderedQuantity, int soldQuantity) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update("INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '재고테스트', 'USER', NOW(6), NOW(6))", "stock-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('재고업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '재고카드', 10000, NOW(6), NOW(6))", vendorId);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO stock (product_id, total_quantity, reserved_quantity, sold_quantity, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
				productId, totalQuantity, orderedQuantity, soldQuantity);
		jdbcTemplate.update("INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, 20000, 'PAID', ?, NOW(6), NOW(6))", "ORDER-" + suffix, memberId, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?, ?, ?, 10000, NOW(6), NOW(6))", orderId, productId, orderedQuantity);
		jdbcTemplate.update("INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, status, created_at, updated_at) VALUES (?, 'CARD', 'mock-pg', ?, ?, 20000, 'DONE', NOW(6), NOW(6))", orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix);
		Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO payment_alert_log (alert_type, severity, payment_id, order_id, message, status, created_at, updated_at) VALUES ('STOCK_CONFIRMATION_FAILED', 'CRITICAL', ?, ?, '재고 확정 실패', 'PENDING', NOW(6), NOW(6))", paymentId, orderId);
		Long alertId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return new Fixture(alertId, orderId, productId);
	}

	private int reservedQuantity(long productId) {
		return jdbcTemplate.queryForObject("SELECT reserved_quantity FROM stock WHERE product_id = ?", Integer.class, productId);
	}

	private int soldQuantity(long productId) {
		return jdbcTemplate.queryForObject("SELECT sold_quantity FROM stock WHERE product_id = ?", Integer.class, productId);
	}

	private String alertStatus(long alertId) {
		return jdbcTemplate.queryForObject("SELECT status FROM payment_alert_log WHERE id = ?", String.class, alertId);
	}

	private record Fixture(long alertId, long orderId, long productId) { }
}
