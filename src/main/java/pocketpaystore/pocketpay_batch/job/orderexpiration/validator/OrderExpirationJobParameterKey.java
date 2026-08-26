package pocketpaystore.pocketpay_batch.job.orderexpiration.validator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderExpirationJobParameterKey {

	THRESHOLD_MINUTES("thresholdMinutes"),
	CHUNK_SIZE("chunkSize"),
	START_DATE("startDate"),
	END_DATE("endDate");

	private final String key;

}
