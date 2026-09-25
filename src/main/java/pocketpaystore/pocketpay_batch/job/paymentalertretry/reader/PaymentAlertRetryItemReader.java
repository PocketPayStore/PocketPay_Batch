package pocketpaystore.pocketpay_batch.job.paymentalertretry.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymentalertretry.dto.PaymentAlertCandidate;
import pocketpaystore.pocketpay_batch.job.paymentalertretry.parameter.PaymentAlertRetryJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.PaymentAlertRetryMapper;

@StepScope
@Component
public class PaymentAlertRetryItemReader implements ItemReader<PaymentAlertCandidate> {

	private final PaymentAlertRetryMapper mapper;
	private final int chunkSize;
	private Iterator<PaymentAlertCandidate> iterator;
	private long lastId;

	public PaymentAlertRetryItemReader(PaymentAlertRetryMapper mapper, PaymentAlertRetryJobParameter jobParameter) {
		this.mapper = mapper;
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public PaymentAlertCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<PaymentAlertCandidate> candidates = mapper.findPendingAlerts(lastId, chunkSize);
			if (candidates.isEmpty()) {
				return null;
			}
			lastId = candidates.get(candidates.size() - 1).getId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
