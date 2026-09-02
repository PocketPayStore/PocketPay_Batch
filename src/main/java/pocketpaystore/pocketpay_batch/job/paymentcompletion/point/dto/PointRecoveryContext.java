package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointRecoveryContext {
	private Long memberId;
	private Long amount;
	private Long usedPointAmount;
}
