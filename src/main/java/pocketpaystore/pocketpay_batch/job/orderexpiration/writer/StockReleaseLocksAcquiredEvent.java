package pocketpaystore.pocketpay_batch.job.orderexpiration.writer;

import java.util.List;

import org.springframework.context.ApplicationEvent;

public class StockReleaseLocksAcquiredEvent extends ApplicationEvent {

	private final List<Long> productIds;

	public StockReleaseLocksAcquiredEvent(List<Long> productIds) {
		super(productIds);
		this.productIds = List.copyOf(productIds);
	}

	public List<Long> getProductIds() {
		return productIds;
	}
}
