package org.platform.commons;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Every value the starter lets a service tune, under one prefix.
 */
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private final Correlation correlation = new Correlation();
    private final RestClient restClient = new RestClient();
    private final ErrorHandling errorHandling = new ErrorHandling();

    public Correlation getCorrelation() {
        return correlation;
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public ErrorHandling getErrorHandling() {
        return errorHandling;
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
