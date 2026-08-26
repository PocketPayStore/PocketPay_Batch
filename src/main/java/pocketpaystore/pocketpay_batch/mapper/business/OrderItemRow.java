package pocketpaystore.pocketpay_batch.mapper.business;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRow {

	private Long productId;
	private Integer quantity;

}
