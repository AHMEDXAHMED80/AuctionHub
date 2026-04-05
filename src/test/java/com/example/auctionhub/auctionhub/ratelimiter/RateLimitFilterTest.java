package com.example.auctionhub.auctionhub.ratelimiter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock private FilterChain chain;

    @BeforeEach
    void setUp() {
        // fresh filter = fresh buckets for every test
        filter = new RateLimitFilter();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MockHttpServletRequest request(String path, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        req.setRemoteAddr(ip);
        return req;
    }

    private MockHttpServletResponse doFilter(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    private void exhaust(String path, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            filter.doFilter(request(path, ip), new MockHttpServletResponse(), chain);
        }
    }

    // -----------------------------------------------------------------------
    // IP extraction
    // -----------------------------------------------------------------------

    @Test
    void getClientIp_usesRemoteAddrAsFallback() throws Exception {
        MockHttpServletRequest req = request("/api/items", "10.0.0.1");
        doFilter(req);
        // bucket key uses "10.0.0.1:general" — if a second request from same IP
        // is within limit it should also pass (proves IP was read correctly)
        MockHttpServletResponse res = doFilter(req);
        assertEquals(200, res.getStatus());
    }

    @Test
    void getClientIp_usesXForwardedForHeader() throws Exception {
        MockHttpServletRequest req = request("/api/items", "10.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.5");

        // exhaust auth limit for forwarded IP, not for remoteAddr
        exhaust("/api/auth/login", "203.0.113.5", 5);

        MockHttpServletRequest authReq = request("/api/auth/login", "10.0.0.1");
        authReq.addHeader("X-Forwarded-For", "203.0.113.5");
        MockHttpServletResponse res = doFilter(authReq);

        assertEquals(429, res.getStatus(), "Forwarded IP should be rate limited, not remoteAddr");
    }

    @Test
    void getClientIp_usesFirstIpFromXForwardedForWhenMultiple() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);

        MockHttpServletRequest req = request("/api/auth/login", "99.99.99.99");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 9.10.11.12");

        MockHttpServletResponse res = doFilter(req);
        assertEquals(429, res.getStatus(), "First IP in X-Forwarded-For chain should be used");
    }

    @Test
    void getClientIp_usesXRealIpWhenForwardedForAbsent() throws Exception {
        exhaust("/api/auth/login", "55.55.55.55", 5);

        MockHttpServletRequest req = request("/api/auth/login", "99.99.99.99");
        req.addHeader("X-Real-IP", "55.55.55.55");

        MockHttpServletResponse res = doFilter(req);
        assertEquals(429, res.getStatus(), "X-Real-IP should be used when X-Forwarded-For is absent");
    }

    @Test
    void getClientIp_prefersXForwardedForOverXRealIp() throws Exception {
        exhaust("/api/auth/login", "1.1.1.1", 5);

        MockHttpServletRequest req = request("/api/auth/login", "99.99.99.99");
        req.addHeader("X-Forwarded-For", "1.1.1.1");
        req.addHeader("X-Real-IP", "2.2.2.2");

        MockHttpServletResponse res = doFilter(req);
        assertEquals(429, res.getStatus(), "X-Forwarded-For should take precedence over X-Real-IP");
    }

    // -----------------------------------------------------------------------
    // Auth endpoint — 5 per minute
    // -----------------------------------------------------------------------

    @Test
    void authEndpoint_allowsFirst5Requests() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void authEndpoint_blocks6thRequest() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);
        MockHttpServletResponse res = doFilter(request("/api/auth/login", "1.2.3.4"));
        assertEquals(429, res.getStatus());
    }

    @Test
    void authEndpoint_blockedResponse_hasJsonContentType() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);
        MockHttpServletResponse res = doFilter(request("/api/auth/login", "1.2.3.4"));
        assertEquals("application/json", res.getContentType());
    }

    @Test
    void authEndpoint_blockedResponse_bodyContainsTooManyRequests() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);
        MockHttpServletResponse res = doFilter(request("/api/auth/login", "1.2.3.4"));
        assertTrue(res.getContentAsString().contains("Too many requests"));
    }

    @Test
    void authEndpoint_blockedResponse_hasRetryAfterHeader() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);
        MockHttpServletResponse res = doFilter(request("/api/auth/login", "1.2.3.4"));
        boolean hasRetryHeader =
            res.getHeader("X-Rate-Limit-Retry-After-Seconds") != null ||
            res.getHeader("X-Rate-Limit-Retry-After-Minutes") != null;
        assertTrue(hasRetryHeader, "Blocked response must include a Retry-After header");
    }

    @Test
    void authEndpoint_blockedRequest_doesNotCallChain() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);
        reset(chain);  // reset to track only the 6th call
        doFilter(request("/api/auth/login", "1.2.3.4"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void authEndpoint_allowedRequest_callsChain() throws Exception {
        doFilter(request("/api/auth/login", "1.2.3.4"));
        verify(chain, times(1)).doFilter(any(), any());
    }

    // -----------------------------------------------------------------------
    // Search endpoint — 30 per minute
    // -----------------------------------------------------------------------

    @Test
    void searchEndpoint_allowsFirst30Requests() throws Exception {
        exhaust("/api/items/search", "1.2.3.4", 30);
        verify(chain, times(30)).doFilter(any(), any());
    }

    @Test
    void searchEndpoint_blocks31stRequest() throws Exception {
        exhaust("/api/items/search", "1.2.3.4", 30);
        MockHttpServletResponse res = doFilter(request("/api/items/search", "1.2.3.4"));
        assertEquals(429, res.getStatus());
    }

    @Test
    void searchEndpoint_blockedResponse_bodyContainsErrorMessage() throws Exception {
        exhaust("/api/items/search", "1.2.3.4", 30);
        MockHttpServletResponse res = doFilter(request("/api/items/search", "1.2.3.4"));
        assertTrue(res.getContentAsString().contains("Too many requests"));
    }

    // -----------------------------------------------------------------------
    // General endpoint — 100 per minute
    // -----------------------------------------------------------------------

    @Test
    void generalEndpoint_allowsFirst100Requests() throws Exception {
        exhaust("/api/bids", "1.2.3.4", 100);
        verify(chain, times(100)).doFilter(any(), any());
    }

    @Test
    void generalEndpoint_blocks101stRequest() throws Exception {
        exhaust("/api/bids", "1.2.3.4", 100);
        MockHttpServletResponse res = doFilter(request("/api/bids", "1.2.3.4"));
        assertEquals(429, res.getStatus());
    }

    // -----------------------------------------------------------------------
    // Bucket isolation
    // -----------------------------------------------------------------------

    @Test
    void differentIPs_haveIndependentBuckets() throws Exception {
        // exhaust limit for IP A
        exhaust("/api/auth/login", "1.1.1.1", 5);

        // IP B should still be within its own limit
        MockHttpServletResponse res = doFilter(request("/api/auth/login", "2.2.2.2"));
        assertEquals(200, res.getStatus(), "Different IPs must have separate buckets");
    }

    @Test
    void sameIP_authAndSearchHaveIndependentBuckets() throws Exception {
        // exhaust auth limit
        exhaust("/api/auth/login", "1.2.3.4", 5);

        // search endpoint for same IP should still work (different bucket)
        MockHttpServletResponse res = doFilter(request("/api/items/search", "1.2.3.4"));
        assertEquals(200, res.getStatus(), "Auth and search endpoints must use separate buckets");
    }

    @Test
    void sameIP_authAndGeneralHaveIndependentBuckets() throws Exception {
        exhaust("/api/auth/login", "1.2.3.4", 5);

        MockHttpServletResponse res = doFilter(request("/api/payments", "1.2.3.4"));
        assertEquals(200, res.getStatus(), "Auth and general endpoints must use separate buckets");
    }

    @Test
    void sameIP_searchAndGeneralHaveIndependentBuckets() throws Exception {
        exhaust("/api/items/search", "1.2.3.4", 30);

        MockHttpServletResponse res = doFilter(request("/api/bids", "1.2.3.4"));
        assertEquals(200, res.getStatus(), "Search and general endpoints must use separate buckets");
    }
}
