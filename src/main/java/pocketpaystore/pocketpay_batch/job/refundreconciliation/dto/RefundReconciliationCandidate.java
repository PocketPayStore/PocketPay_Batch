package pocketpaystore.pocketpay_batch.job.refundreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RefundReconciliationCandidate {
	private Long refundId;
	private Long paymentId;
	private String pgTransactionId;
	private Long requestAmount;
	private String idempotencyKey;
	private String status;
}
