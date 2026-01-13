package com.echocampus.bot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XssFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private XssFilter xssFilter;

    @Test
    void testDoFilterInternal_ShouldWrapRequest() throws ServletException, IOException {
        xssFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(XssHttpServletRequestWrapper.class), eq(response));
    }

    @Test
    void testDoFilterInternal_ShouldNotThrowException() throws ServletException, IOException {
        xssFilter.doFilterInternal(request, response, filterChain);
    }
}
