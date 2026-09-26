package pocketpaystore.pocketpay_batch.job.refundreconciliation.config;

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

import pocketpaystore.pocketpay_batch.job.refundreconciliation.dto.RefundReconciliationCandidate;
import pocketpaystore.pocketpay_batch.job.refundreconciliation.reader.RefundReconciliationItemReader;
import pocketpaystore.pocketpay_batch.job.refundreconciliation.validator.RefundReconciliationJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.refundreconciliation.writer.RefundReconciliationItemWriter;

@Configuration
public class RefundReconciliationJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final RefundReconciliationItemWriter writer;
	private final RefundReconciliationJobParametersValidator jobParametersValidator;

	public RefundReconciliationJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			RefundReconciliationItemWriter writer,
			RefundReconciliationJobParametersValidator jobParametersValidator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.jobParametersValidator = jobParametersValidator;
	}

	@Bean
	public Job refundReconciliationJob(Step refundReconciliationStep) {
		return new JobBuilder("refundReconciliationJob", jobRepository)
				.validator(jobParametersValidator)
				.start(refundReconciliationStep)
				.build();
	}

	@Bean
	@JobScope
	public Step refundReconciliationStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			RefundReconciliationItemReader reader) {
		return new StepBuilder("refundReconciliationStep", jobRepository)
				.<RefundReconciliationCandidate, RefundReconciliationCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
