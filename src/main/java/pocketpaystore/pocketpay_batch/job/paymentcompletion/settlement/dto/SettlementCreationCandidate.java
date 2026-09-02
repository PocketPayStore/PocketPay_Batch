package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCreationCandidate {
	private Long paymentId;
	private Long vendorId;
	private Long amount;
	private Long pgFeeAmount;
	private Long platformFeeAmount;
	private Long netAmount;
}
