package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.event;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentStatusChangedEvent {
	private String eventId;
	private Long paymentId;
	private Long orderId;
	private String orderNumber;
	private String status;
	private LocalDateTime updatedAt;

	public static PaymentStatusChangedEvent create(Long paymentId, Long orderId, String orderNumber) {
		return new PaymentStatusChangedEvent(
				UUID.randomUUID().toString(), paymentId, orderId, orderNumber, "DONE", LocalDateTime.now());
	}
}
