package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.pointearnretry.dto.PointEarnCandidate;

@Mapper
public interface PointEarnRetryMapper {

	List<PointEarnCandidate> findPendingPointEarns(@Param("lastId") long lastId, @Param("chunkSize") int chunkSize);

	int claim(@Param("id") long id);

	Long findRemainingAmount(@Param("id") long id);

	Long findPointBalanceForUpdate(@Param("memberId") long memberId);

	int updatePointBalanceForEarn(@Param("memberId") long memberId, @Param("balance") long balance);

	int insertEarnLedger(@Param("memberId") long memberId, @Param("orderId") long orderId,
			@Param("amount") long amount, @Param("balanceAfter") long balanceAfter);

	int markResolved(@Param("id") long id);

	int markFailed(@Param("id") long id);

}
