package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTimeoutCandidate {
	private Long paymentId;
	private Long orderId;
	private String pgTransactionId;
	private Long amount;
}
