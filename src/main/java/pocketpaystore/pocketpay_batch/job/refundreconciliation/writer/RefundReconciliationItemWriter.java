package pocketpaystore.pocketpay_batch.job.refundreconciliation.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossCancelRequest;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer.TossPgClient;
import pocketpaystore.pocketpay_batch.job.refundreconciliation.dto.RefundReconciliationCandidate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconciliationItemWriter implements ItemWriter<RefundReconciliationCandidate> {

	private static final String CANCEL_REASON = "REFUND";

	private final TossPgClient tossPgClient;
	private final RefundReconciliationStateService stateService;

	@Override
	public void write(Chunk<? extends RefundReconciliationCandidate> chunk) {
		for (RefundReconciliationCandidate candidate : chunk.getItems()) reconcile(candidate);
	}

	private void reconcile(RefundReconciliationCandidate candidate) {
		boolean pgCancelSucceeded;
		try {
			tossPgClient.cancel(candidate.getPgTransactionId(), candidate.getIdempotencyKey(),
					new TossCancelRequest(CANCEL_REASON, candidate.getRequestAmount()));
			pgCancelSucceeded = true;
		} catch (Exception e) {
			log.error("[RefundReconciliation] PG 취소 재시도 실패, 다음 회차에 재시도: refundId={}", candidate.getRefundId(), e);
			pgCancelSucceeded = false;
		}

		try {
			stateService.applyReconciliation(candidate, pgCancelSucceeded);
		} catch (Exception e) {
			log.error("[RefundReconciliation] 상태 갱신 실패, 다음 회차에 재시도: refundId={}", candidate.getRefundId(), e);
		}
	}

}
