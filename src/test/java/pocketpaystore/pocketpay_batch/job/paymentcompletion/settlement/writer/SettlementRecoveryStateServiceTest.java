package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

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
class SettlementRecoveryStateServiceTest extends ExpirationTestSupport {

	@Autowired
	private SettlementRecoveryStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
	}

	@Test
	void createsSettlementRowAndResolvesAlert() {
		Fixture fixture = seed(20000L);

		assertThat(stateService.recover(fixture.alertId(), fixture.paymentId(), 10)).isTrue();

		assertThat(settlementCount(fixture.paymentId())).isEqualTo(1);
		assertThat(netAmount(fixture.paymentId())).isEqualTo(20000L - Math.round(20000L * 0.029) - Math.round(20000L * 0.05));
		assertThat(alertStatus(fixture.alertId())).isEqualTo("RESOLVED");
	}

	@Test
	void doesNotDoubleInsertSettlement_whenAlertAlreadyResolved() {
		Fixture fixture = seed(20000L);
		assertThat(stateService.recover(fixture.alertId(), fixture.paymentId(), 10)).isTrue();

		boolean secondAttempt = stateService.recover(fixture.alertId(), fixture.paymentId(), 10);

		assertThat(secondAttempt).isFalse();
		assertThat(settlementCount(fixture.paymentId())).isEqualTo(1);
	}

	private Fixture seed(long amount) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update("INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '정산테스트', 'USER', NOW(6), NOW(6))", "settlement-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO vendor (name, created_at, updated_at) VALUES ('정산업체', NOW(6), NOW(6))");
		Long vendorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO product (vendor_id, name, price, created_at, updated_at) VALUES (?, '정산카드', ?, NOW(6), NOW(6))", vendorId, amount);
		Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, 'PAID', ?, NOW(6), NOW(6))", "ORDER-" + suffix, memberId, amount, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO order_item (order_id, product_id, quantity, unit_price, created_at, updated_at) VALUES (?, ?, 1, ?, NOW(6), NOW(6))", orderId, productId, amount);
		jdbcTemplate.update("INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, status, created_at, updated_at) VALUES (?, 'CARD', 'mock-pg', ?, ?, ?, 'DONE', NOW(6), NOW(6))",
				orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix, amount);
		Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO payment_alert_log (alert_type, severity, payment_id, order_id, message, status, created_at, updated_at) VALUES ('PAYMENT_COMPLETION_FAILED', 'WARNING', ?, ?, 'SETTLEMENT 후처리 실패', 'PENDING', NOW(6), NOW(6))", paymentId, orderId);
		Long alertId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return new Fixture(alertId, paymentId);
	}

	private int settlementCount(long paymentId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement WHERE payment_id = ?", Integer.class, paymentId);
	}

	private long netAmount(long paymentId) {
		return jdbcTemplate.queryForObject("SELECT net_amount FROM settlement WHERE payment_id = ?", Long.class, paymentId);
	}

	private String alertStatus(long alertId) {
		return jdbcTemplate.queryForObject("SELECT status FROM payment_alert_log WHERE id = ?", String.class, alertId);
	}

	private record Fixture(long alertId, long paymentId) { }
}
