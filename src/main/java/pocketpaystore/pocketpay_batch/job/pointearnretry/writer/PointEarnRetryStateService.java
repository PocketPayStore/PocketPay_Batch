package pocketpaystore.pocketpay_batch.job.pointearnretry.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.job.pointearnretry.dto.PointEarnCandidate;
import pocketpaystore.pocketpay_batch.mapper.business.PointEarnRetryMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointEarnRetryStateService {

	private final PointEarnRetryMapper mapper;

	public void retry(PointEarnCandidate candidate) {
		try {
			boolean applied = apply(candidate);
			if (!applied) {
				log.info("[PointEarnRetry] 이미 처리된 적립 건, 스킵: id={}", candidate.getId());
			}
		} catch (Exception e) {
			log.error("[PointEarnRetry] 재시도 실패, 다음 실행에서 다시 시도: id={}", candidate.getId(), e);
			markFailed(candidate.getId());
		}
	}

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
