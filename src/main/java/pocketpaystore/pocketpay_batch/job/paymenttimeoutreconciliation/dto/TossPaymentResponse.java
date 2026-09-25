package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스페이먼츠 결제 조회 API(GET /v1/payments/{paymentKey})가 반환하는 Payment 객체 중
 * 우리가 쓰는 필드만 옮겨 담는다. status는 READY/IN_PROGRESS/WAITING_FOR_DEPOSIT/DONE/
 * CANCELED/PARTIAL_CANCELED/ABORTED/EXPIRED 중 하나다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TossPaymentResponse {
	private String paymentKey;
	private String orderId;
	private String status;
	private LocalDateTime approvedAt;
}
