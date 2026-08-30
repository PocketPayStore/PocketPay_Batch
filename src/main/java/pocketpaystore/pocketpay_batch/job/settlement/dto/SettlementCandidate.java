package pocketpaystore.pocketpay_batch.job.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCandidate {
	private Long vendorId;
	private Long originalAmount;
	private Long pgFeeAmount;
	private Long platformFeeAmount;
	private Long finalAmount;
	private Long settlementCount;
}
