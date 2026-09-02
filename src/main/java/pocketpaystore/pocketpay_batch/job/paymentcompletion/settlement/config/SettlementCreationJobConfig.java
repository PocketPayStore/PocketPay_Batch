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

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementCreationCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.reader.SettlementCreationItemReader;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.validator.SettlementCreationJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer.SettlementCreationItemWriter;

@Configuration
public class SettlementCreationJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final SettlementCreationItemWriter writer;
	private final SettlementCreationJobParametersValidator validator;

	public SettlementCreationJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			SettlementCreationItemWriter writer,
			SettlementCreationJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job settlementCreationJob(Step settlementCreationStep) {
		return new JobBuilder("settlementCreationJob", jobRepository)
				.validator(validator)
				.start(settlementCreationStep)
				.build();
	}

	@Bean
	@JobScope
	public Step settlementCreationStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			SettlementCreationItemReader reader) {
		return new StepBuilder("settlementCreationStep", jobRepository)
				.<SettlementCreationCandidate, SettlementCreationCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
