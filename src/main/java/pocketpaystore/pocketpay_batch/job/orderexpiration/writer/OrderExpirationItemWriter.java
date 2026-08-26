package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import java.util.ArrayList;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderExpirationItemWriter implements ItemWriter<Long> {

	private final StockReleaseService stockReleaseService;

	@Override
	public void write(Chunk<? extends Long> chunk) {
		stockReleaseService.expireAndRelease(new ArrayList<>(chunk.getItems()));
	}

}
