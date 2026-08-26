package pocketpaystore.pocketpay_batch.mapper.business;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Param;

public interface OrderExpirationMapper {

	List<Long> findExpirationCandidateIds(
			@Param("thresholdMinutes") long thresholdMinutes,
			@Param("lastId") long lastId,
			@Param("limit") int limit,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	int markExpiredIfStillPending(@Param("orderId") Long orderId);

	int markExpiredIfStillStockReserved(@Param("orderIds") List<Long> orderIds);

	List<OrderItemRow> findOrderItemsByExpiredOrderIds(@Param("orderIds") List<Long> orderIds);

	Optional<OrderItemRow> findOrderItem(@Param("orderId") Long orderId);

	int releaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

}
