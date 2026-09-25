package pocketpaystore.pocketpay_batch.job.paymentalertretry.config;

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

import pocketpaystore.pocketpay_batch.job.paymentalertretry.dto.PaymentAlertCandidate;
import pocketpaystore.pocketpay_batch.job.paymentalertretry.reader.PaymentAlertRetryItemReader;
import pocketpaystore.pocketpay_batch.job.paymentalertretry.validator.PaymentAlertRetryJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.paymentalertretry.writer.PaymentAlertRetryItemWriter;

@Configuration
public class PaymentAlertRetryJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final PaymentAlertRetryItemWriter writer;
	private final PaymentAlertRetryJobParametersValidator validator;

	public PaymentAlertRetryJobConfig(
			JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			PaymentAlertRetryItemWriter writer,
			PaymentAlertRetryJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job paymentAlertRetryJob(Step paymentAlertRetryStep) {
		return new JobBuilder("paymentAlertRetryJob", jobRepository)
				.validator(validator)
				.start(paymentAlertRetryStep)
				.build();
	}

	@Bean
	@JobScope
	public Step paymentAlertRetryStep(
			@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			PaymentAlertRetryItemReader reader) {
		return new StepBuilder("paymentAlertRetryStep", jobRepository)
				.<PaymentAlertCandidate, PaymentAlertCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}

}
