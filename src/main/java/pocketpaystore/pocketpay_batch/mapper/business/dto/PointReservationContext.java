package pocketpaystore.pocketpay_batch.mapper.business.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PointReservationContext {
	private Long memberId;
	private Long amount;
	private String status;
	private Long balance;
	private Long reservedAmount;
}
