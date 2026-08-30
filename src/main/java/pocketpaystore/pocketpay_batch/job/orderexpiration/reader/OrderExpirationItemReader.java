package pocketpaystore.pocketpay_batch.job.orderexpiration.reader;

import java.util.Iterator;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

import pocketpaystore.pocketpay_batch.job.orderexpiration.parameter.OrderExpirationJobParameter;
import pocketpaystore.pocketpay_batch.mapper.business.OrderExpirationMapper;

@StepScope
@Component
public class OrderExpirationItemReader implements ItemReader<Long> {

	private final OrderExpirationMapper mapper;
	private final OrderExpirationJobParameter jobParameter;

	private Iterator<Long> iterator;
	private long lastId = 0L;

	public OrderExpirationItemReader(OrderExpirationMapper mapper, OrderExpirationJobParameter jobParameter) {
		this.mapper = mapper;
		this.jobParameter = jobParameter;
	}

	@Override
	public Long read() {
		if (iterator == null || !iterator.hasNext()) {
			List<Long> candidateIds = fetchNextPage();
			if (candidateIds.isEmpty()) {
				return null;
			}
			lastId = candidateIds.get(candidateIds.size() - 1);
			iterator = candidateIds.iterator();
		}
		return iterator.next();
	}

	private List<Long> fetchNextPage() {
		long thresholdMinutes = jobParameter.getThresholdMinutes();
		int chunkSize = jobParameter.getChunkSize().intValue();
		List<Long> candidateIds = mapper.findExpirationCandidateIds(
				thresholdMinutes, lastId, chunkSize, jobParameter.getStartDate(), jobParameter.getEndDate());
		return candidateIds;
	}

}
