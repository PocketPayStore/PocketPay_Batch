package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecalculationCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.parameter.SettlementCreationJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementRecalculationMapper;

@StepScope
@Component
public class SettlementRecalculationItemReader implements ItemReader<SettlementRecalculationCandidate> {

	private final SettlementRecalculationMapper mapper;
	private final int chunkSize;
	private final double pgFeeRate;
	private final double platformFeeRate;
	private Iterator<SettlementRecalculationCandidate> iterator;
	private long lastId;

	public SettlementRecalculationItemReader(SettlementRecalculationMapper mapper,
			SettlementCreationJobParameter jobParameter,
			@Value("${settlement.pg-fee-rate}") double pgFeeRate,
			@Value("${settlement.platform-fee-rate}") double platformFeeRate) {
		this.mapper = mapper;
		this.chunkSize = jobParameter.getChunkSize().intValue();
		this.pgFeeRate = pgFeeRate;
		this.platformFeeRate = platformFeeRate;
	}

	@Override
	public SettlementRecalculationCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<SettlementRecalculationCandidate> candidates = mapper.findStaleCandidates(
					lastId, chunkSize, pgFeeRate, platformFeeRate);
			if (candidates.isEmpty()) {
				return null;
			}
			lastId = candidates.get(candidates.size() - 1).getSettlementId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
