package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import pocketpaystore.pocketpay_batch.mapper.business.OrderExpirationMapper;
import pocketpaystore.pocketpay_batch.mapper.business.OrderItemRow;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReleaseService {

	private final OrderExpirationMapper mapper;

	@Transactional("businessTransactionManager")
	public void expireAndReleaseOrder(Long orderId) {
		String status = mapper.findOrderStatusForUpdate(orderId);
		if (!isExpirable(orderId, status) || mapper.markExpired(orderId, status) == 0) {
			return;
		}
		List<Long> orderIds = List.of(orderId);

		Map<Long, Integer> quantitiesByProduct = new TreeMap<>();
		for (OrderItemRow item : mapper.findOrderItemsByExpiredOrderIds(orderIds)) {
			quantitiesByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
		}
		if (quantitiesByProduct.isEmpty()) {
			throw new IllegalStateException("[Expiration] 만료 주문의 order_item이 없습니다: orderId=" + orderId);
		}

		for (Map.Entry<Long, Integer> entry : quantitiesByProduct.entrySet()) {
			if (mapper.releaseStock(entry.getKey(), entry.getValue()) == 0) {
				throw new IllegalStateException(
						"[Expiration] 재고 원복 UPDATE 0건: orderId=" + orderId + ", productId=" + entry.getKey());
			}
			log.info("[배치] 예약 재고 복구 완료: 주문 ID={}, 상품 ID={}, 복구 수량={}", orderId, entry.getKey(), entry.getValue());
		}
	}

	private boolean isExpirable(Long orderId, String status) {
		if ("STOCK_RESERVED".equals(status)) {
			return true;
		}
		return "PAYMENT_PENDING".equals(status) && mapper.countUnfailedPaymentsForShare(orderId) == 0;
	}

}
