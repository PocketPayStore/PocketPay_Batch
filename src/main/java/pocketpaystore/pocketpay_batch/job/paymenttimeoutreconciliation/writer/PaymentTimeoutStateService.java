package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.event.PaymentStatusChangedEvent;
import pocketpaystore.pocketpay_batch.mapper.business.PaymentTimeoutReconciliationMapper;

@Service
@RequiredArgsConstructor
public class PaymentTimeoutStateService {
	private final PaymentTimeoutReconciliationMapper mapper;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional("businessTransactionManager")
	public boolean markPaidIfStillTimeoutUnknown(Long paymentId, Long orderId, String orderNumber) {
		int paymentUpdated = mapper.markPaymentDone(paymentId);
		int orderUpdated = mapper.markOrderPaid(orderId);
		if (paymentUpdated == 0 && orderUpdated == 0) {
			return false;
		}
		if (paymentUpdated != 1 || orderUpdated != 1) {
			throw new IllegalStateException("[PaymentTimeout] payment/order 상태 보정 결과 불일치: paymentId="
					+ paymentId + ", orderId=" + orderId);
		}
		mapper.savePaymentStatusHistory(paymentId);
		eventPublisher.publishEvent(PaymentStatusChangedEvent.create(paymentId, orderId, orderNumber));
		return true;
	}
}
