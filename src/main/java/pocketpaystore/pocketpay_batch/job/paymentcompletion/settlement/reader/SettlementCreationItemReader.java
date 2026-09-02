package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementCreationCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.parameter.SettlementCreationJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementCreationMapper;

@StepScope
@Component
public class SettlementCreationItemReader implements ItemReader<SettlementCreationCandidate> {

	private final SettlementCreationMapper mapper;
	private final int chunkSize;
	private final double pgFeeRate;
	private final double platformFeeRate;
	private Iterator<SettlementCreationCandidate> iterator;
	private long lastPaymentId;

	public SettlementCreationItemReader(SettlementCreationMapper mapper,
			SettlementCreationJobParameter jobParameter,
			@Value("${settlement.pg-fee-rate}") double pgFeeRate,
			@Value("${settlement.platform-fee-rate}") double platformFeeRate) {
		this.mapper = mapper;
		this.chunkSize = jobParameter.getChunkSize().intValue();
		this.pgFeeRate = pgFeeRate;
		this.platformFeeRate = platformFeeRate;
	}

	@Override
	public SettlementCreationCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<SettlementCreationCandidate> candidates = mapper.findCandidates(
					lastPaymentId, chunkSize, pgFeeRate, platformFeeRate);
			if (candidates.isEmpty()) {
				return null;
			}
			lastPaymentId = candidates.get(candidates.size() - 1).getPaymentId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
