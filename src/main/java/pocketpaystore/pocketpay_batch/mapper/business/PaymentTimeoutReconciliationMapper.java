package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.PaymentTimeoutCandidate;

public interface PaymentTimeoutReconciliationMapper {

	List<PaymentTimeoutCandidate> findCandidates(@Param("thresholdMinutes") long thresholdMinutes,
			@Param("lastId") long lastId, @Param("limit") int limit);

	int markPaymentDone(@Param("paymentId") Long paymentId);

	int markOrderPaid(@Param("orderId") Long orderId);
}
