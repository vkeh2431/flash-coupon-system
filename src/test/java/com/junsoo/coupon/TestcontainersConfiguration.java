package com.junsoo.coupon;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// docker-compose.yaml과 동일한 버전으로 고정한다.
	private static final String MYSQL_IMAGE = "mysql:8.4";
	private static final String REDIS_IMAGE = "redis:7-alpine";
	private static final int REDIS_PORT = 6379;

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse(MYSQL_IMAGE));
	}

	@Bean
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
				.withExposedPorts(REDIS_PORT);
	}

	// Redisson은 spring.data.redis를 쓰지 않으므로 @ServiceConnection이 붙지 않는다.
	@Bean
	DynamicPropertyRegistrar redisPropertyRegistrar(GenericContainer<?> redisContainer) {
		return registry -> registry.add("app.redis.address",
				() -> "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(REDIS_PORT));
	}

}
