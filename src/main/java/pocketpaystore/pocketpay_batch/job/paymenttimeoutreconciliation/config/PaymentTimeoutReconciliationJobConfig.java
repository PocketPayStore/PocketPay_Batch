package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.config;

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

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.reader.PaymentTimeoutItemReader;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.PaymentTimeoutCandidate;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer.PaymentTimeoutItemWriter;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.validator.PaymentTimeoutReconciliationJobParametersValidator;

@Configuration
public class PaymentTimeoutReconciliationJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final PaymentTimeoutItemWriter writer;
	private final PaymentTimeoutReconciliationJobParametersValidator jobParametersValidator;

	public PaymentTimeoutReconciliationJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			PaymentTimeoutItemWriter writer,
			PaymentTimeoutReconciliationJobParametersValidator jobParametersValidator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.jobParametersValidator = jobParametersValidator;
	}

	@Bean
	public Job paymentTimeoutReconciliationJob(Step paymentTimeoutReconciliationStep) {
		return new JobBuilder("paymentTimeoutReconciliationJob", jobRepository)
				.validator(jobParametersValidator)
				.start(paymentTimeoutReconciliationStep)
				.build();
	}

	@Bean
	@JobScope
	public Step paymentTimeoutReconciliationStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			PaymentTimeoutItemReader reader) {
		return new StepBuilder("paymentTimeoutReconciliationStep", jobRepository)
				.<PaymentTimeoutCandidate, PaymentTimeoutCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
