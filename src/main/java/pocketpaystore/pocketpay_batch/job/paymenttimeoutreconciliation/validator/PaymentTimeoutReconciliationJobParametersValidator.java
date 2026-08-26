package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.validator;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class PaymentTimeoutReconciliationJobParametersValidator implements JobParametersValidator {

	@Override
	public void validate(JobParameters parameters) throws InvalidJobParametersException {
		if (parameters == null) {
			throw new InvalidJobParametersException("Job 파라미터가 없습니다");
		}
		validatePositive(parameters, "thresholdMinutes");
		validatePositive(parameters, "chunkSize");
	}

	private void validatePositive(JobParameters parameters, String key) throws InvalidJobParametersException {
		Long value = parameters.getLong(key);
		if (value == null) {
			throw new InvalidJobParametersException(key + " 파라미터는 필수값이며 Long 타입이어야 합니다");
		}
		if (value <= 0) {
			throw new InvalidJobParametersException(key + " 파라미터는 0보다 커야 합니다: " + value);
		}
	}
}
