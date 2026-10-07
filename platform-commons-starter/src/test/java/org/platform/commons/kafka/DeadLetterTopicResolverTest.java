package org.platform.commons.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterTopicResolverTest {

    @Test
    void appendsTheSuffixToTheSourceTopic() {
        TopicPartition destination = new DeadLetterTopicResolver(".DLT")
                .apply(record("order.created", 2), new IllegalStateException());

        assertThat(destination.topic()).isEqualTo("order.created.DLT");
    }

    @Test
    void leavesThePartitionToTheBroker() {
        TopicPartition destination = new DeadLetterTopicResolver(".DLT")
                .apply(record("order.created", 7), new IllegalStateException());

        // Mirroring partition 7 would fail on a single-partition DLT.
        assertThat(destination.partition()).isEqualTo(-1);
    }

    @Test
    void honoursACustomSuffix() {
        TopicPartition destination = new DeadLetterTopicResolver("-failed")
                .apply(record("payment.requested", 0), new IllegalStateException());

        assertThat(destination.topic()).isEqualTo("payment.requested-failed");
    }

    private static ConsumerRecord<String, String> record(String topic, int partition) {
        return new ConsumerRecord<>(topic, partition, 0L, "key", "value");
    }
}
