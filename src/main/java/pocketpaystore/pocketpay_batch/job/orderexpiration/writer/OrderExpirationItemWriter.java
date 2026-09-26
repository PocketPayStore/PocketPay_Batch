package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationItemWriter implements ItemWriter<Long> {

	private final StockReleaseService stockReleaseService;

	@Override
	public void write(Chunk<? extends Long> chunk) {
		for (Long orderId : chunk.getItems()) {
			try {
				stockReleaseService.expireAndReleaseOrder(orderId);
			} catch (Exception e) {
				log.error("[Expiration] 주문 만료 처리 실패, 다음 배치 실행에서 재시도: orderId={}", orderId, e);
			}
		}
	}

}
