package com.secureflow.identity.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class RegistrationBodyLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 4096;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST")
                || !(request.getRequestURI().equals(request.getContextPath() + "/api/v1/auth/register")
                        || request.getRequestURI().equals(request.getContextPath() + "/api/v1/auth/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            response.setStatus(400);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":400,\"title\":\"Bad Request\",\"detail\":\"Invalid request\"}");
            return;
        }
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public ServletInputStream getInputStream() {
                        var input = new ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            @Override
                            public int read() {
                                return input.read();
                            }

                            @Override
                            public boolean isFinished() {
                                return input.available() == 0;
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setReadListener(ReadListener listener) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
                    }
                },
                response);
    }
}
