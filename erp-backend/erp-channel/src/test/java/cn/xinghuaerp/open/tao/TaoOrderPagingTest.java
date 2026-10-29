package cn.xinghuaerp.open.tao;

import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.tao.response.TaoOrderListResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

/**
 * 回归：taobao.trades.sold.get 的分页响应节点固定是 trades_sold_get_response。
 * 旧实现把翻页结果读成 trades_sold_increment_get_response（那是 trades.sold.increment.get 的节点），
 * 该节点恒为 null，于是 has_next 读成 false，第二页起的数据被静默丢弃且不报错。
 */
class TaoOrderPagingTest {

    private static final String PAGE1 = "{\"trades_sold_get_response\":{\"has_next\":true,\"trades\":{\"trade\":["
            + "{\"tid\":\"T1\",\"status\":\"WAIT_BUYER_PAY\",\"orders\":{\"order\":[{\"oid\":\"O1\",\"num\":1}]}}"
            + "]}}}";

    private static final String PAGE2 = "{\"trades_sold_get_response\":{\"has_next\":false,\"trades\":{\"trade\":["
            + "{\"tid\":\"T2\",\"status\":\"TRADE_FINISHED\",\"orders\":{\"order\":[{\"oid\":\"O2\",\"num\":2}]}}"
            + "]}}}";

    @Test
    void secondPageIsReadFromTradesSoldGetResponse() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 2, 0, 0, 0);
        try (var api = mockStatic(TaoOrderApiHelper.class)) {
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, 1, "k", "s", "t")).thenReturn(PAGE1);
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, 2, "k", "s", "t")).thenReturn(PAGE2);
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, "k", "s", "t")).thenCallRealMethod();

            ApiResultVo<TaoOrderListResponse> result =
                    TaoOrderApiHelper.pullOrderList(start, end, "k", "s", "t");

            assertEquals(0, result.getCode());
            assertNotNull(result.getList());
            assertEquals(2, result.getList().size(), "两页订单都必须被读到，翻页不能丢");
            assertEquals(1, result.getList().get(0).getOrders().size());
            assertEquals(1, result.getList().get(1).getOrders().size());
        }
    }

    @Test
    void failedLaterPageIsReportedInsteadOfSilentlyTruncated() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 2, 0, 0, 0);
        try (var api = mockStatic(TaoOrderApiHelper.class)) {
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, 1, "k", "s", "t")).thenReturn(PAGE1);
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, 2, "k", "s", "t")).thenReturn("");
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, "k", "s", "t")).thenCallRealMethod();

            ApiResultVo<TaoOrderListResponse> result =
                    TaoOrderApiHelper.pullOrderList(start, end, "k", "s", "t");

            assertEquals(1505, result.getCode(), "第二页请求失败必须报错，不能返回页一的半份成功");
        }
    }

    @Test
    void wrongResponseNodeYieldsNoTradesButNoCrash() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 2, 0, 0, 0);
        try (var api = mockStatic(TaoOrderApiHelper.class)) {
            // 只有 increment 节点，没有 trades_sold_get_response
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, 1, "k", "s", "t"))
                    .thenReturn("{\"trades_sold_increment_get_response\":{\"has_next\":false,\"trades\":{\"trade\":[]}}}");
            api.when(() -> TaoOrderApiHelper.pullOrderList(start, end, "k", "s", "t")).thenCallRealMethod();

            ApiResultVo<TaoOrderListResponse> result =
                    TaoOrderApiHelper.pullOrderList(start, end, "k", "s", "t");

            assertEquals(1505, result.getCode(), "响应缺少约定节点时必须显式失败，不能当成 0 单");
        }
    }
}
