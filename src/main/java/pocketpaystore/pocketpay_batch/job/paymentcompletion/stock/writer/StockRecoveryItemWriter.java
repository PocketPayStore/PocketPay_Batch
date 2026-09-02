package pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.dto.StockRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.parameter.StockRecoveryJobParameter;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRecoveryItemWriter implements ItemWriter<StockRecoveryCandidate> {

	private final StockRecoveryStateService stateService;
	private final StockRecoveryJobParameter parameter;

	@Override
	public void write(Chunk<? extends StockRecoveryCandidate> chunk) {
		for (StockRecoveryCandidate candidate : chunk.getItems()) recover(candidate);
	}

	private void recover(StockRecoveryCandidate candidate) {
		try {
			boolean claimed = stateService.recover(candidate.getAlertId(), candidate.getOrderId(), parameter.getStaleMinutes());
			if (!claimed) {
				log.info("[StockRecovery] 다른 인스턴스가 처리 중이거나 이미 종결된 알림: alertId={}", candidate.getAlertId());
			}
		} catch (Exception e) {
			log.error("[StockRecovery] 재고 확정 재처리 실패, 다음 회차에 재시도: alertId={}, orderId={}",
					candidate.getAlertId(), candidate.getOrderId(), e);
			stateService.markFailed(candidate.getAlertId());
		}
	}
}
