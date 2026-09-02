package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.parameter.SettlementRecoveryJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementRecoveryMapper;

@StepScope
@Component
public class SettlementRecoveryItemReader implements ItemReader<SettlementRecoveryCandidate> {

	private final SettlementRecoveryMapper mapper;
	private final long staleMinutes;
	private final int chunkSize;
	private Iterator<SettlementRecoveryCandidate> iterator;
	private long lastId;

	public SettlementRecoveryItemReader(SettlementRecoveryMapper mapper, SettlementRecoveryJobParameter jobParameter) {
		this.mapper = mapper;
		this.staleMinutes = jobParameter.getStaleMinutes();
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public SettlementRecoveryCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<SettlementRecoveryCandidate> candidates = mapper.findPendingAlerts(lastId, staleMinutes, chunkSize);
			if (candidates.isEmpty()) return null;
			lastId = candidates.get(candidates.size() - 1).getAlertId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
