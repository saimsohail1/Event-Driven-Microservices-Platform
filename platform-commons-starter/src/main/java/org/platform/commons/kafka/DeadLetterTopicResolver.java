package org.platform.commons.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

import java.util.function.BiFunction;

/**
 * Names the dead-letter topic for a record that could not be processed.
 */
public class DeadLetterTopicResolver implements BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {

    /** Partition left to the broker; see {@link #apply}. */
    static final int ANY_PARTITION = -1;

    private final String suffix;

    public DeadLetterTopicResolver(String suffix) {
        this.suffix = suffix;
    }

    /**
     * Keeps the partition unset rather than mirroring the source partition,
     * which fails outright whenever the dead-letter topic has fewer
     * partitions than the topic it shadows.
     */
    @Override
    public TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception) {
        return new TopicPartition(record.topic() + suffix, ANY_PARTITION);
    }
}
