package pocketpaystore.pocketpay_batch.job.paymentalertretry.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAlertCandidate {
	private Long id;
	private String alertType;
	private String severity;
	private Long paymentId;
	private Long orderId;
	private String message;

	public String formattedMessage() {
		return "[" + severity + "] " + alertType + " (paymentId=" + paymentId + ", orderId=" + orderId + "): " + message;
	}
}
