package cn.xinghuaerp.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;

@ComponentScan(basePackages={"cn.xinghuaerp"})
@SpringBootApplication
@EnableScheduling
public class ErpApi {
    public static void main( String[] args )
    {
        System.out.println( "Hello erp-api!" );
        ConfigurableApplicationContext context = SpringApplication.run(ErpApi.class, args);
        printAccessUrls(context.getEnvironment());
    }

    private static void printAccessUrls(Environment env) {
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        if (!contextPath.isEmpty() && !contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }
        String ip = "127.0.0.1";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }
        System.out.println("----------------------------------------------------------");
        System.out.println("Application erp-api is running!");
        System.out.println("Local access:  http://localhost:" + port + contextPath + "/");
        System.out.println("External access:  http://" + ip + ":" + port + contextPath + "/");
        System.out.println("----------------------------------------------------------");
    }
}
