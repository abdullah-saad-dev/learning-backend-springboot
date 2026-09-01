package com.example.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException{
        String requestId = resolveRequestId(request);
        response.setHeader(HEADER, requestId);
        try(MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)){
            chain.doFilter(request, response);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER);
        if(inbound == null || inbound.isBlank())
            return UUID.randomUUID().toString();
        return sanitize(inbound);
    }
    private String sanitize(String value) {
        String capped = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        StringBuilder safe = new StringBuilder(capped.length());
        for (int i = 0; i < capped.length(); i++) {
            char c = capped.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_')
                safe.append(c);

        }
        return safe.isEmpty() ? UUID.randomUUID().toString() : safe.toString();
    }
}
