package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecoveryCandidate;

@Mapper
public interface SettlementRecoveryMapper {
	List<SettlementRecoveryCandidate> findPendingAlerts(@Param("lastId") long lastId,
			@Param("staleMinutes") long staleMinutes, @Param("chunkSize") int chunkSize);

	int claim(@Param("alertId") long alertId, @Param("staleMinutes") long staleMinutes);

	int insertSettlement(@Param("paymentId") long paymentId,
			@Param("pgFeeRate") double pgFeeRate, @Param("platformFeeRate") double platformFeeRate);

	int markResolved(@Param("alertId") long alertId);

	int markFailed(@Param("alertId") long alertId);
}
