package pocketpaystore.pocketpay_batch.support;

import java.sql.Connection;
import java.sql.DriverManager;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class ExpirationTestSupport {

	private static final String BATCH_H2_URL = "jdbc:h2:mem:batch;MODE=MySQL;DB_CLOSE_DELAY=-1";

	private static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
					.withExposedPorts(6379);

	private static final MySQLContainer<?> MYSQL =
			new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
					.withDatabaseName("pocket_pay_store")
					.withInitScript("schema-business.sql");

	static {
		REDIS.start();
		MYSQL.start();
		initBatchSchema();
	}

	private static void initBatchSchema() {
		try (Connection connection = DriverManager.getConnection(BATCH_H2_URL, "sa", "")) {
			ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-batch.sql"));
		} catch (Exception e) {
			throw new IllegalStateException("H2 batch 스키마(Spring Batch 메타데이터) 초기화 실패", e);
		}
	}

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.datasource.business.jdbc-url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.business.username", MYSQL::getUsername);
		registry.add("spring.datasource.business.password", MYSQL::getPassword);
		registry.add("spring.datasource.business.driver-class-name", MYSQL::getDriverClassName);
		registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
	}

}
