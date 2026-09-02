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

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementCreationCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementCreationMapper;
import pocketpaystore.pocketpay_batch.support.ExpirationTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SettlementCreationStateServiceTest extends ExpirationTestSupport {

	@Autowired
	private SettlementCreationStateService stateService;

	@Autowired
	private SettlementCreationMapper mapper;

	@Autowired
	@Qualifier("businessDataSource")
	private DataSource businessDataSource;

	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate = new JdbcTemplate(businessDataSource);
	}

	@Test
	void findsCompletedPaymentAndCreatesSettlement() {
		long paymentId = seedCompletedPayment(20_000L);
		SettlementCreationCandidate candidate = mapper.findCandidates(0, 100, 0.029, 0.05).stream()
				.filter(it -> it.getPaymentId() == paymentId)
				.findFirst()
				.orElseThrow();

		assertThat(stateService.create(candidate)).isTrue();

		assertThat(settlementCount(paymentId)).isEqualTo(1);
		assertThat(netAmount(paymentId))
				.isEqualTo(20_000L - Math.round(20_000L * 0.029) - Math.round(20_000L * 0.05));
	}

	@Test
	void doesNotCreateDuplicateSettlement() {
		long paymentId = seedCompletedPayment(20_000L);
		SettlementCreationCandidate candidate = mapper.findCandidates(0, 100, 0.029, 0.05).stream()
				.filter(it -> it.getPaymentId() == paymentId)
				.findFirst()
				.orElseThrow();

		assertThat(stateService.create(candidate)).isTrue();
		assertThat(stateService.create(candidate)).isFalse();
		assertThat(settlementCount(paymentId)).isEqualTo(1);
	}

	private long seedCompletedPayment(long amount) {
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
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private int settlementCount(long paymentId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement WHERE payment_id = ?", Integer.class, paymentId);
	}

	private long netAmount(long paymentId) {
		return jdbcTemplate.queryForObject("SELECT net_amount FROM settlement WHERE payment_id = ?", Long.class, paymentId);
	}
}
