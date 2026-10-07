package org.platform.commons.autoconfigure;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.platform.commons.PlatformProperties;
import org.platform.commons.client.CorrelationIdPropagatingInterceptor;
import org.platform.commons.client.RestClientMetricsInterceptor;
import org.platform.commons.client.RetryingClientHttpRequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * A ready-made {@link RestClient}: timeouts are always set, the current
 * correlation id is always forwarded, every call is timed, and transient
 * failures are retried.
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformRestClientAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformRestClientAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdPropagatingInterceptor correlationIdPropagatingInterceptor(PlatformProperties properties) {
        PlatformProperties.Correlation correlation = properties.getCorrelation();
        return new CorrelationIdPropagatingInterceptor(correlation.getHeaderName(), correlation.getMdcKey());
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public RestClientMetricsInterceptor restClientMetricsInterceptor(MeterRegistry registry) {
        return new RestClientMetricsInterceptor(registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.rest-client.retry", name = "enabled", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "platformRestClientRetry")
    public Retry platformRestClientRetry(PlatformProperties properties) {
        PlatformProperties.Retry retryProperties = properties.getRestClient().getRetry();
        RetryConfig config = RetryConfig.<ClientHttpResponse>custom()
                .maxAttempts(retryProperties.getMaxAttempts())
                .waitDuration(retryProperties.getWaitDuration())
                .retryExceptions(IOException.class)
                .retryOnResult(RetryingClientHttpRequestInterceptor::isRetryableResponse)
                .build();

        Retry retry = Retry.of("platform-rest-client", config);
        // Retries are invisible in the call timer, which measures the whole
        // logical call, so they are logged instead.
        retry.getEventPublisher().onRetry(event ->
                log.warn("Retrying outgoing call, attempt {} of {}",
                        event.getNumberOfRetryAttempts() + 1, retryProperties.getMaxAttempts()));
        return retry;
    }

    @Bean
    @ConditionalOnBean(Retry.class)
    @ConditionalOnMissingBean
    public RetryingClientHttpRequestInterceptor retryingClientHttpRequestInterceptor(
            Retry platformRestClientRetry, PlatformProperties properties) {

        return new RetryingClientHttpRequestInterceptor(platformRestClientRetry,
                properties.getRestClient().getRetry().isRetryNonIdempotentMethods());
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient platformRestClient(RestClient.Builder builder,
                                         PlatformProperties properties,
                                         CorrelationIdPropagatingInterceptor correlationInterceptor,
                                         ObjectProvider<RestClientMetricsInterceptor> metricsInterceptor,
                                         ObjectProvider<RetryingClientHttpRequestInterceptor> retryInterceptor) {

        PlatformProperties.RestClient restClient = properties.getRestClient();
        // A client with no timeouts will eventually hang a thread pool, so
        // these are set rather than left to the JDK defaults.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(restClient.getConnectTimeout())
                .withReadTimeout(restClient.getReadTimeout());

        builder.requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor(correlationInterceptor);

        // Order matters. The timer wraps the retry so it records what the
        // caller actually waited for, and the retry stays last so that going
        // round again re-runs the real request.
        metricsInterceptor.ifAvailable(builder::requestInterceptor);
        retryInterceptor.ifAvailable(builder::requestInterceptor);

        return builder.build();
    }
}
