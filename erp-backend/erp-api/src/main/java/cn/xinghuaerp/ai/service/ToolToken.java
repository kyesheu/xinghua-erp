package cn.xinghuaerp.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * AI 工具调用令牌。
 *
 * <p>工具的执行仍留在 Java：Python 侧负责决定查什么，但数据必须回到 Java 才拿得到。
 * 为了让「谁发起的对话」不依赖 Python 自报身份，Java 在调用 AI 服务时签发一个短时令牌，
 * Python 回调工具网关时原样带回，Java 校验签名后才知道该查哪个账号能看到的数据。
 * 令牌只含用户标识与过期时间，不含任何业务数据；有效期 5 分钟，一次对话一次签发。</p>
 */
@Component
public class ToolToken {

    private static final long TTL_MILLIS = 5 * 60 * 1000L;
    /** 允许 60 秒时钟偏差，避免两端时间轻微不一致时正常请求被拒。 */
    private static final long CLOCK_SKEW_MILLIS = 60 * 1000L;

    private final String secret;

    public ToolToken(@Value("${token.secret}") String secret) {
        this.secret = secret;
    }

    public String issue(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("工具令牌需要有效用户");
        long expiresAt = System.currentTimeMillis() + TTL_MILLIS;
        String payload = userId + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    /** 校验令牌并返回发起人用户标识；任何不合法的令牌都抛异常，不返回 0 或 null 让调用方猜。 */
    public long verify(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("缺少工具令牌");
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("工具令牌格式不正确");
        long userId;
        long expiresAt;
        try {
            userId = Long.parseLong(parts[0]);
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("工具令牌格式不正确");
        }
        if (userId <= 0) throw new IllegalArgumentException("工具令牌格式不正确");
        if (expiresAt + CLOCK_SKEW_MILLIS < System.currentTimeMillis()) throw new IllegalArgumentException("工具令牌已过期");
        byte[] expected = sign(userId + "." + expiresAt).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("工具令牌签名不匹配");
        return userId;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法签发工具令牌", e);
        }
    }
}
