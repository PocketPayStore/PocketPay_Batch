package pocketpaystore.pocketpay_batch.job.pointearnretry.config;

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

import pocketpaystore.pocketpay_batch.job.pointearnretry.dto.PointEarnCandidate;
import pocketpaystore.pocketpay_batch.job.pointearnretry.reader.PointEarnRetryItemReader;
import pocketpaystore.pocketpay_batch.job.pointearnretry.validator.PointEarnRetryJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.pointearnretry.writer.PointEarnRetryItemWriter;

@Configuration
public class PointEarnRetryJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final PointEarnRetryItemWriter writer;
	private final PointEarnRetryJobParametersValidator validator;

	public PointEarnRetryJobConfig(
			JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			PointEarnRetryItemWriter writer,
			PointEarnRetryJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job pointEarnRetryJob(Step pointEarnRetryStep) {
		return new JobBuilder("pointEarnRetryJob", jobRepository)
				.validator(validator)
				.start(pointEarnRetryStep)
				.build();
	}

	@Bean
	@JobScope
	public Step pointEarnRetryStep(
			@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			PointEarnRetryItemReader reader) {
		return new StepBuilder("pointEarnRetryStep", jobRepository)
				.<PointEarnCandidate, PointEarnCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}

}
