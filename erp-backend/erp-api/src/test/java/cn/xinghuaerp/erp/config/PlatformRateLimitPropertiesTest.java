package cn.xinghuaerp.erp.config;

import cn.xinghuaerp.open.common.PlatformRateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 守护 {@code erp.platform.rate-limit.*} 的配置形状。
 *
 * <p>为什么需要这个测试：{@link PlatformRateLimitConfig} 的每个 {@code @Value} 都自带缺省值，
 * 所以 YAML 里 key 写错一个字母<b>不会报错</b>，只会静默退回默认值——运维改了配置却毫无效果。
 * 这里直接用 Spring Boot 的 YAML 加载器把 application.yml 摊平成属性键，断言每个平台键都存在，
 * 再用真实 yml 实例化配置类，确认 8 个 {@code @Value} 占位符都能解析并转成数字。
 */
class PlatformRateLimitPropertiesTest {

    private static final String BASE_PATH = "erp.platform.rate-limit.";

    @Test
    void applicationYmlDefinesEveryPlatformQps() {
        Map<String, Object> props = applicationYmlAsProperties();

        // 这些值与 PlatformRateLimiter 的默认值一致：改动这里等于改动默认行为，
        // 请同步更新 PlatformRateLimitConfig 的 @Value 缺省值与 PlatformRateLimiter 里的出处注释。
        assertEquals(1000, intValue(props, "wait-millis"));
        assertEquals(10, intValue(props, "pdd.qps"));
        assertEquals(10, intValue(props, "tao.qps"));
        assertEquals(5, intValue(props, "jd.qps"));
        assertEquals(20, intValue(props, "dou.qps"));
        assertEquals(10, intValue(props, "kwai.qps"));
        assertEquals(10, intValue(props, "xhs.qps"));
        assertEquals(10, intValue(props, "wei.qps"));
    }

    @Test
    void configClassBindsToApplicationYml() {
        Map<String, Object> props = applicationYmlAsProperties();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // 只装配这一个 Bean：不需要数据源/Redis，验证 @Value 占位符能从真实 yml 解析并转成 int
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("application-yml-flat", props));
            context.register(PlatformRateLimitConfig.class);
            assertDoesNotThrow(context::refresh);
        }
    }

    private static int intValue(Map<String, Object> props, String key) {
        Object value = props.get(BASE_PATH + key);
        assertEquals(true, value instanceof Number, BASE_PATH + key + " 必须是数字，实际是 " + value);
        return ((Number) value).intValue();
    }

    /** 用 Spring Boot 的 YAML 加载器把 application.yml 摊平成「dotted key → 值」，与运行期 Environment 的形态一致。 */
    private static Map<String, Object> applicationYmlAsProperties() {
        List<PropertySource<?>> sources;
        try {
            sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Object> flattened = new HashMap<>();
        for (PropertySource<?> source : sources) {
            if (source.getSource() instanceof Map<?, ?> map) {
                map.forEach((key, value) -> flattened.put(String.valueOf(key),
                        value instanceof OriginTrackedValue tracked ? tracked.getValue() : value));
            }
        }
        return flattened;
    }
}
