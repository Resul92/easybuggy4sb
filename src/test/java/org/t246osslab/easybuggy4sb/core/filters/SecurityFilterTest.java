package org.t246osslab.easybuggy4sb.core.filters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

public class SecurityFilterTest {

    @Test
    public void oversizedGuardedUploadStopsBeforeController() throws Exception {
        SecurityFilter filter = new SecurityFilter();
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("msg.max.file.size.exceed", Locale.ENGLISH, "File too large");
        ReflectionTestUtils.setField(filter, "msg", messageSource);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/xxe");
        request.setContentType("multipart/form-data; boundary=easybuggy");
        request.addHeader("Content-Length", String.valueOf(51 * 1024 * 1024));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse("Oversized uploads should not continue to the controller", chain.wasCalled());
        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
    }

    private static class RecordingFilterChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            called = true;
        }

        private boolean wasCalled() {
            return called;
        }
    }
}
