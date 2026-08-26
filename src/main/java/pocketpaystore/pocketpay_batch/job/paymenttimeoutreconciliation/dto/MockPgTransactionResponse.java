package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MockPgTransactionResponse {
	private String pgTransactionId;
	private String status;
	private LocalDateTime approvedAt;
}
