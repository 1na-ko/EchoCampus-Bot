package com.echocampus.bot.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XssHttpServletRequestWrapperTest {

    @Mock
    private HttpServletRequest request;

    private XssHttpServletRequestWrapper wrapper;

    @BeforeEach
    void setUp() {
        wrapper = new XssHttpServletRequestWrapper(request);
    }

    @Test
    void testGetParameter_WithXssScript_ShouldEscape() {
        when(request.getParameter("test")).thenReturn("<script>alert('xss')</script>");

        String result = wrapper.getParameter("test");

        assertNotEquals("<script>alert('xss')</script>", result);
        assertTrue(result.contains("&lt;"));
    }

    @Test
    void testGetParameter_WithNullValue_ShouldReturnNull() {
        when(request.getParameter("test")).thenReturn(null);

        String result = wrapper.getParameter("test");

        assertNull(result);
    }

    @Test
    void testGetParameter_WithNormalText_ShouldReturnSame() {
        when(request.getParameter("test")).thenReturn("normal text");

        String result = wrapper.getParameter("test");

        assertEquals("normal text", result);
    }

    @Test
    void testGetParameterValues_WithXssScript_ShouldEscape() {
        when(request.getParameterValues("test")).thenReturn(new String[]{"<script>alert('xss')</script>", "normal"});

        String[] result = wrapper.getParameterValues("test");

        assertNotNull(result);
        assertEquals(2, result.length);
        assertNotEquals("<script>alert('xss')</script>", result[0]);
        assertEquals("normal", result[1]);
    }

    @Test
    void testGetParameterValues_WithNullValue_ShouldReturnNull() {
        when(request.getParameterValues("test")).thenReturn(null);

        String[] result = wrapper.getParameterValues("test");

        assertNull(result);
    }

    @Test
    void testGetHeader_WithXssScript_ShouldEscape() {
        when(request.getHeader("test")).thenReturn("<script>alert('xss')</script>");

        String result = wrapper.getHeader("test");

        assertNotEquals("<script>alert('xss')</script>", result);
        assertTrue(result.contains("&lt;"));
    }

    @Test
    void testGetHeader_WithNullValue_ShouldReturnNull() {
        when(request.getHeader("test")).thenReturn(null);

        String result = wrapper.getHeader("test");

        assertNull(result);
    }
}
