package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.PaymentTimeoutCandidate;
import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.TossPaymentResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutItemWriter implements ItemWriter<PaymentTimeoutCandidate> {
	private final TossPgClient tossPgClient;
	private final PaymentTimeoutStateService stateService;

	@Override
	public void write(Chunk<? extends PaymentTimeoutCandidate> chunk) {
		for (PaymentTimeoutCandidate payment : chunk.getItems()) reconcile(payment);
	}

	private void reconcile(PaymentTimeoutCandidate payment) {
		try {
			TossPaymentResponse response = tossPgClient.getPayment(payment.getPgTransactionId());
			if ("DONE".equals(response.getStatus())) {
				if (stateService.markPaidIfStillTimeoutUnknown(payment.getPaymentId(), payment.getOrderId(),
						payment.getOrderNumber(), payment.getAmount(), payment.getMemberId())) {
					log.warn("[PaymentTimeout] PG 승인 확인 후 결제 완료 정정: orderId={}, paymentId={}", payment.getOrderId(), payment.getPaymentId());
				}
			} else if (isDefinitiveFailure(response.getStatus())
					&& stateService.markFailedIfStillTimeoutUnknown(
						payment.getPaymentId(), payment.getOrderId(), payment.getOrderNumber())) {
				log.warn("[PaymentTimeout] PG 승인 실패 확인 후 결제 실패 정정: orderId={}, paymentId={}", payment.getOrderId(), payment.getPaymentId());
			}
		} catch (HttpClientErrorException.NotFound e) {
			log.warn("[PaymentTimeout] PG 거래를 찾지 못해 다음 회차에 재확인: paymentId={}", payment.getPaymentId());
		} catch (Exception e) {
			log.error("[PaymentTimeout] PG 재확인 실패, 다음 회차에 재시도: paymentId={}", payment.getPaymentId(), e);
		}
	}

	private boolean isDefinitiveFailure(String status) {
		return "ABORTED".equals(status) || "EXPIRED".equals(status) || "CANCELED".equals(status);
	}
}
