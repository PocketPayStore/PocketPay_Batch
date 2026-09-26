package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementRecalculationCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementRecalculationMapper;

@Service
@RequiredArgsConstructor
public class SettlementRecalculationStateService {

	private final SettlementRecalculationMapper mapper;

	@Transactional("businessTransactionManager")
	public boolean recalculate(SettlementRecalculationCandidate candidate) {
		return mapper.recalculate(candidate) == 1;
	}
}
