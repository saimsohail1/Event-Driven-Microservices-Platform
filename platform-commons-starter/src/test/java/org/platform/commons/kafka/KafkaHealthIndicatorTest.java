package org.platform.commons.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaHealthIndicatorTest {

    private final Admin admin = mock(Admin.class);
    private final KafkaHealthIndicator indicator = new KafkaHealthIndicator(admin, Duration.ofMillis(500));

    @Test
    void reportsUpWithTheClusterItReached() {
        DescribeClusterResult cluster = mock(DescribeClusterResult.class);
        when(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture("kafka-cluster-1"));
        when(cluster.nodes()).thenReturn(KafkaFuture.completedFuture(
                List.of(new Node(0, "broker-0", 9092), new Node(1, "broker-1", 9092))));
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("clusterId", "kafka-cluster-1");
        assertThat(health.getDetails()).containsEntry("brokers", 2);
    }

    @Test
    void reportsDownWhenNoBrokerAnswers() {
        when(admin.describeCluster(any(DescribeClusterOptions.class)))
                .thenThrow(new KafkaException("no brokers available"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void passesTheConfiguredTimeoutToTheBrokerCall() {
        DescribeClusterResult cluster = mock(DescribeClusterResult.class);
        when(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture("id"));
        when(cluster.nodes()).thenReturn(KafkaFuture.completedFuture(List.of()));
        when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);

        new KafkaHealthIndicator(admin, Duration.ofSeconds(3)).health();

        ArgumentCaptor<DescribeClusterOptions> options = ArgumentCaptor.forClass(DescribeClusterOptions.class);
        verify(admin).describeCluster(options.capture());
        // A health check must not outlive the probe that called it.
        assertThat(options.getValue().timeoutMs()).isEqualTo(3000);
    }
}
