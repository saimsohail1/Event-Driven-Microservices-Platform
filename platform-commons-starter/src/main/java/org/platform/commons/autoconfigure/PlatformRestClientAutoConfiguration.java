package org.platform.commons.autoconfigure;

import org.platform.commons.PlatformProperties;
import org.platform.commons.client.CorrelationIdPropagatingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * A ready-made {@link RestClient}: timeouts are always set, and the current
 * correlation id is always forwarded.
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformRestClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdPropagatingInterceptor correlationIdPropagatingInterceptor(PlatformProperties properties) {
        PlatformProperties.Correlation correlation = properties.getCorrelation();
        return new CorrelationIdPropagatingInterceptor(correlation.getHeaderName(), correlation.getMdcKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient platformRestClient(RestClient.Builder builder,
                                         PlatformProperties properties,
                                         CorrelationIdPropagatingInterceptor correlationInterceptor) {

        PlatformProperties.RestClient restClient = properties.getRestClient();
        // A client with no timeouts will eventually hang a thread pool, so
        // these are set rather than left to the JDK defaults.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(restClient.getConnectTimeout())
                .withReadTimeout(restClient.getReadTimeout());

        return builder
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .requestInterceptor(correlationInterceptor)
                .build();
    }
}
