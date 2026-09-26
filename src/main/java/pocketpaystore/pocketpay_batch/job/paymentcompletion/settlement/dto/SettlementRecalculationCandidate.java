package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRecalculationCandidate {
	private Long settlementId;
	private Long paymentId;
	private Long newAmount;
	private Long newPgFeeAmount;
	private Long newPlatformFeeAmount;
	private Long newNetAmount;
}
