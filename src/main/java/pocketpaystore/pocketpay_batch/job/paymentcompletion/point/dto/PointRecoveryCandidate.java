package pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointRecoveryCandidate {
	private Long alertId;
	private Long paymentId;
	private Long orderId;
	private String completionStep;
}
