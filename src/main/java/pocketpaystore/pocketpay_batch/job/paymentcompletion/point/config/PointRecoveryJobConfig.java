package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.config;

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

import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.reader.PointRecoveryItemReader;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.validator.PointRecoveryJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.writer.PointRecoveryItemWriter;

@Configuration
public class PointRecoveryJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final PointRecoveryItemWriter writer;
	private final PointRecoveryJobParametersValidator validator;

	public PointRecoveryJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			PointRecoveryItemWriter writer,
			PointRecoveryJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job paymentCompletionPointRecoveryJob(Step paymentCompletionPointRecoveryStep) {
		return new JobBuilder("paymentCompletionPointRecoveryJob", jobRepository)
				.validator(validator)
				.start(paymentCompletionPointRecoveryStep)
				.build();
	}

	@Bean
	@JobScope
	public Step paymentCompletionPointRecoveryStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			PointRecoveryItemReader reader) {
		return new StepBuilder("paymentCompletionPointRecoveryStep", jobRepository)
				.<PointRecoveryCandidate, PointRecoveryCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
