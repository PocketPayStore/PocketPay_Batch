package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryCandidate;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.point.dto.PointRecoveryContext;

@Mapper
public interface PointRecoveryMapper {
	List<PointRecoveryCandidate> findPendingAlerts(@Param("lastId") long lastId,
			@Param("staleMinutes") long staleMinutes, @Param("chunkSize") int chunkSize);

	int claim(@Param("alertId") long alertId, @Param("staleMinutes") long staleMinutes);

	PointRecoveryContext findContext(@Param("paymentId") long paymentId);

	boolean existsLedgerEntry(@Param("orderId") long orderId, @Param("type") String type);

	Long findBalanceForUpdate(@Param("memberId") long memberId);

	int updateBalance(@Param("memberId") long memberId, @Param("balance") long balance);

	int insertLedger(@Param("memberId") long memberId, @Param("orderId") long orderId,
			@Param("type") String type, @Param("amount") long amount, @Param("balanceAfter") long balanceAfter);

	int markResolved(@Param("alertId") long alertId);

	int markFailed(@Param("alertId") long alertId);
}
