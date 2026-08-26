package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.mapper.business.PaymentTimeoutReconciliationMapper;

@Service
@RequiredArgsConstructor
public class PaymentTimeoutStateService {
	private final PaymentTimeoutReconciliationMapper mapper;

	@Transactional("businessTransactionManager")
	public boolean markPaidIfStillTimeoutUnknown(Long paymentId, Long orderId) {
		int paymentUpdated = mapper.markPaymentDone(paymentId);
		int orderUpdated = mapper.markOrderPaid(orderId);
		return paymentUpdated == 1 && orderUpdated == 1;
	}
}
