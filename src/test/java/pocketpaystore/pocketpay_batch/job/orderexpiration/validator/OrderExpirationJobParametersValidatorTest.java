package pocketpaystore.pocketpay_batch.job.orderexpiration.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

class OrderExpirationJobParametersValidatorTest {

	private final OrderExpirationJobParametersValidator validator = new OrderExpirationJobParametersValidator();

	@Test
	@DisplayName("thresholdMinutes/chunkSize만 있고 startDate/endDate가 없으면 통과한다")
	void validate_passesWithoutDateRange() {
		var parameters = new JobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.toJobParameters();

		assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("startDate, endDate가 둘 다 있고 startDate가 endDate보다 앞서면 통과한다")
	void validate_passesWithValidDateRange() {
		var parameters = new JobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.addLocalDate("startDate", java.time.LocalDate.of(2026, 1, 1))
				.addLocalDate("endDate", java.time.LocalDate.of(2026, 1, 31))
				.toJobParameters();

		assertThatCode(() -> validator.validate(parameters)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("startDate만 있고 endDate가 없으면 거부한다")
	void validate_rejectsStartDateWithoutEndDate() {
		var parameters = new JobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.addLocalDate("startDate", java.time.LocalDate.of(2026, 1, 1))
				.toJobParameters();

		assertThatThrownBy(() -> validator.validate(parameters)).isInstanceOf(InvalidJobParametersException.class);
	}

	@Test
	@DisplayName("startDate가 endDate보다 늦으면 거부한다")
	void validate_rejectsStartDateAfterEndDate() {
		var parameters = new JobParametersBuilder()
				.addLong("thresholdMinutes", 10L)
				.addLong("chunkSize", 20L)
				.addLocalDate("startDate", java.time.LocalDate.of(2026, 2, 1))
				.addLocalDate("endDate", java.time.LocalDate.of(2026, 1, 1))
				.toJobParameters();

		assertThatThrownBy(() -> validator.validate(parameters)).isInstanceOf(InvalidJobParametersException.class);
	}

}
