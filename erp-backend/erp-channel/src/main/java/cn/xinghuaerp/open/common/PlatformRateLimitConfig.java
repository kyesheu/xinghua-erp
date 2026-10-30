package cn.xinghuaerp.open.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 {@code erp.platform.rate-limit.*} 配置灌进 {@link PlatformRateLimiter}。
 *
 * <p>平台调用方是静态工具类，拿不到容器里的 Bean，所以这里只在启动时做一次「静态注册」，
 * 用构造器注入 {@code @Value} 而不是 {@code @PostConstruct}：少一个注解依赖，且注册时机最早——
 * 任何定时任务/接口请求开始时桶已经就位。
 *
 * <p>所有项都带缺省值，配置缺失也能启动（缺省值与 {@link PlatformRateLimiter} 的默认值保持一致）。
 * 数值非平台官方承诺，按各自证书/权限包的实际配额调整。
 */
@Configuration
public class PlatformRateLimitConfig {

    /** 单次调用最多等待多久拿令牌，超过就快速失败。 */
    private static final String WAIT_MILLIS = "${erp.platform.rate-limit.wait-millis:1000}";

    /**
     * @param pdd       拼多多 QPS
     * @param tao       淘宝 QPS
     * @param jd        京东 QPS
     * @param dou       抖店 QPS
     * @param kwai      快手小店 QPS
     * @param xhs       小红书 QPS
     * @param wei       微信 QPS
     * @param waitMillis 单次调用允许的等待上限（毫秒）
     */
    public PlatformRateLimitConfig(
            @Value("${erp.platform.rate-limit.pdd.qps:10}") int pdd,
            @Value("${erp.platform.rate-limit.tao.qps:10}") int tao,
            @Value("${erp.platform.rate-limit.jd.qps:5}") int jd,
            @Value("${erp.platform.rate-limit.dou.qps:20}") int dou,
            @Value("${erp.platform.rate-limit.kwai.qps:10}") int kwai,
            @Value("${erp.platform.rate-limit.xhs.qps:10}") int xhs,
            @Value("${erp.platform.rate-limit.wei.qps:10}") int wei,
            @Value(WAIT_MILLIS) long waitMillis) {
        Map<String, Integer> qps = new LinkedHashMap<>();
        qps.put(PlatformRateLimiter.PDD, pdd);
        qps.put(PlatformRateLimiter.TAO, tao);
        qps.put(PlatformRateLimiter.JD, jd);
        qps.put(PlatformRateLimiter.DOU, dou);
        qps.put(PlatformRateLimiter.KWAI, kwai);
        qps.put(PlatformRateLimiter.XHS, xhs);
        qps.put(PlatformRateLimiter.WEI, wei);
        PlatformRateLimiter.configure(qps);
        PlatformRateLimiter.setWaitBudgetMillis(waitMillis);
    }
}
