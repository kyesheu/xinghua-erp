package cn.xinghuaerp.ai.service;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AiStreamTransportTest {
    static class Stub implements AutoCloseable {
        final ServerSocket server;final ExecutorService executor=Executors.newSingleThreadExecutor();
        final CountDownLatch sent=new CountDownLatch(1),release=new CountDownLatch(1);volatile Socket accepted;
        Stub(String payload,boolean hang)throws Exception {
            server=new ServerSocket(0,5,InetAddress.getByName("127.0.0.1"));
            executor.submit(()->{try(var socket=server.accept()) {
                accepted=socket;var input=socket.getInputStream();var headers=new StringBuilder();int c;
                while((c=input.read())!=-1){headers.append((char)c);if(headers.toString().endsWith("\r\n\r\n"))break;if(headers.length()>16000)throw new IOException();}
                int length=0;for(String line:headers.toString().split("\r\n"))if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                input.readNBytes(length);
                var output=socket.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson\r\nConnection: close\r\n\r\n"+payload).getBytes(StandardCharsets.UTF_8));output.flush();sent.countDown();
                if(hang)release.await();
            }catch(Exception ignored){sent.countDown();}});
        }
        PythonAgentClient client(){return new PythonAgentClient("http://127.0.0.1:"+server.getLocalPort(),"x".repeat(32),java.nio.file.Path.of("nonexistent-offline-config.json"),(uri,headers,body)->new PythonAgentClient.Response(200,"{}"));}
        public void close()throws Exception{release.countDown();if(accepted!=null)accepted.close();server.close();executor.shutdownNow();}
    }
    @Test void truncatedOutputIsNotReportedAsComplete()throws Exception {
        try(var stub=new Stub("{\"type\":\"message\",\"content\":\"partial\"}\n",false)) {
            assertThrows(IllegalStateException.class,()->stub.client().stream("/internal/chat",java.util.Map.of(),"",(type,content)->{}));
        }
    }
    @Test void interruptClosesBlockedHttpReadWithoutWaitingForReadTimeout()throws Exception {
        try(var stub=new Stub("{\"type\":\"message\",\"content\":\"partial\"}\n",true);var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var stopped=new CountDownLatch(1);var partial=new CountDownLatch(1);
            var future=pool.submit(()->{try{stub.client().stream("/internal/chat",java.util.Map.of(),"",(type,content)->{if(type.equals("message"))partial.countDown();});}finally{stopped.countDown();}});
            assertTrue(partial.await(3,TimeUnit.SECONDS));future.cancel(true);assertTrue(stopped.await(3,TimeUnit.SECONDS));
        }
    }
}
