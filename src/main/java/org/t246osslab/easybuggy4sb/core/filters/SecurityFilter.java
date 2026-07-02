package org.t246osslab.easybuggy4sb.core.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Servlet Filter for security
 */
@Component
public class SecurityFilter implements Filter {

    @Autowired
    private MessageSource msg;
    
    /**
     * The maximum size permitted for the complete request.
     */
    private static final int REQUEST_SIZE_MAX = 1024 * 1024 * 50;

    /**
     * Prevent several security vulnerabilities.
     * 
     * @see Filter#doFilter(ServletRequest, ServletResponse, FilterChain)
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String target = request.getRequestURI();
        
         /* Prevent clickjacking if target is not /admins/clickjacking ... */
        if (!target.startsWith("/admins/clickjacking")) {
            response.addHeader("Content-Security-Policy", "frame-ancestors 'self'");
        }
        /* Prevent Content-Type sniffing */
        response.addHeader("X-Content-Type-Options", "nosniff");

        /* Prevent XSS */
        response.addHeader("X-XSS-Protection", "1; mode=block");

        /* Reject oversized uploads early without parsing the multipart body (preserves stream for Spring). */
        if ((target.startsWith("/ureupload") || target.startsWith("/xee") || target.startsWith("/xxe"))
                && request.getMethod().equalsIgnoreCase("POST")) {
            long contentLength = request.getContentLengthLong();
            if (contentLength > REQUEST_SIZE_MAX) {
                req.setAttribute("errorMessage", msg.getMessage("msg.max.file.size.exceed", null, request.getLocale()));
            }
        }

       chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        // Do nothing
    }

    @Override
    public void init(FilterConfig arg0) {
        // Do nothing
    }
}
