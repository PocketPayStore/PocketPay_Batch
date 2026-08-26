package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import pocketpaystore.pocketpay_batch.mapper.business.OrderExpirationMapper;

@Service
@RequiredArgsConstructor
public class OrderExpirationStateService {

	private final OrderExpirationMapper mapper;

	@Transactional("businessTransactionManager")
	public boolean markExpiredIfStillPending(Long orderId) {
		return mapper.markExpiredIfStillPending(orderId) == 1;
	}

}
