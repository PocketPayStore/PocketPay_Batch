package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TossCancelRequest {
	private String cancelReason;
	private Long cancelAmount;
}
