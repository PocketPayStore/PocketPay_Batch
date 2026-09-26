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

	/**
	 * PG 취소 성공 여부는 트랜잭션 밖(HTTP 호출)에서 이미 판정된 뒤 넘어온다. PROCESSING에 멈춰있던 환불은
	 * PG 취소 성공 여부와 무관하게 로컬 완료 처리(감사 기록 + 상태 전환)까지 마친다 — Core의 doRefund()도
	 * PG 취소가 best-effort라 실패해도 로컬 완료는 그대로 진행하는 것과 동일한 정책이다.
	 */
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
