package pocketpaystore.pocketpay_batch.job.pointearnretry.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointEarnCandidate {
	private Long id;
	private Long memberId;
	private Long orderId;
	private Long paymentId;
	private Long amount;
}
