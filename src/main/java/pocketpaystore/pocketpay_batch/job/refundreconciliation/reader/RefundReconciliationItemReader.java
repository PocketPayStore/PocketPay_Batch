package pocketpaystore.pocketpay_batch.job.refundreconciliation.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.refundreconciliation.dto.RefundReconciliationCandidate;
import pocketpaystore.pocketpay_batch.job.refundreconciliation.parameter.RefundReconciliationJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.RefundReconciliationMapper;

@StepScope
@Component
public class RefundReconciliationItemReader implements ItemReader<RefundReconciliationCandidate> {

	private final RefundReconciliationMapper mapper;
	private final long thresholdMinutes;
	private final int chunkSize;
	private Iterator<RefundReconciliationCandidate> iterator;
	private long lastId;

	public RefundReconciliationItemReader(RefundReconciliationMapper mapper,
			RefundReconciliationJobParameter jobParameter) {
		this.mapper = mapper;
		this.thresholdMinutes = jobParameter.getThresholdMinutes();
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public RefundReconciliationCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<RefundReconciliationCandidate> candidates = mapper.findCandidates(thresholdMinutes, lastId, chunkSize);
			if (candidates.isEmpty()) return null;
			lastId = candidates.get(candidates.size() - 1).getRefundId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
