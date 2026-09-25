package pocketpaystore.pocketpay_batch.job.settlement.writer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.job.settlement.dto.SettlementCandidate;
import pocketpaystore.pocketpay_batch.mapper.batch.VendorSettlementSummaryMapper;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementMapper;

@Service
@RequiredArgsConstructor
public class SettlementStateService {
	private final VendorSettlementSummaryMapper mapper;
	private final SettlementMapper settlementMapper;

	@Transactional("batchTransactionManager")
	public int saveSummaries(List<? extends SettlementCandidate> summaries,
			LocalDate startDate, LocalDate endDate) {
		if (summaries.isEmpty()) {
			return 0;
		}
		return mapper.upsert(new ArrayList<>(summaries), startDate, endDate);
	}

	@Transactional("businessTransactionManager")
	public int markSourceSettled(List<Long> vendorIds,
			LocalDate startDate, LocalDate endDate) {
		return settlementMapper.markSettled(vendorIds, startDate, endDate);
	}
}
