package cn.xinghuaerp.ai.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class AiClientAdmissionTest {
    @Test void paidEntryPointsCannotBypassAdmissionButExplicitRulesStayFree() throws Exception {
        var network=new AtomicInteger();
        var client=new PythonAgentClient("http://127.0.0.1:8000","x".repeat(32),(uri,headers,body)->{network.incrementAndGet();return new PythonAgentClient.Response(200,"{}");});
        var admission=mock(AiRequestAdmission.class);
        when(admission.begin(anyString(),anyString(),anyString())).thenThrow(new AiAdmissionRejected("limited"));
        var field=PythonAgentClient.class.getDeclaredField("admission");field.setAccessible(true);field.set(client,admission);
        assertNotNull(client.managementAnalysis(Map.of("useModel",false)));assertEquals(1,network.get());
        assertThrows(AiAdmissionRejected.class,()->client.managementAnalysis(Map.of("useModel",true)));
        assertThrows(AiAdmissionRejected.class,()->client.managementAnalysis(Map.of("useModel","true")));
        assertThrows(AiAdmissionRejected.class,()->client.managementPlan(Map.of("question","hi")));
        assertThrows(AiAdmissionRejected.class,()->client.productDraft(Map.of()));
        assertThrows(AiAdmissionRejected.class,()->client.brief(Map.of(),"signed"));
        assertThrows(AiAdmissionRejected.class,()->client.modelPing(Map.of(),"signed"));
        assertEquals(1,network.get());
    }
    @Test void realUsageIsSettledRatherThanAssumingAZeroCostResponse() throws Exception {
        var admission=mock(AiRequestAdmission.class);
        var permit=new AiRequestAdmission.Permit("id",7,1,"2026-11-13",220000);
        when(admission.begin(anyString(),anyString(),anyString())).thenReturn(permit);
        var client=new PythonAgentClient("http://127.0.0.1:8000","x".repeat(32),(uri,headers,body)->new PythonAgentClient.Response(200,"{}","{\"known\":true,\"inputTokens\":10,\"outputTokens\":2}"));
        var field=PythonAgentClient.class.getDeclaredField("admission");field.setAccessible(true);field.set(client,admission);
        client.brief(Map.of(),"signed");verify(admission).finish(permit,10L,2L,true,"COMPLETED");
    }
    @Test void streamLinesAreBounded() {
        assertThrows(java.io.IOException.class,()->PythonAgentClient.readLineBounded(new java.io.StringReader("a".repeat(65537))));
    }
}
