package org.platform.commons.autoconfigure;

import org.platform.commons.PlatformProperties;
import org.platform.commons.correlation.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.correlation", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformCorrelationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter(PlatformProperties properties) {
        PlatformProperties.Correlation correlation = properties.getCorrelation();
        return new CorrelationIdFilter(correlation.getHeaderName(), correlation.getMdcKey());
    }
}
