package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecalculationCandidate;

@Mapper
public interface SettlementRecalculationMapper {

	List<SettlementRecalculationCandidate> findStaleCandidates(@Param("lastId") long lastId,
			@Param("chunkSize") int chunkSize,
			@Param("pgFeeRate") double pgFeeRate,
			@Param("platformFeeRate") double platformFeeRate);

	int recalculate(@Param("candidate") SettlementRecalculationCandidate candidate);
}
