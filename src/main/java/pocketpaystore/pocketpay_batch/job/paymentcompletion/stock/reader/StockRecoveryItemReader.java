package pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.dto.StockRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.parameter.StockRecoveryJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.StockRecoveryMapper;

@StepScope
@Component
public class StockRecoveryItemReader implements ItemReader<StockRecoveryCandidate> {

	private final StockRecoveryMapper mapper;
	private final long staleMinutes;
	private final int chunkSize;
	private Iterator<StockRecoveryCandidate> iterator;
	private long lastId;

	public StockRecoveryItemReader(StockRecoveryMapper mapper, StockRecoveryJobParameter jobParameter) {
		this.mapper = mapper;
		this.staleMinutes = jobParameter.getStaleMinutes();
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public StockRecoveryCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<StockRecoveryCandidate> candidates = mapper.findPendingAlerts(lastId, staleMinutes, chunkSize);
			if (candidates.isEmpty()) return null;
			lastId = candidates.get(candidates.size() - 1).getAlertId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
