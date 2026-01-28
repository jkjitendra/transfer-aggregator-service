package com.arcube.transferaggregator.observability;

import com.arcube.transferaggregator.config.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TenantFilterTest {

    @Test
    void setsAndClearsTenantContext() throws Exception {
        TenantFilter filter = new TenantFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "tenant-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                assertThat(TenantContext.getTenantId()).isEqualTo("tenant-a");
            }
        });

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void doesNothingWhenHeaderMissing() throws Exception {
        TenantFilter filter = new TenantFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                assertThat(TenantContext.getTenantId()).isNull();
            }
        });

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void ignoresBlankTenantHeader() throws Exception {
        TenantFilter filter = new TenantFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                assertThat(TenantContext.getTenantId()).isNull();
            }
        });

        assertThat(TenantContext.getTenantId()).isNull();
    }
}
