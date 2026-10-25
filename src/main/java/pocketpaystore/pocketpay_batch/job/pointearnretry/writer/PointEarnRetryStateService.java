package pocketpaystore.pocketpay_batch.job.pointearnretry.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.job.pointearnretry.dto.PointEarnCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.PointEarnRetryMapper;

@Service
@RequiredArgsConstructor
public class PointEarnRetryStateService {

	private final PointEarnRetryMapper mapper;

	@Transactional("businessTransactionManager")
	public boolean apply(PointEarnCandidate candidate) {
		if (mapper.claim(candidate.getId()) == 0) {
			return false;
		}
		long remaining = mapper.findRemainingAmount(candidate.getId());
		if (remaining > 0) {
			Long balance = mapper.findPointBalanceForUpdate(candidate.getMemberId());
			if (balance == null) {
				throw new IllegalStateException("포인트 잔액을 찾지 못함: memberId=" + candidate.getMemberId());
			}
			long balanceAfter = balance + remaining;
			mapper.updatePointBalanceForEarn(candidate.getMemberId(), balanceAfter);
			mapper.insertEarnLedger(candidate.getMemberId(), candidate.getOrderId(), remaining, balanceAfter);
		}
		mapper.markResolved(candidate.getId());
		return true;
	}

	@Transactional("businessTransactionManager")
	public void markFailed(long id) {
		mapper.markFailed(id);
	}

}
