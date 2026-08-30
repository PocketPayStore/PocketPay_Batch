package pocketpaystore.pocketpay_batch.job.settlement.config;

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
import pocketpaystore.pocketpay_batch.job.settlement.dto.SettlementCandidate;
import pocketpaystore.pocketpay_batch.job.settlement.reader.SettlementItemReader;
import pocketpaystore.pocketpay_batch.job.settlement.validator.SettlementJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.settlement.writer.SettlementItemWriter;

@Configuration
public class SettlementJobConfig {
	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final SettlementItemWriter writer;
	private final SettlementJobParametersValidator validator;

	public SettlementJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			SettlementItemWriter writer,
			SettlementJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job settlementJob(Step settlementStep) {
		return new JobBuilder("settlementJob", jobRepository)
				.validator(validator)
				.start(settlementStep)
				.build();
	}

	@Bean
	@JobScope
	public Step settlementStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			SettlementItemReader reader) {
		return new StepBuilder("settlementStep", jobRepository)
				.<SettlementCandidate, SettlementCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
