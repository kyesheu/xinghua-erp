package cn.xinghuaerp.open.jd;

import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.jd.response.JdOrderListResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 京东订单分页壳的失败可见性。
 *
 * <p>被锁定的缺陷：内层 HTTP 方法把所有异常（含 `PlatformRateLimiter` 抛出的限流拒绝）
 * 都 {@code catch} 成空串返回，分页壳若「判空跳过」，上层 {@code ShopPullApiServiceImpl.pullJdOrder}
 * 就会拿到 code=0，记录「成功，新增0，更新0」，操作员毫无线索需要重拉。
 *
 * <p>为什么不直接调真实出口来测「第 2 页失败」：京东地址是写死的
 * {@code https://api.jd.com/routerjson}，第 1 页必然真的联网（离线环境里必然返回空串），
 * 于是永远命中「第 1 页失败」那条分支。所以这里替换 {@link JdOrderApiHelper.PageFetcher}
 * 这个取数入口，本文件<b>全程不联网</b>。
 */
class JdOrderPaginationFailureTest {

    /** code=0、共 25 条（3 页）、首页 orderInfoList 为空（避免触发手机号解密分支）。 */
    private static final String THREE_PAGES =
            "{\"jingdong_pop_order_enSearch_responce\":{\"code\":\"0\","
                    + "\"searchorderinfo_result\":{\"orderTotal\":25,\"orderInfoList\":[]}}}";

    /** code=0 但确实没有订单：必须仍然是成功，不能被「反静默失败」的改动误伤。 */
    private static final String NO_ORDERS =
            "{\"jingdong_pop_order_enSearch_responce\":{\"code\":\"0\","
                    + "\"searchorderinfo_result\":{\"orderTotal\":0}}}";

    @Test
    void firstPageNoResponseReturnsError() {
        Recording fetcher = new Recording(Map.of());

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertNotEquals(0, result.getCode(), "首页无响应必须返回错误码，不能是 code=0 的空成功；msg=" + result.getMsg());
        assertNotNull(result.getMsg());
        assertTrue(result.getMsg().contains("无响应"), "消息应说明接口无响应；实际：" + result.getMsg());
        assertTrue(result.getMsg().contains("限流"), "消息应提示可能被限流，便于操作员判断要重拉；实际：" + result.getMsg());
        assertEquals(List.of(1), fetcher.requested, "首页失败后不应继续翻页");
    }

    @Test
    void secondPageNoResponseAbortsWholePullWithPageNumber() {
        Recording fetcher = new Recording(Map.of(1, THREE_PAGES));

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertNotEquals(0, result.getCode(), "第 2 页无响应必须整批失败（upsert 重拉幂等），不能悄悄少页；msg=" + result.getMsg());
        assertTrue(result.getMsg().contains("第2/3页"), "消息必须带页码，否则无法判断缺了哪一页；实际：" + result.getMsg());
        assertTrue(result.getMsg().contains("不完整"), "消息应明确「拉取不完整」；实际：" + result.getMsg());
        assertEquals(List.of(1, 2), fetcher.requested, "第 2 页失败后不应继续翻第 3 页");
    }

    @Test
    void thirdPageNoResponseAbortsWholePullWithPageNumber() {
        Recording fetcher = new Recording(Map.of(1, THREE_PAGES, 2, THREE_PAGES));

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertNotEquals(0, result.getCode());
        assertTrue(result.getMsg().contains("第3/3页"), "实际：" + result.getMsg());
        assertEquals(List.of(1, 2, 3), fetcher.requested);
    }

    @Test
    void secondPageNonZeroCodeAbortsWholePullWithPageNumber() {
        Recording fetcher = new Recording(Map.of(1, THREE_PAGES, 2,
                "{\"jingdong_pop_order_enSearch_responce\":{\"code\":\"12\"}}"));

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertNotEquals(0, result.getCode(), "第 2 页返回非 0 code 也必须中止，不能静默跳过；msg=" + result.getMsg());
        assertTrue(result.getMsg().contains("第2/3页返回异常"), "实际：" + result.getMsg());
        assertTrue(result.getMsg().contains("12"), "消息应带上平台返回的 code；实际：" + result.getMsg());
    }

    @Test
    void firstPageNonZeroCodeReturnsError() {
        Recording fetcher = new Recording(Map.of(1,
                "{\"jingdong_pop_order_enSearch_responce\":{\"code\":\"7\"}}"));

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertNotEquals(0, result.getCode(), "首页 code 非 0 说明平台明确报错，不能吞成空成功；msg=" + result.getMsg());
        assertTrue(result.getMsg().contains("首页返回异常"), "实际：" + result.getMsg());
        assertTrue(result.getMsg().contains("7"), "实际：" + result.getMsg());
    }

    @Test
    void noOrdersIsStillSuccess() {
        Recording fetcher = new Recording(Map.of(1, NO_ORDERS));

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertEquals(0, result.getCode(), "确实没有订单时必须仍然是成功，否则每次拉空都会报错；msg=" + result.getMsg());
        assertEquals(0, result.getTotalRecords());
        assertTrue(result.getList() == null || result.getList().isEmpty());
    }

    @Test
    void errorResponseNodeIsSurfaced() {
        Recording fetcher = new Recording(Map.of(1, "{\"error_response\":{\"zh_desc\":\"签名无效\"}}"));

        ApiResultVo<JdOrderListResponse> result = pull(fetcher);

        assertNotEquals(0, result.getCode());
        assertTrue("签名无效".equals(result.getMsg()), "error_response 的 zh_desc 必须原样透出；实际：" + result.getMsg());
    }

    private static ApiResultVo<JdOrderListResponse> pull(JdOrderApiHelper.PageFetcher fetcher) {
        return JdOrderApiHelper.pullOrder(
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), "appKey", "appSecret", "accessToken", fetcher);
    }

    /** 按「页码 → 响应体」返回，未配置的页返回空串（模拟无响应），并记录实际请求过哪些页。 */
    private static final class Recording implements JdOrderApiHelper.PageFetcher {

        private final Map<Integer, String> bodies;
        private final List<Integer> requested = new ArrayList<>();

        Recording(Map<Integer, String> bodies) {
            this.bodies = new HashMap<>(bodies);
        }

        @Override
        public String fetch(LocalDateTime startTime, LocalDateTime endTime, Integer pageNo, Integer pageSize,
                            String appKey, String appSecret, String accessToken) {
            requested.add(pageNo);
            return bodies.getOrDefault(pageNo, "");
        }
    }
}
