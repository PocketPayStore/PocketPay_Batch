package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.writer;

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
class PointRecoveryStateServiceTest extends ExpirationTestSupport {

	@Autowired
	private PointRecoveryStateService stateService;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
	}

	@Test
	void earnsPointsAndAppendsLedger() {
		Fixture fixture = seed(0L, 20000L, 0L);

		assertThat(stateService.recover(fixture.alertId(), fixture.paymentId(), fixture.orderId(), "POINT_EARN", 10)).isTrue();

		assertThat(balanceOf(fixture.memberId())).isEqualTo(1000L);
		assertThat(ledgerCount(fixture.orderId(), "EARN")).isEqualTo(1);
		assertThat(alertStatus(fixture.alertId())).isEqualTo("RESOLVED");
	}

	@Test
	void doesNotDoubleApply_whenAlertAlreadyResolved() {
		Fixture fixture = seed(0L, 20000L, 0L);
		assertThat(stateService.recover(fixture.alertId(), fixture.paymentId(), fixture.orderId(), "POINT_EARN", 10)).isTrue();

		boolean secondAttempt = stateService.recover(fixture.alertId(), fixture.paymentId(), fixture.orderId(), "POINT_EARN", 10);

		assertThat(secondAttempt).isFalse();
		assertThat(balanceOf(fixture.memberId())).isEqualTo(1000L);
		assertThat(ledgerCount(fixture.orderId(), "EARN")).isEqualTo(1);
	}

	private Fixture seed(long initialBalance, long paymentAmount, long usedPointAmount) {
		String suffix = UUID.randomUUID().toString();
		jdbcTemplate.update("INSERT INTO member (email, password, name, role, created_at, updated_at) VALUES (?, 'test1234', '포인트테스트', 'USER', NOW(6), NOW(6))", "point-" + suffix + "@test.com");
		Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO point_balance (member_id, balance, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))", memberId, initialBalance);
		jdbcTemplate.update("INSERT INTO orders (order_number, member_id, total_amount, status, idempotency_key, created_at, updated_at) VALUES (?, ?, ?, 'PAID', ?, NOW(6), NOW(6))", "ORDER-" + suffix, memberId, paymentAmount, "IDEM-" + suffix);
		Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO payment (order_id, payment_method, pg_provider, pg_transaction_id, idempotency_key, amount, used_point_amount, status, created_at, updated_at) VALUES (?, 'CARD', 'mock-pg', ?, ?, ?, ?, 'DONE', NOW(6), NOW(6))",
				orderId, "MOCK-" + suffix, "IDEM-PAY-" + suffix, paymentAmount, usedPointAmount);
		Long paymentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		jdbcTemplate.update("INSERT INTO payment_alert_log (alert_type, severity, payment_id, order_id, message, status, created_at, updated_at) VALUES ('PAYMENT_COMPLETION_FAILED', 'WARNING', ?, ?, 'POINT_EARN 후처리 실패', 'PENDING', NOW(6), NOW(6))", paymentId, orderId);
		Long alertId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return new Fixture(alertId, paymentId, orderId, memberId);
	}

	private Long balanceOf(long memberId) {
		return jdbcTemplate.queryForObject("SELECT balance FROM point_balance WHERE member_id = ?", Long.class, memberId);
	}

	private int ledgerCount(long orderId, String type) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_ledger WHERE order_id = ? AND type = ?", Integer.class, orderId, type);
	}

	private String alertStatus(long alertId) {
		return jdbcTemplate.queryForObject("SELECT status FROM payment_alert_log WHERE id = ?", String.class, alertId);
	}

	private record Fixture(long alertId, long paymentId, long orderId, long memberId) { }
}
