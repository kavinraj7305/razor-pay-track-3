package com.razorpayhackthon.revenue_recovery.service.auth;

import com.razorpayhackthon.revenue_recovery.dto.auth.PlatformComponent;
import com.razorpayhackthon.revenue_recovery.dto.auth.PlatformStatus;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlatformStatusService {

	private final DataSource dataSource;
	private final StringRedisTemplate redis;
	private final String kafkaBootstrap;
	private final String paymentTopic;
	private final String invoiceTopic;
	private final String checkoutTopic;

	public PlatformStatusService(
			DataSource dataSource,
			StringRedisTemplate redis,
			@Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrap,
			@Value("${recovery.kafka.payment-events-topic:payment.events}") String paymentTopic,
			@Value("${recovery.kafka.invoice-events-topic:invoice.events}") String invoiceTopic,
			@Value("${recovery.kafka.checkout-events-topic:checkout.events}") String checkoutTopic) {
		this.dataSource = dataSource;
		this.redis = redis;
		this.kafkaBootstrap = kafkaBootstrap;
		this.paymentTopic = paymentTopic;
		this.invoiceTopic = invoiceTopic;
		this.checkoutTopic = checkoutTopic;
	}

	public PlatformStatus snapshot() {
		PlatformComponent redisNode = redisStatus();
		PlatformComponent kafkaNode = kafkaStatus();
		PlatformComponent ledger = postgresStatus();
		return assemble(redisNode, kafkaNode, ledger);
	}

	static PlatformStatus assemble(PlatformComponent redis, PlatformComponent kafka, PlatformComponent ledger) {
		return new PlatformStatus(redis.connected() && kafka.connected() && ledger.connected(), List.of(redis, kafka, ledger));
	}

	private PlatformComponent redisStatus() {
		try {
			RedisConnectionFactory factory = redis.getConnectionFactory();
			if (factory == null) {
				return down("redis", "Redis", "Duplicate-event lock", "No connection factory");
			}
			try (RedisConnection connection = factory.getConnection()) {
				String pong = connection.ping();
				if (pong == null || pong.isBlank()) {
					return down("redis", "Redis", "Duplicate-event lock", "No reply");
				}
				return new PlatformComponent("redis", "Redis", true, "Duplicate-event lock", "One webhook, one case");
			}
		} catch (Exception ex) {
			return down("redis", "Redis", "Duplicate-event lock", "Unreachable");
		}
	}

	private PlatformComponent kafkaStatus() {
		Properties props = new Properties();
		props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
		props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
		props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
		props.put(AdminClientConfig.CLIENT_ID_CONFIG, "recovery-platform-status");
		try (AdminClient client = AdminClient.create(props)) {
			DescribeClusterResult cluster = client.describeCluster();
			cluster.clusterId().get(2, TimeUnit.SECONDS);
			String bus = paymentTopic + " · " + invoiceTopic + " · " + checkoutTopic;
			return new PlatformComponent("kafka", "Kafka", true, "Payment event bus", bus);
		} catch (Exception ex) {
			return down("kafka", "Kafka", "Payment event bus", "Unreachable");
		}
	}

	private PlatformComponent postgresStatus() {
		try (Connection connection = dataSource.getConnection()) {
			if (!connection.isValid(2)) {
				return down("postgres", "Postgres", "Case ledger", "Connection not valid");
			}
			return new PlatformComponent("postgres", "Postgres", true, "Case ledger", "Open book + audit");
		} catch (Exception ex) {
			return down("postgres", "Postgres", "Case ledger", "Unreachable");
		}
	}

	private static PlatformComponent down(String id, String name, String role, String detail) {
		return new PlatformComponent(id, name, false, role, detail);
	}
}
