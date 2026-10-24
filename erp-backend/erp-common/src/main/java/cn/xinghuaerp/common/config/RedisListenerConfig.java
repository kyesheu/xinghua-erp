package cn.xinghuaerp.common.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.config.RedisListenerConfigurer;
import org.springframework.data.redis.serializer.RedisMessageConverters;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

/**
 * Redis 监听容器（{@code @RedisListener}）消息转换器配置。
 *
 * <p><b>为什么需要这个类（不要删除）：</b></p>
 * <ol>
 *   <li>Spring Boot 4.1 的 {@code DataRedisAnnotationDrivenConfiguration} 在
 *       {@code org.springframework.messaging.Message} 与 {@code EnableRedisListeners} 位于类路径上时，
 *       <b>无条件</b>为应用开启 {@code @EnableRedisListeners}，且<b>没有任何属性开关</b>可以关闭。</li>
 *   <li>{@code RedisListenerAnnotationBeanPostProcessor#afterSingletonsInstantiated()} 会无条件执行
 *       {@code RedisListenerEndpointRegistrar#apply(configurers)}；
 *       其中末尾一定会调用 {@code RedisMessageConverters.builder().build()}。</li>
 *   <li>{@code DefaultRedisMessageConverters.DefaultBuilder#getDefaultConverters()} 用
 *       {@code ClassUtils.isPresent("jakarta.json.bind.Jsonb", ...)} 探测 JSON-B 是否可用；
 *       探测为 true 时即 {@code new JsonbMessageConverter()}，而该构造器会用
 *       {@code ServiceLoader} 查找 {@code jakarta.json.bind.spi.JsonbProvider} 实现，
 *       并在实现缺失时抛出 {@code jakarta.json.bind.JsonbException: JSON Binding provider ... not found}。</li>
 *   <li>本项目 {@code erp-channel} 以 compile 作用域引入了
 *       {@code jakarta.platform:jakarta.jakartaee-api}（API 伞包），把 {@code jakarta.json.bind.Jsonb}
 *       接口带进了运行时类路径，但<b>并没有</b> JSON-B 的实现（yasson 已随 AI 调用下沉 Python 一并移除）。
 *       于是"接口在、实现不在"→ 探测为 true → 构造 {@code JsonbMessageConverter} 失败 → 应用启动直接崩溃。</li>
 * </ol>
 *
 * <p><b>修复思路：</b>本项目没有任何 {@code @RedisListener} / {@code @EnableRedisListeners} /
 * 自定义 {@code RedisMessageListenerContainer}，业务侧只使用 {@code RedisTemplate}（见 {@link RedisConfig}）。
 * 也就是说这些"默认消息转换器"对本项目<b>毫无实际用途</b>，纯粹是自动装配的副作用。
 * 因此这里注册一个 {@link RedisListenerConfigurer}，用 {@code registerDefaults(false)}
 * 关闭整组默认转换器的装配（同时也就关闭了 JSON-B 探测），然后只显式补上项目真正需要的两个转换器：
 * UTF-8 字符串转换器与 Jackson 3 的 JSON 转换器（AI 重构后 {@code tools.jackson.*} 是全项目唯一 JSON 库）。</p>
 *
 * <p><b>注意：</b>本类<b>不影响</b> {@code RedisTemplate} 的序列化方式，
 * 缓存/分布式锁等业务读写仍然沿用 {@link RedisConfig} 中的
 * {@link JacksonJsonRedisSerializer} 配置。两者的作用域完全不同：
 * 前者只服务于"Redis 消息监听"，后者服务于"Redis 数据读写"。</p>
 *
 * @author xinghua
 * @see RedisConfig
 */
@Configuration
public class RedisListenerConfig
{
    /**
     * 定制 Redis 监听容器的消息转换器链，绕开 Spring Data Redis 的默认装配。
     *
     * <p>{@code registerDefaults(false)} 必须在添加自定义转换器之前调用：
     * {@code DefaultBuilder#build()} 只有在 {@code registerDefaults == true} 时才会调用
     * {@code getDefaultConverters()}，从而彻底跳过 JSON-B 类路径探测。</p>
     *
     * @return 仅关闭默认装配、保留字符串与 Jackson JSON 转换器的配置器
     */
    @Bean
    public RedisListenerConfigurer redisListenerMessageConverterConfigurer()
    {
        return new RedisListenerConfigurer()
        {
            @Override
            public void configureMessageConverters(RedisMessageConverters.Builder builder)
            {
                // 关键：关闭默认转换器装配（其中包含 JSON-B 探测，正是启动崩溃的根因）
                builder.registerDefaults(false);

                // 显式补回字符串转换器，编码固定为 UTF-8，与项目其余 IO 保持一致
                builder.withStringConverter(new StringMessageConverter(StandardCharsets.UTF_8));

                // 显式补回 JSON 转换器：Spring Framework 7 基于 Jackson 3（tools.jackson.*）的实现
                builder.addCustomConverter(redisListenerJsonMessageConverter());
            }
        };
    }

    /**
     * 构造 Redis 监听容器使用的 JSON 消息转换器。
     *
     * <p>使用 Spring Framework 7 提供的 Jackson 3 版本 {@link JacksonJsonMessageConverter}，
     * 与本项目的 Jackson 3 技术栈（{@code tools.jackson.databind.json.JsonMapper}）保持统一，
     * 避免再次引入任何 JSON-B 相关依赖。</p>
     *
     * @return 基于 Jackson 3 默认 {@code JsonMapper} 的 JSON 消息转换器
     */
    private MessageConverter redisListenerJsonMessageConverter()
    {
        return new JacksonJsonMessageConverter();
    }
}
