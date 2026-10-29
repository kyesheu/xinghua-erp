package cn.xinghuaerp.order.service.impl;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同一店铺同类平台拉取的单实例重入守卫。
 *
 * <p>为什么需要：拉单是「翻页 + 逐条落库」的长事务式流程，单次最多可跑 90 秒、500 页。
 * 页面上重复点一次就会对同一时间窗再打一遍平台接口，白烧调用额度，也让检查点推进与日志变得难以解释。
 * 这里做的是<b>快速失败</b>，不是排队：后来的请求直接拿到「正在拉取中」，前端重新点即可。</p>
 *
 * <p>边界：只覆盖单实例进程内。多实例部署时两个节点仍可能同时对同一店铺拉取，
 * 那时需要把守卫换成库表租约或 Redis 锁；当前实现不能表述为分布式互斥。</p>
 */
@Component
public class PlatformPullGuard {

    /** 失败的拉取会立刻释放，不会长期占用；进程重启即清空 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    private static String key(Long shopId, String type) {
        return shopId + ":" + type;
    }

    /**
     * @return true 表示抢到本次拉取，调用方必须在 finally 里 end；false 表示已有同类拉取在进行
     */
    public boolean tryBegin(Long shopId, String type) {
        if (shopId == null || type == null) return true;
        return running.add(key(shopId, type));
    }

    public void end(Long shopId, String type) {
        if (shopId == null || type == null) return;
        running.remove(key(shopId, type));
    }

    /** 仅用于观测与测试：当前进行中的拉取数量 */
    public int runningCount() {
        return running.size();
    }
}
