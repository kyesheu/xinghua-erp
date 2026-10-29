package cn.xinghuaerp.ai.service;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.util.UUID;

/** AI HTTP 边界：请求追踪、受限请求体；不记录提示词、查询参数或凭据。 */
@Component
@lombok.RequiredArgsConstructor
public class AiRequestTraceFilter extends OncePerRequestFilter {
    private final AiRequestAdmission admission;
    static final long MAX_BODY=524288;
    // 回调独立于公开请求，避免四个 SSE 都等工具时互相占满同一组名额。
    final java.util.concurrent.Semaphore publicSlots=new java.util.concurrent.Semaphore(4);
    final java.util.concurrent.Semaphore toolSlots=new java.util.concurrent.Semaphore(4);
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        return !publicAi(path) && !path.startsWith("/internal/ai-tools/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String supplied=request.getHeader("X-Request-ID");
        String id=supplied!=null && supplied.matches("[a-fA-F0-9-]{16,64}")?supplied:UUID.randomUUID().toString();
        String previous=org.slf4j.MDC.get("requestId");org.slf4j.MDC.put("requestId",id);
        response.setHeader("X-Request-ID",id);long started=System.nanoTime();
        var slots=request.getRequestURI().startsWith("/internal/ai-tools/")?toolSlots:publicSlots;
        boolean limited=requiresIngress(request.getRequestURI()) || request.getRequestURI().startsWith("/internal/ai-tools/");
        boolean acquired=!limited || slots.tryAcquire();
        var released=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable release=()->{if(limited && acquired && released.compareAndSet(false,true))slots.release();};
        try {
            if(!acquired){response.sendError(429,"AI在途请求过多，请稍后重试");return;}
            if(request.getContentLengthLong()>MAX_BODY){response.sendError(413,"AI 请求内容过大");return;}
            if (requiresIngress(request.getRequestURI())) {
                try {admission.checkIngress(cn.xinghuaerp.system.security.common.SecurityUtils.getUserId());}
                catch(AiAdmissionRejected rejected){response.setStatus(429);response.setContentType("application/json;charset=UTF-8");response.getWriter().write(cn.xinghuaerp.json.JsonUtils.write(java.util.Map.of("code",429,"msg",rejected.getMessage())));return;}
                catch(RuntimeException unavailable){response.sendError(503,"AI入口保护暂不可用");return;}
            }
            var wrapped=new HttpServletRequestWrapper(request) {
                private ServletInputStream bounded;
                @Override public ServletInputStream getInputStream() throws IOException {
                    if(bounded==null)bounded=new BoundedInput(super.getInputStream());return bounded;
                }
                @Override public BufferedReader getReader() throws IOException {return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
            };
            chain.doFilter(wrapped,response);
        } catch(TooLarge error) {if(!response.isCommitted())response.sendError(413,"AI 请求内容过大");}
        finally {
            if(acquired && limited && request.isAsyncStarted()) {
                try {request.getAsyncContext().addListener(new AsyncListener() {
                    public void onComplete(AsyncEvent event){release.run();}
                    public void onTimeout(AsyncEvent event){release.run();}
                    public void onError(AsyncEvent event){release.run();}
                    public void onStartAsync(AsyncEvent event){event.getAsyncContext().addListener(this);}
                });} catch(IllegalStateException completed){release.run();}
            } else release.run();
            logger.info("AI dispatch requestId="+id+" path="+request.getRequestURI()+" status="+response.getStatus()+" elapsedMs="+(System.nanoTime()-started)/1_000_000);
            if(previous==null)org.slf4j.MDC.remove("requestId");else org.slf4j.MDC.put("requestId",previous);
        }
    }
    static boolean publicAi(String path) {
        return path.startsWith("/api/ai/") || path.startsWith("/api/erp-api/ai-agent/") || path.startsWith("/api/erp-api/ai-data/");
    }
    static boolean requiresIngress(String path) {
        if (!publicAi(path) || path.startsWith("/api/ai/operations/")) return false;
        if (path.equals("/api/ai/config") || path.startsWith("/api/ai/config/")) return path.endsWith("/test");
        return true;
    }
    static class TooLarge extends IOException {}
    static class BoundedInput extends ServletInputStream {
        private final ServletInputStream delegate;private long count;
        BoundedInput(ServletInputStream delegate){this.delegate=delegate;}
        private void added(int size) throws IOException {if(size>0 && (count+=size)>MAX_BODY)throw new TooLarge();}
        @Override public int read() throws IOException {int value=delegate.read();if(value>=0)added(1);return value;}
        @Override public int read(byte[] bytes,int off,int len)throws IOException {if(count>MAX_BODY)throw new TooLarge();int size=delegate.read(bytes,off,(int)Math.min(len,MAX_BODY-count+1));added(size);return size;}
        @Override public boolean isFinished(){return delegate.isFinished();}
        @Override public boolean isReady(){return delegate.isReady();}
        @Override public void setReadListener(ReadListener listener){delegate.setReadListener(listener);}
    }
}
