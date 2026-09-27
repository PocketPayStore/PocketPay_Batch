package pocketpaystore.pocketpay_batch.job.refundreconciliation.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.refundreconciliation.dto.RefundReconciliationCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.RefundReconciliationMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundReconciliationStateService {

	private static final String CANCEL_REASON = "REFUND";

	private final RefundReconciliationMapper mapper;

	@Transactional("businessTransactionManager")
	public void applyReconciliation(RefundReconciliationCandidate candidate, boolean pgCancelSucceeded) {
		if ("PROCESSING".equals(candidate.getStatus())
				&& mapper.completeIfStillProcessing(candidate.getRefundId()) == 1) {
			mapper.insertPaymentCancel(candidate.getPaymentId(), candidate.getRefundId(),
					candidate.getRequestAmount(), CANCEL_REASON);
			log.warn("[RefundReconciliation] PROCESSING에 멈춰있던 환불을 완료 처리: refundId={}", candidate.getRefundId());
		}
		if (pgCancelSucceeded) {
			mapper.markPgCancelConfirmed(candidate.getRefundId());
		}
	}

}
