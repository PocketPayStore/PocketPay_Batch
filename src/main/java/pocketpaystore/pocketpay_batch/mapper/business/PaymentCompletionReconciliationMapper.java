package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentCompletionReconciliationMapper {
	List<Map<String, Object>> findPendingAlerts(long lastId, int limit);
	int markProcessing(long alertId);
	int completeStock(long orderId);
	int completePoint(long orderId);
	int completeSettlement(long paymentId);
	int markResolved(long alertId);
	int markFailed(long alertId);
}
