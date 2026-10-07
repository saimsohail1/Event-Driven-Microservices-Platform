package org.platform.commons.autoconfigure;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.platform.commons.correlation.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCorrelationAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformCorrelationAutoConfiguration.class));

    @Test
    void registersTheFilterByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(CorrelationIdFilter.class));
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnFilter() {
        runner.withUserConfiguration(CustomFilterConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CorrelationIdFilter.class);
            assertThat(context.getBean(CorrelationIdFilter.class))
                    .isSameAs(context.getBean("customCorrelationIdFilter"));
        });
    }

    @Test
    void canBeTurnedOffEntirely() {
        runner.withPropertyValues("platform.correlation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CorrelationIdFilter.class));
    }

    @Test
    void doesNotApplyOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformCorrelationAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(CorrelationIdFilter.class));
    }

    @Test
    void honoursTheConfiguredHeaderAndMdcKey() {
        runner.withPropertyValues(
                        "platform.correlation.header-name=X-Request-Trace",
                        "platform.correlation.mdc-key=traceId")
                .run(context -> {
                    CorrelationIdFilter filter = context.getBean(CorrelationIdFilter.class);

                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addHeader("X-Request-Trace", "from-upstream");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    AtomicReference<String> seen = new AtomicReference<>();
                    FilterChain chain = (req, res) -> seen.set(MDC.get("traceId"));
                    filter.doFilter(request, response, chain);

                    assertThat(seen.get()).isEqualTo("from-upstream");
                    assertThat(response.getHeader("X-Request-Trace")).isEqualTo("from-upstream");
                });
    }

    @Configuration
    static class CustomFilterConfiguration {

        @Bean
        CorrelationIdFilter customCorrelationIdFilter() {
            return new CorrelationIdFilter("X-Custom", "custom");
        }
    }
}
