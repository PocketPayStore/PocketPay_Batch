package pocketpaystore.pocketpay_batch.job.paymentcompletion.config;

import java.util.Map;
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
import pocketpaystore.pocketpay_batch.mapper.business.PaymentCompletionReconciliationMapper;

@Configuration
public class PaymentCompletionReconciliationJobConfig {
    @Bean
    public Job paymentCompletionReconciliationJob(JobRepository repository, Step paymentCompletionReconciliationStep) {
        return new JobBuilder("paymentCompletionReconciliationJob", repository).start(paymentCompletionReconciliationStep).build();
    }

    @Bean
    public Step paymentCompletionReconciliationStep(JobRepository repository,
            @Qualifier("businessTransactionManager") PlatformTransactionManager transactionManager,
            PaymentCompletionReconciliationMapper mapper,
            @Value("#{jobParameters['chunkSize'] ?: 500}") Long chunkSize) {
        return new StepBuilder("paymentCompletionReconciliationStep", repository).tasklet((contribution, context) -> {
            long lastId = 0;
            while (true) {
                var alerts = mapper.findPendingAlerts(lastId, chunkSize.intValue());
                if (alerts.isEmpty()) break;
                for (Map<String, Object> alert : alerts) {
                    long id = ((Number) alert.get("id")).longValue();
                    lastId = id;
                    if (mapper.markProcessing(id) != 1) continue;
                    try {
                        String type = String.valueOf(alert.get("alert_type"));
                        if (type.contains("STOCK")) mapper.completeStock(((Number) alert.get("order_id")).longValue());
                        if (type.contains("COMPLETION") && alert.get("payment_id") != null) mapper.completeSettlement(((Number) alert.get("payment_id")).longValue());
                        if (type.contains("POINT") && alert.get("order_id") != null) mapper.completePoint(((Number) alert.get("order_id")).longValue());
                        mapper.markResolved(id);
                    } catch (Exception e) { mapper.markFailed(id); }
                }
            }
            return org.springframework.batch.infrastructure.repeat.RepeatStatus.FINISHED;
        }, transactionManager).build();
    }
}
