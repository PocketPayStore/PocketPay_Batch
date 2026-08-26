package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.PaymentTimeoutCandidate;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.parameter.PaymentTimeoutReconciliationJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.PaymentTimeoutReconciliationMapper;

@StepScope
@Component
public class PaymentTimeoutItemReader implements ItemReader<PaymentTimeoutCandidate> {

	private final PaymentTimeoutReconciliationMapper mapper;
	private final long thresholdMinutes;
	private final int chunkSize;
	private Iterator<PaymentTimeoutCandidate> iterator;
	private long lastId;

	public PaymentTimeoutItemReader(PaymentTimeoutReconciliationMapper mapper,
			PaymentTimeoutReconciliationJobParameter jobParameter) {
		this.mapper = mapper;
		this.thresholdMinutes = jobParameter.getThresholdMinutes();
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public PaymentTimeoutCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<PaymentTimeoutCandidate> candidates = mapper.findCandidates(thresholdMinutes, lastId, chunkSize);
			if (candidates.isEmpty()) return null;
			lastId = candidates.get(candidates.size() - 1).getPaymentId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
