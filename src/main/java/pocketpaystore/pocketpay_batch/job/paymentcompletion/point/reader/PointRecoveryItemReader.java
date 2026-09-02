package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.parameter.PointRecoveryJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.PointRecoveryMapper;

@StepScope
@Component
public class PointRecoveryItemReader implements ItemReader<PointRecoveryCandidate> {

	private final PointRecoveryMapper mapper;
	private final long staleMinutes;
	private final int chunkSize;
	private Iterator<PointRecoveryCandidate> iterator;
	private long lastId;

	public PointRecoveryItemReader(PointRecoveryMapper mapper, PointRecoveryJobParameter jobParameter) {
		this.mapper = mapper;
		this.staleMinutes = jobParameter.getStaleMinutes();
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public PointRecoveryCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<PointRecoveryCandidate> candidates = mapper.findPendingAlerts(lastId, staleMinutes, chunkSize);
			if (candidates.isEmpty()) return null;
			lastId = candidates.get(candidates.size() - 1).getAlertId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
