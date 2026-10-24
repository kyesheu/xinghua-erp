package cn.xinghuaerp.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.config.RedisListenerConfigurer;
import org.springframework.data.redis.serializer.RedisMessageConverters;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

/**
 * {@link RedisListenerConfig} 的回归测试。
 *
 * <p>锁定"启动不得再因 JSON-B 实现缺失而崩溃"这一修复：消息转换器链必须完全绕开
 * Spring Data Redis 的默认装配（默认装配里含 {@code JsonbMessageConverter}），
 * 同时字符串通道与 Jackson JSON 通道必须仍然可用。</p>
 *
 * <p>本测试不启动 Spring 容器、不连接 Redis，只直接驱动配置器产物，
 * 因此可以放在普通 surefire 用例里快速执行。</p>
 *
 * @see RedisListenerConfig
 */
class RedisListenerConfigTest
{
    /** JSON-B 消息转换器的全限定类名。用字符串比较，避免在测试里强行加载该类。 */
    private static final String JSONB_MESSAGE_CONVERTER = "org.springframework.messaging.converter.JsonbMessageConverter";

    /**
     * 驱动配置器，返回它最终装配出的转换器列表。
     *
     * <p>这里 {@code builder.build()} 本身就是核心断言：若默认装配没有被关闭，
     * {@code getDefaultConverters()} 会去 {@code new JsonbMessageConverter()}，
     * 在当前类路径（接口在、实现不在）下直接抛 {@code JsonbException}，
     * 于是本方法抛出，测试即失败。</p>
     */
    private List<MessageConverter> configuredConverters()
    {
        RedisListenerConfigurer configurer = new RedisListenerConfig().redisListenerMessageConverterConfigurer();
        assertNotNull(configurer, "RedisListenerConfig 必须提供 RedisListenerConfigurer");

        RedisMessageConverters.Builder builder = RedisMessageConverters.builder();
        configurer.configureMessageConverters(builder);

        MessageConverter converter = builder.build().getConverter();
        CompositeMessageConverter composite = assertInstanceOf(CompositeMessageConverter.class, converter,
                "Redis 消息转换器链应为 CompositeMessageConverter");
        return composite.getConverters();
    }

    private boolean containsConverterOfType(List<MessageConverter> converters, Class<?> expected)
    {
        return converters.stream().anyMatch(expected::isInstance);
    }

    @Test
    void neverRegistersJsonbMessageConverter()
    {
        List<MessageConverter> converters = configuredConverters();
        assertTrue(converters.stream().noneMatch(c -> JSONB_MESSAGE_CONVERTER.equals(c.getClass().getName())),
                "转换器链中不允许出现 JsonbMessageConverter，否则启动会因 JSON-B 实现缺失而崩溃，实际为: " + converters);
    }

    @Test
    void keepsStringAndJacksonJsonConverters()
    {
        List<MessageConverter> converters = configuredConverters();
        assertTrue(containsConverterOfType(converters, StringMessageConverter.class),
                "字符串转换器不能被一起关掉，实际为: " + converters);
        assertTrue(containsConverterOfType(converters, JacksonJsonMessageConverter.class),
                "Jackson JSON 转换器必须显式补回，实际为: " + converters);
    }

    @Test
    void stillConvertsStringPayloads()
    {
        List<MessageConverter> converters = configuredConverters();
        CompositeMessageConverter composite = new CompositeMessageConverter(converters);

        Message<String> message = MessageBuilder.withPayload("hello-redis").build();
        Object converted = composite.fromMessage(message, String.class);

        assertEquals("hello-redis", converted, "字符串通道必须可用，否则 Redis 监听无法读取文本消息");
    }

    @Test
    void stillConvertsJsonPayloadsToMap() throws Exception
    {
        List<MessageConverter> converters = configuredConverters();
        CompositeMessageConverter composite = new CompositeMessageConverter(converters);

        Message<byte[]> message = MessageBuilder
                .withPayload("{\"sku_id\":\"A-1\",\"quantity\":2}".getBytes(StandardCharsets.UTF_8))
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .build();
        Object converted = composite.fromMessage(message, Map.class);

        Map<?, ?> payload = assertInstanceOf(Map.class, converted, "JSON 通道必须可用");
        assertEquals("A-1", payload.get("sku_id"));
        assertEquals(2, payload.get("quantity"));
    }
}
