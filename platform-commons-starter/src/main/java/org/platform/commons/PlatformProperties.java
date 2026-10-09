package org.platform.commons;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Every value the starter lets a service tune, under one prefix.
 */
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private final Correlation correlation = new Correlation();
    private final RestClient restClient = new RestClient();
    private final ErrorHandling errorHandling = new ErrorHandling();
    private final Metrics metrics = new Metrics();
    private final Kafka kafka = new Kafka();
    private final RateLimit rateLimit = new RateLimit();

    public Correlation getCorrelation() {
        return correlation;
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public ErrorHandling getErrorHandling() {
        return errorHandling;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Correlation {

        /** Whether to install the correlation-id filter. */
        private boolean enabled = true;

        /** Header carrying the correlation id, inbound and outbound. */
        private String headerName = "X-Correlation-Id";

        /** MDC key the id is published under, which is what log output reads. */
        private String mdcKey = "correlationId";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getMdcKey() {
            return mdcKey;
        }

        public void setMdcKey(String mdcKey) {
            this.mdcKey = mdcKey;
        }
    }

    public static class RestClient {

        /** How long to wait for a connection to be established. */
        private Duration connectTimeout = Duration.ofSeconds(2);

        /** How long to wait for a response once connected. */
        private Duration readTimeout = Duration.ofSeconds(5);

        private final Retry retry = new Retry();

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Retry getRetry() {
            return retry;
        }
    }

    public static class Retry {

        /** Whether to retry failed outgoing calls. */
        private boolean enabled = true;

        /** Total attempts, so 3 means one call and two retries. */
        private int maxAttempts = 3;

        /** Fixed pause between attempts. */
        private Duration waitDuration = Duration.ofMillis(200);

        /**
         * Whether POST and PATCH are retried too. Off by default: a read
         * timeout does not tell us whether the server already applied the
         * write, so retrying one risks duplicating it.
         */
        private boolean retryNonIdempotentMethods = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }

        public boolean isRetryNonIdempotentMethods() {
            return retryNonIdempotentMethods;
        }

        public void setRetryNonIdempotentMethods(boolean retryNonIdempotentMethods) {
            this.retryNonIdempotentMethods = retryNonIdempotentMethods;
        }
    }

    public static class Metrics {

        /** Whether to add the common tags to every meter. */
        private boolean enabled = true;

        /** Value of the {@code service} tag. Falls back to spring.application.name. */
        private String serviceName;

        /** Value of the {@code environment} tag. */
        private String environment = "local";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }

    public static class Kafka {

        /** Whether to apply the shared Kafka defaults. */
        private boolean enabled = true;

        private final KafkaProducer producer = new KafkaProducer();
        private final KafkaConsumer consumer = new KafkaConsumer();
        private final KafkaHealth health = new KafkaHealth();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public KafkaProducer getProducer() {
            return producer;
        }

        public KafkaConsumer getConsumer() {
            return consumer;
        }

        public KafkaHealth getHealth() {
            return health;
        }
    }

    public static class KafkaProducer {

        /**
         * Whether to default the producer to acks=all with idempotence on.
         * Keys a service sets itself under spring.kafka are left alone.
         */
        private boolean applyDefaults = true;

        public boolean isApplyDefaults() {
            return applyDefaults;
        }

        public void setApplyDefaults(boolean applyDefaults) {
            this.applyDefaults = applyDefaults;
        }
    }

    public static class KafkaConsumer {

        /** Delivery attempts before the record goes to the dead-letter topic. */
        private int maxAttempts = 3;

        /** Pause between delivery attempts. */
        private Duration backoff = Duration.ofSeconds(2);

        /** Appended to the source topic name to form the dead-letter topic. */
        private String deadLetterSuffix = ".DLT";

        /**
         * Exceptions that go straight to the dead-letter topic without being
         * retried, as fully qualified class names. Poison messages and
         * business rejections belong here; retrying them never helps.
         */
        private List<String> nonRetryableExceptions = new ArrayList<>();

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }

        public String getDeadLetterSuffix() {
            return deadLetterSuffix;
        }

        public void setDeadLetterSuffix(String deadLetterSuffix) {
            this.deadLetterSuffix = deadLetterSuffix;
        }

        public List<String> getNonRetryableExceptions() {
            return nonRetryableExceptions;
        }

        public void setNonRetryableExceptions(List<String> nonRetryableExceptions) {
            this.nonRetryableExceptions = nonRetryableExceptions;
        }
    }

    public static class KafkaHealth {

        /** How long the broker metadata lookup is allowed to take. */
        private Duration timeout = Duration.ofSeconds(2);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class RateLimit {

        /** Whether to install the inbound rate-limit filter. */
        private boolean enabled = true;

        /** Requests allowed from one client during {@link #window}. */
        private int requests = 100;

        /** How long a client's budget lasts before it refills. */
        private Duration window = Duration.ofMinutes(1);

        /**
         * How many distinct client keys to remember. Extra clients share one
         * overflow bucket so a scan cannot grow the map without bound.
         */
        private int maxKeys = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = requests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getMaxKeys() {
            return maxKeys;
        }

        public void setMaxKeys(int maxKeys) {
            this.maxKeys = maxKeys;
        }
    }

    public static class ErrorHandling {

        /** Whether to install the shared controller advice. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
