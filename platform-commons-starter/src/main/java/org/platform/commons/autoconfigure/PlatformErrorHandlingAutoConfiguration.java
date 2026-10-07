package org.platform.commons.autoconfigure;

import org.platform.commons.PlatformProperties;
import org.platform.commons.web.PlatformExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.error-handling", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PlatformExceptionHandler platformExceptionHandler(PlatformProperties properties) {
        return new PlatformExceptionHandler(properties.getCorrelation().getMdcKey());
    }
}
