package pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockRecoveryCandidate {
	private Long alertId;
	private Long paymentId;
	private Long orderId;
}
