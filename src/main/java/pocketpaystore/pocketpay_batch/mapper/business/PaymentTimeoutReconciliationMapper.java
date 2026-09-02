package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.dto.PaymentTimeoutCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.dto.PointReservationContext;

public interface PaymentTimeoutReconciliationMapper {

	List<PaymentTimeoutCandidate> findCandidates(@Param("thresholdMinutes") long thresholdMinutes,
			@Param("lastId") long lastId, @Param("limit") int limit);

	int markPaymentDone(@Param("paymentId") Long paymentId);

	int markPaymentFailed(@Param("paymentId") Long paymentId);

	int markOrderPaid(@Param("orderId") Long orderId);

	int savePaymentStatusHistory(@Param("paymentId") Long paymentId);

	int saveFailedPaymentStatusHistory(@Param("paymentId") Long paymentId);

	PointReservationContext findPointReservationForUpdate(@Param("paymentId") Long paymentId);

	Long findUsedPointAmount(@Param("paymentId") Long paymentId);

	int confirmPointReservation(@Param("paymentId") Long paymentId);

	int releasePointReservation(@Param("paymentId") Long paymentId);

	int updatePointBalanceForConfirmation(@Param("memberId") Long memberId, @Param("amount") Long amount);

	int updatePointBalanceForRelease(@Param("memberId") Long memberId, @Param("amount") Long amount);

	int insertPointUseLedger(@Param("memberId") Long memberId, @Param("orderId") Long orderId,
			@Param("amount") Long amount, @Param("balanceAfter") Long balanceAfter);
}
