package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementCreationCandidate;

@Mapper
public interface SettlementCreationMapper {
	List<SettlementCreationCandidate> findCandidates(@Param("lastPaymentId") long lastPaymentId,
			@Param("chunkSize") int chunkSize,
			@Param("pgFeeRate") double pgFeeRate,
			@Param("platformFeeRate") double platformFeeRate);

	int insert(@Param("candidate") SettlementCreationCandidate candidate);
}
