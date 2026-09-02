package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymentcompletion.stock.dto.StockRecoveryCandidate;

@Mapper
public interface StockRecoveryMapper {
	List<StockRecoveryCandidate> findPendingAlerts(@Param("lastId") long lastId,
			@Param("staleMinutes") long staleMinutes, @Param("chunkSize") int chunkSize);

	int claim(@Param("alertId") long alertId, @Param("staleMinutes") long staleMinutes);

	Long findProductIdByOrderId(@Param("orderId") long orderId);

	int confirmStock(@Param("orderId") long orderId);

	int markResolved(@Param("alertId") long alertId);

	int markFailed(@Param("alertId") long alertId);
}
