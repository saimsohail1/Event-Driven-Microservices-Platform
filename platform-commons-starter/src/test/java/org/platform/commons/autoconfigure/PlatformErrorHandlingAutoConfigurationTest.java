package org.platform.commons.autoconfigure;

import org.junit.jupiter.api.Test;
import org.platform.commons.web.PlatformExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformErrorHandlingAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformErrorHandlingAutoConfiguration.class));

    @Test
    void registersTheAdviceByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(PlatformExceptionHandler.class));
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwnAdvice() {
        runner.withUserConfiguration(CustomAdviceConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(PlatformExceptionHandler.class);
            assertThat(context.getBean(PlatformExceptionHandler.class))
                    .isSameAs(context.getBean("customExceptionHandler"));
        });
    }

    @Test
    void canBeTurnedOffEntirely() {
        runner.withPropertyValues("platform.error-handling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PlatformExceptionHandler.class));
    }

    @Test
    void doesNotApplyOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformErrorHandlingAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(PlatformExceptionHandler.class));
    }

    /**
     * Load-bearing: a service's own advice must out-rank the shared fallback,
     * which only works while this stays at lowest precedence.
     */
    @Test
    void isOrderedLastSoServiceAdvicesWin() {
        Order order = AnnotatedElementUtils.findMergedAnnotation(PlatformExceptionHandler.class, Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Configuration
    static class CustomAdviceConfiguration {

        @Bean
        PlatformExceptionHandler customExceptionHandler() {
            return new PlatformExceptionHandler("custom");
        }
    }
}
