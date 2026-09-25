package pocketpaystore.pocketpay_batch.mapper.business;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import pocketpaystore.pocketpay_batch.job.paymentalertretry.dto.PaymentAlertCandidate;

@Mapper
public interface PaymentAlertRetryMapper {

	List<PaymentAlertCandidate> findPendingAlerts(@Param("lastId") long lastId, @Param("chunkSize") int chunkSize);

	int markResolved(@Param("id") long id);

	int markFailed(@Param("id") long id);

}
