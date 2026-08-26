package pocketpaystore.pocketpay_batch.job.orderexpiration.validator;

import java.time.LocalDate;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationJobParametersValidator implements JobParametersValidator {

	@Override
	public void validate(JobParameters parameters) throws InvalidJobParametersException {
		if (parameters == null) {
			throw new InvalidJobParametersException(OrderExpirationValidationErrorMessage.PARAMETERS_NULL.getMessageFormat());
		}

		validatePositiveIntegerParameter(parameters, OrderExpirationJobParameterKey.THRESHOLD_MINUTES);
		validatePositiveIntegerParameter(parameters, OrderExpirationJobParameterKey.CHUNK_SIZE);
		validateDateRangeParameters(parameters);
	}

	private void validatePositiveIntegerParameter(JobParameters parameters, OrderExpirationJobParameterKey paramKey)
			throws InvalidJobParametersException {
		Long value = parameters.getLong(paramKey.getKey());
		if (value == null) {
			throw new InvalidJobParametersException(
					OrderExpirationValidationErrorMessage.PARAMETER_REQUIRED.format(paramKey.getKey()));
		}

		if (value <= 0) {
			throw new InvalidJobParametersException(
					OrderExpirationValidationErrorMessage.NOT_POSITIVE.format(paramKey.getKey(), value));
		}
	}

	private void validateDateRangeParameters(JobParameters parameters) throws InvalidJobParametersException {
		LocalDate startDate = parameters.getLocalDate(OrderExpirationJobParameterKey.START_DATE.getKey());
		LocalDate endDate = parameters.getLocalDate(OrderExpirationJobParameterKey.END_DATE.getKey());

		if (startDate == null && endDate == null) {
			return;
		}
		if (startDate == null || endDate == null) {
			throw new InvalidJobParametersException(OrderExpirationValidationErrorMessage.DATE_RANGE_INCOMPLETE.getMessageFormat());
		}
		if (startDate.isAfter(endDate)) {
			throw new InvalidJobParametersException(
					OrderExpirationValidationErrorMessage.DATE_RANGE_INVALID_ORDER.format(startDate, endDate));
		}
	}

}
