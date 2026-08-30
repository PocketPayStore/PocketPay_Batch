package pocketpaystore.pocketpay_batch.job.settlement.validator;

import java.time.LocalDate;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.stereotype.Component;

@Component
public class SettlementJobParametersValidator implements JobParametersValidator {
	@Override
	public void validate(JobParameters parameters) throws InvalidJobParametersException {
		Long chunkSize = parameters == null ? null : parameters.getLong("chunkSize");
		if (chunkSize == null || chunkSize <= 0) {
			throw new InvalidJobParametersException("chunkSize 파라미터는 0보다 큰 Long 타입 필수값이어야 합니다");
		}

		LocalDate startDate = parameters.getLocalDate("startDate");
		LocalDate endDate = parameters.getLocalDate("endDate");
		if (startDate == null || endDate == null) {
			throw new InvalidJobParametersException("정산 기간을 위해 startDate와 endDate는 필수입니다");
		}
		if (startDate != null && startDate.isAfter(endDate)) {
			throw new InvalidJobParametersException("startDate는 endDate보다 늦을 수 없습니다");
		}
	}
}
