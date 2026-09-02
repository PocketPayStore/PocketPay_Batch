package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.config;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.reader.SettlementRecoveryItemReader;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.validator.SettlementRecoveryJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer.SettlementRecoveryItemWriter;

@Configuration
public class SettlementRecoveryJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final SettlementRecoveryItemWriter writer;
	private final SettlementRecoveryJobParametersValidator validator;

	public SettlementRecoveryJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			SettlementRecoveryItemWriter writer,
			SettlementRecoveryJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job paymentCompletionSettlementRecoveryJob(Step paymentCompletionSettlementRecoveryStep) {
		return new JobBuilder("paymentCompletionSettlementRecoveryJob", jobRepository)
				.validator(validator)
				.start(paymentCompletionSettlementRecoveryStep)
				.build();
	}

	@Bean
	@JobScope
	public Step paymentCompletionSettlementRecoveryStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			SettlementRecoveryItemReader reader) {
		return new StepBuilder("paymentCompletionSettlementRecoveryStep", jobRepository)
				.<SettlementRecoveryCandidate, SettlementRecoveryCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
