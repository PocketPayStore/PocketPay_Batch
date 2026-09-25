package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TossCancelResponse {
	private String paymentKey;
	private String status;
}
