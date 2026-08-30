package pocketpaystore.pocketpay_batch.job.settlement.parameter;

import java.time.LocalDate;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.Getter;

@Getter
@StepScope
@Component
public class SettlementJobParameter {
	@Value("#{jobParameters['chunkSize']}")
	private Long chunkSize;

	@Value("#{jobParameters['startDate']}")
	private LocalDate startDate;

	@Value("#{jobParameters['endDate']}")
	private LocalDate endDate;
}
