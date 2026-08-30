package pocketpaystore.pocketpay_batch.job.paymenttimeoutreconciliation.event;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPaymentStatusEventListener {
	private final RedissonClient redissonClient;
	private final ObjectMapper objectMapper;

	@Value("${payment-events.channel}")
	private String channel;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(PaymentStatusChangedEvent event) {
		try {
			redissonClient.getTopic(channel).publish(objectMapper.writeValueAsString(event));
		} catch (Exception e) {
			log.error("[PaymentEvent] Redis 발행 실패: eventId={}, paymentId={}",
					event.getEventId(), event.getPaymentId(), e);
		}
	}
}
