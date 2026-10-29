package cn.xinghuaerp.ai.service;

import jakarta.servlet.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class AiRequestTraceFilterTest {
    @Test void chunkedBodyIsBoundedWithoutTrustingContentLength() throws Exception {
        var bytes=new ByteArrayInputStream(new byte[(int)AiRequestTraceFilter.MAX_BODY+100]);
        var raw=new ServletInputStream(){public int read(){return bytes.read();}public int read(byte[] b,int o,int l){return bytes.read(b,o,l);}public boolean isFinished(){return bytes.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){}};
        var bounded=new AiRequestTraceFilter.BoundedInput(raw);
        assertThrows(AiRequestTraceFilter.TooLarge.class,bounded::readAllBytes);
        assertTrue(bytes.available()>0);
    }
    @Test void allPublicAiFamiliesAndModelTestsShareIngressProtection() {
        for(String path:java.util.List.of("/api/ai/chat/send","/api/ai/brief","/api/ai/config/7/test","/api/erp-api/ai-agent/inventory/analysis","/api/erp-api/ai-agent/management/analysis","/api/erp-api/ai-data/product/draft"))
            assertTrue(AiRequestTraceFilter.requiresIngress(path),path);
        for(String path:java.util.List.of("/api/ai/operations/enabled","/api/ai/config","/api/ai/config/7","/internal/ai-tools/query","/api/orders"))
            assertFalse(AiRequestTraceFilter.requiresIngress(path),path);
    }
    @Test void asyncSlotsRemainHeldUntilTerminalEventAndReleaseOnlyOnce() throws Exception {
        var filter=new AiRequestTraceFilter(org.mockito.Mockito.mock(AiRequestAdmission.class));
        var request=org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        var response=org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class);
        var async=org.mockito.Mockito.mock(AsyncContext.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/internal/ai-tools/query");
        org.mockito.Mockito.when(request.isAsyncStarted()).thenReturn(true);
        org.mockito.Mockito.when(request.getAsyncContext()).thenReturn(async);
        filter.doFilterInternal(request,response,(req,res)->{});
        assertEquals(3,filter.toolSlots.availablePermits());
        var capture=org.mockito.ArgumentCaptor.forClass(AsyncListener.class);
        org.mockito.Mockito.verify(async).addListener(capture.capture());
        capture.getValue().onComplete(new AsyncEvent(async));
        capture.getValue().onError(new AsyncEvent(async));
        assertEquals(4,filter.toolSlots.availablePermits());
    }
    @Test void saturatedPublicSlotsRejectWorkButDoNotBlockEmergencySwitch() throws Exception {
        var filter=new AiRequestTraceFilter(org.mockito.Mockito.mock(AiRequestAdmission.class));
        filter.publicSlots.acquire(4);
        var request=org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        var response=org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/erp-api/ai-agent/inventory/analysis");
        filter.doFilterInternal(request,response,(req,res)->fail("saturated request executed"));
        org.mockito.Mockito.verify(response).sendError(org.mockito.ArgumentMatchers.eq(429),org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/ai/operations/enabled");
        var called=new java.util.concurrent.atomic.AtomicBoolean();
        filter.doFilterInternal(request,response,(req,res)->called.set(true));
        assertTrue(called.get());
        assertEquals(0,filter.publicSlots.availablePermits());
    }
}
