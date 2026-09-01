package cn.xinghuaerp.erp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 可选的 NIO2 连接器，用于本机 NIO selector 无法建立 loopback 的环境。 */
@Configuration
@ConditionalOnProperty(name = "erp.server.nio2", havingValue = "true")
public class TomcatProtocolConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Protocol() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
