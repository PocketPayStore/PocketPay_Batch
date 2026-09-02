package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRecoveryCandidate {
	private Long alertId;
	private Long paymentId;
	private Long orderId;
}
