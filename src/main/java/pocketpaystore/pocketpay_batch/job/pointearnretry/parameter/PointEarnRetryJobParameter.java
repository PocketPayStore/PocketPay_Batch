package pocketpaystore.pocketpay_batch.job.pointearnretry.parameter;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@StepScope
@Component
public class PointEarnRetryJobParameter {
	@Value("#{jobParameters['chunkSize']}")
	private Long chunkSize;
}
