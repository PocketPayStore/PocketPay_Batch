package pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.config;

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

import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.dto.StockRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.reader.StockRecoveryItemReader;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.validator.StockRecoveryJobParametersValidator;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.writer.StockRecoveryItemWriter;

@Configuration
public class StockRecoveryJobConfig {

	private final JobRepository jobRepository;
	private final PlatformTransactionManager batchTransactionManager;
	private final StockRecoveryItemWriter writer;
	private final StockRecoveryJobParametersValidator validator;

	public StockRecoveryJobConfig(JobRepository jobRepository,
			@Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
			StockRecoveryItemWriter writer,
			StockRecoveryJobParametersValidator validator) {
		this.jobRepository = jobRepository;
		this.batchTransactionManager = batchTransactionManager;
		this.writer = writer;
		this.validator = validator;
	}

	@Bean
	public Job paymentCompletionStockRecoveryJob(Step paymentCompletionStockRecoveryStep) {
		return new JobBuilder("paymentCompletionStockRecoveryJob", jobRepository)
				.validator(validator)
				.start(paymentCompletionStockRecoveryStep)
				.build();
	}

	@Bean
	@JobScope
	public Step paymentCompletionStockRecoveryStep(@Value("#{jobParameters['chunkSize']}") Long chunkSize,
			StockRecoveryItemReader reader) {
		return new StepBuilder("paymentCompletionStockRecoveryStep", jobRepository)
				.<StockRecoveryCandidate, StockRecoveryCandidate>chunk(chunkSize.intValue())
				.transactionManager(batchTransactionManager)
				.reader(reader)
				.writer(writer)
				.build();
	}
}
