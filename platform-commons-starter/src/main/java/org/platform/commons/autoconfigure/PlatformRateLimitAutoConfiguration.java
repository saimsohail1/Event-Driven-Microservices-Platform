package org.platform.commons.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.platform.commons.PlatformProperties;
import org.platform.commons.web.RateLimitFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.rate-limit", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean
    public RateLimitFilter rateLimitFilter(PlatformProperties properties, ObjectMapper objectMapper) {
        return new RateLimitFilter(
                properties.getRateLimit(),
                properties.getCorrelation().getMdcKey(),
                objectMapper);
    }
}
