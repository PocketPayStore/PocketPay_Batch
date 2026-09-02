package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.validator;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class SettlementCreationJobParametersValidator implements JobParametersValidator {

	@Override
	public void validate(JobParameters parameters) throws InvalidJobParametersException {
		Long chunkSize = parameters == null ? null : parameters.getLong("chunkSize");
		if (chunkSize == null || chunkSize <= 0) {
			throw new InvalidJobParametersException("chunkSize 파라미터는 0보다 큰 Long 타입 필수값이어야 합니다");
		}
	}
}
