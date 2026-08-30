package pocketpaystore.pocketpay_batch.mapper.batch;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.settlement.dto.SettlementCandidate;

@Mapper
public interface VendorSettlementSummaryMapper {
	int upsert(@Param("items") List<SettlementCandidate> items,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);
}
