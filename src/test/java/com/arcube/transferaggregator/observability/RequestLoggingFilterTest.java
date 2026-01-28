package com.arcube.transferaggregator.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    @Test
    void setsRequestIdAndClearsMdc() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                assertThat(MDC.get("requestId")).isNotBlank();
                assertThat(MDC.get("method")).isEqualTo("GET");
                assertThat(MDC.get("path")).isEqualTo("/api/v1/test");
            }
        });

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void logsDebugWhenEnabled() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        try {
            RequestLoggingFilter filter = new RequestLoggingFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/debug");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new FilterChain() {
                @Override
                public void doFilter(ServletRequest req, ServletResponse res) {
                }
            });
        } finally {
            logger.setLevel(previous);
        }
    }

    @Test
    void usesProvidedRequestIdWhenPresent() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/with-id");
        request.addHeader("X-Request-Id", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                assertThat(MDC.get("requestId")).isEqualTo("req-123");
            }
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
    }

    @Test
    void generatesRequestIdWhenBlankHeader() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/blank-id");
        request.addHeader("X-Request-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                assertThat(MDC.get("requestId")).isNotBlank();
            }
        });

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }
}
