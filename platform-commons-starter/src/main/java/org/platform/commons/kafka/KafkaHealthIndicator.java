package org.platform.commons.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Reports whether the broker cluster can be reached. Spring Boot ships no
 * Kafka health indicator, so a service with an unreachable broker otherwise
 * reports itself perfectly healthy while consuming nothing.
 * <p>
 * The cluster description is the cheapest call that still proves a round trip
 * to a broker; it needs no topic to exist and no ACL beyond the connection.
 */
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final Admin admin;
    private final Duration timeout;

    public KafkaHealthIndicator(Admin admin, Duration timeout) {
        super("Kafka health check failed");
        this.admin = admin;
        this.timeout = timeout;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long timeoutMillis = timeout.toMillis();
        DescribeClusterResult cluster = admin.describeCluster(
                new DescribeClusterOptions().timeoutMs((int) timeoutMillis));

        // A bounded get, so a hung broker cannot stall the health endpoint
        // past the timeout the service configured.
        String clusterId = cluster.clusterId().get(timeoutMillis, TimeUnit.MILLISECONDS);
        int brokers = cluster.nodes().get(timeoutMillis, TimeUnit.MILLISECONDS).size();

        builder.up()
                .withDetail("clusterId", clusterId)
                .withDetail("brokers", brokers);
    }
}
