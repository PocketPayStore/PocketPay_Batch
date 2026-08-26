package pocketpaystore.pocketpay_batch.job.orderexpiration.config;

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

import pocketpaystore.pocketpay_batch.job.orderexpiration.reader.OrderExpirationItemReader;
import pocketpaystore.pocketpay_batch.job.orderexpiration.validator.OrderExpirationJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.orderexpiration.writer.OrderExpirationItemWriter;

@Configuration
public class OrderExpirationJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final OrderExpirationItemWriter orderExpirationItemWriter;
	private final OrderExpirationJobParametersValidator orderExpirationJobParametersValidator;

	public OrderExpirationJobConfig(
			JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			OrderExpirationItemWriter orderExpirationItemWriter,
			OrderExpirationJobParametersValidator orderExpirationJobParametersValidator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.orderExpirationItemWriter = orderExpirationItemWriter;
		this.orderExpirationJobParametersValidator = orderExpirationJobParametersValidator;
	}

	@Bean
	public Job orderExpirationJob(Step orderExpirationStep) {
		return new JobBuilder("orderExpirationJob", jobRepository)
				.validator(orderExpirationJobParametersValidator)
				.start(orderExpirationStep)
				.build();
	}

	@Bean
	@JobScope
	public Step orderExpirationStep(
			@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			OrderExpirationItemReader orderExpirationItemReader) {
		return new StepBuilder("orderExpirationStep", jobRepository)
				.<Long, Long>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(orderExpirationItemReader)
				.writer(orderExpirationItemWriter)
				.build();
	}

}
