package pocketpaystore.pocketpay_batch.job.settlement.reader;

import java.util.Iterator;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;
import pocketpaystore.pocketpay_batch.job.settlement.dto.SettlementCandidate;
import pocketpaystore.pocketpay_batch.job.settlement.parameter.SettlementJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementMapper;

@StepScope
@Component
public class SettlementItemReader implements ItemReader<SettlementCandidate> {
	private final SettlementMapper mapper;
	private final SettlementJobParameter parameter;
	private final int chunkSize;
	private Iterator<SettlementCandidate> iterator;
	private long lastId;

	public SettlementItemReader(SettlementMapper mapper, SettlementJobParameter parameter) {
		this.mapper = mapper;
		this.parameter = parameter;
		this.chunkSize = parameter.getChunkSize().intValue();
	}

	@Override
	public SettlementCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<SettlementCandidate> candidates = mapper.findPending(
					lastId, chunkSize, parameter.getStartDate(), parameter.getEndDate());
			if (candidates.isEmpty()) {
				return null;
			}
			lastId = candidates.get(candidates.size() - 1).getVendorId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
