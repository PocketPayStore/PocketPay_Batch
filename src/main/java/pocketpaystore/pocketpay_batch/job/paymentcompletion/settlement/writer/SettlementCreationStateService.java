package pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_batch.job.paymentcompletion.settlement.dto.SettlementCreationCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.SettlementCreationMapper;

@Service
@RequiredArgsConstructor
public class SettlementCreationStateService {

	private final SettlementCreationMapper mapper;

	@Transactional("businessTransactionManager")
	public boolean create(SettlementCreationCandidate candidate) {
		return mapper.insert(candidate) == 1;
	}
}
