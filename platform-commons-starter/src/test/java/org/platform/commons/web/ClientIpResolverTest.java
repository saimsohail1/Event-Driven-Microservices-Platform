package org.platform.commons.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void usesTheFirstForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.setRemoteAddr("10.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void fallsBackToTheSocketAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.5");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.0.2.5");
    }

    @Test
    void dropsCharactersThatDoNotBelongInAnAddress() {
        assertThat(ClientIpResolver.sanitize("1.2.3.4\nX-Injected: yes")).isEqualTo("1.2.3.4X-Injected:yes");
    }
}
