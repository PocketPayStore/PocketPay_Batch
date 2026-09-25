package pocketpaystore.pocketpay_batch.job.pointearnretry.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.pointearnretry.dto.PointEarnCandidate;
import pocketpaystore.pocketpay_batch.job.pointearnretry.parameter.PointEarnRetryJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.PointEarnRetryMapper;

@StepScope
@Component
public class PointEarnRetryItemReader implements ItemReader<PointEarnCandidate> {

	private final PointEarnRetryMapper mapper;
	private final int chunkSize;
	private Iterator<PointEarnCandidate> iterator;
	private long lastId;

	public PointEarnRetryItemReader(PointEarnRetryMapper mapper, PointEarnRetryJobParameter jobParameter) {
		this.mapper = mapper;
		this.chunkSize = jobParameter.getChunkSize().intValue();
	}

	@Override
	public PointEarnCandidate read() {
		if (iterator == null || !iterator.hasNext()) {
			List<PointEarnCandidate> candidates = mapper.findPendingPointEarns(lastId, chunkSize);
			if (candidates.isEmpty()) {
				return null;
			}
			lastId = candidates.get(candidates.size() - 1).getId();
			iterator = candidates.iterator();
		}
		return iterator.next();
	}
}
