package pocketpaystore.pocketpay_batch.mapper.business;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import pocketpaystore.pocketpay_batch.job.settlement.dto.SettlementCandidate;

@Mapper
public interface SettlementMapper {
	List<SettlementCandidate> findPending(@Param("lastId") long lastId,
			@Param("limit") int limit,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	int markSettled(@Param("vendorIds") List<Long> vendorIds,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);
}
