package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.refundreconciliation.dto.RefundReconciliationCandidate;

public interface RefundReconciliationMapper {

	List<RefundReconciliationCandidate> findCandidates(@Param("thresholdMinutes") long thresholdMinutes,
			@Param("lastId") long lastId, @Param("limit") int limit);

	int completeIfStillProcessing(@Param("refundId") Long refundId);

	int insertPaymentCancel(@Param("paymentId") Long paymentId, @Param("refundId") Long refundId,
			@Param("cancelAmount") Long cancelAmount, @Param("reason") String reason);

	int markPgCancelConfirmed(@Param("refundId") Long refundId);

}
