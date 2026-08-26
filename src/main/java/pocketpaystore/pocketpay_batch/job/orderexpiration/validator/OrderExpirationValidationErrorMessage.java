package pocketpaystore.pocketpay_batch.job.orderexpiration.validator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderExpirationValidationErrorMessage {

	PARAMETERS_NULL("Job 파라미터가 없습니다"),
	PARAMETER_REQUIRED("%s 파라미터는 필수값이며 Long 타입이어야 합니다"),
	NOT_POSITIVE("%s 파라미터는 0보다 커야 합니다: %s"),
	DATE_RANGE_INCOMPLETE("startDate와 endDate는 둘 다 주거나 둘 다 생략해야 합니다"),
	DATE_RANGE_INVALID_ORDER("startDate(%s)는 endDate(%s)보다 늦을 수 없습니다");

	private final String messageFormat;

	public String format(Object... args) {
		return String.format(messageFormat, args);
	}

}
