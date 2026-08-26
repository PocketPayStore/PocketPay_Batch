package pocketpaystore.pocketpay_batch.config;

import javax.sql.DataSource;
import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 이 앱을 처음 spring-boot-starter-batch만 넣고 띄우면, Boot가 기본으로 만들어주는
 * JobRepository는(spring-batch-core의 DefaultBatchConfiguration 기본 구현) 검증해보니
 * ResourcelessJobRepository다 — 즉 실행 이력이 DB에 전혀 남지 않는다. 이 프로젝트는
 * "마지막 성공 Chunk부터 재시작 가능"이 핵심 요구사항(CLAUDE.md 참고)이라 반드시 JDBC
 * 기반 JobRepository로 바꿔야 하고, 그 DataSource도 business가 아니라 이 레포 전용
 * batch 스키마(pocket_pay_batch)를 써야 한다. 그래서 jobRepository()를 직접
 * 오버라이드해서 명시적으로 연결한다 — DefaultBatchConfiguration을 상속한 @Configuration
 * 빈이 있으면 Boot의 기본 구성이 뒤로 빠지고 이쪽이 쓰인다(Spring Batch 공식 확장 방식).
 *
 * batchDataSource/batchTransactionManager는 DataSourceConfig가 소유한 빈을 그대로
 * 주입받는다 — 여기서 새로 만들지 않는다. Spring Batch의 chunk 트랜잭션과 batch
 * 스키마에 쓰는 MyBatis ItemWriter가 같은 PlatformTransactionManager를 공유해야
 * Chunk 커밋/롤백이 메타데이터와 우리 도메인 테이블(대사 결과 등)에 함께 적용된다.
 *
 * 주의: application-dev.yml에서 spring.batch.jdbc.initialize-schema=never로 뒀으므로,
 * 여기서 참조하는 Spring Batch 메타데이터 테이블(BATCH_JOB_INSTANCE 등)이 실제
 * pocket_pay_batch DB에 미리 존재해야 한다 — scripts/pocket_pay_batch_schema.sql을
 * 배포 전에 한 번 수동으로 적용해둘 것.
 */
@Configuration
public class BatchJobRepositoryConfig extends DefaultBatchConfiguration {

    private final DataSource batchDataSource;
    private final PlatformTransactionManager batchTransactionManager;

    public BatchJobRepositoryConfig(
            @Qualifier("batchDataSource") DataSource batchDataSource,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager) {
        this.batchDataSource = batchDataSource;
        this.batchTransactionManager = batchTransactionManager;
    }

    @Override
    public JobRepository jobRepository() {
        try {
            JdbcJobRepositoryFactoryBean factory = new JdbcJobRepositoryFactoryBean();
            factory.setDataSource(batchDataSource);
            factory.setTransactionManager(batchTransactionManager);
            factory.afterPropertiesSet();
            return factory.getObject();
        } catch (Exception e) {
            throw new IllegalStateException("배치 JobRepository 초기화 실패", e);
        }
    }

    @Override
    protected PlatformTransactionManager getTransactionManager() {
        // Job/Step 실행 전반이 이 트랜잭션 매니저를 쓰게 해서, JobRepository·MyBatis
        // batch 매퍼가 같은 batch 데이터소스 트랜잭션 하나로 묶이게 한다.
        return batchTransactionManager;
    }
}
