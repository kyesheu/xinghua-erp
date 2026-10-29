package cn.xinghuaerp.open.jd;

import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.jd.model.AfterSale;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * 回归：京东 asc.query.list 必须带 applyTimeBegin/applyTimeEnd。
 * 旧实现直接对入参 format(null)，时间窗为 null 时抛 NPE，
 * 导致「售后详情同步」入口（调用方传 null,null）必然失败。
 */
class JdAfterSaleWindowTest {

    private static final String ONE_PAGE = "{\"jingdong_asc_query_list_responce\":{\"code\":\"0\",\"pageResult\":{"
            + "\"totalCount\":1,\"data\":[{\"serviceId\":9001,\"applyId\":7001,\"orderId\":5001}]}}}";

    @Test
    void nullTimeWindowFallsBackInsteadOfThrowing() {
        AtomicReference<LocalDateTime> capturedStart = new AtomicReference<>();
        AtomicReference<LocalDateTime> capturedEnd = new AtomicReference<>();
        try (var api = mockStatic(JdAfterSaleApiHelper.class)) {
            api.when(() -> JdAfterSaleApiHelper.pullAfterSale(eq(1L), any(), any(), anyInt(), anyInt(),
                            eq("k"), eq("s"), eq("t")))
                    .thenAnswer(inv -> {
                        capturedStart.set(inv.getArgument(1));
                        capturedEnd.set(inv.getArgument(2));
                        return ONE_PAGE;
                    });
            api.when(() -> JdAfterSaleApiHelper.pullAfterSaleList(eq(1L), any(), any(), eq("k"), eq("s"), eq("t")))
                    .thenCallRealMethod();
            api.when(() -> JdAfterSaleApiHelper.pullAfterSaleList(eq(1L), any(), any(), anyInt(), anyInt(),
                            eq("k"), eq("s"), eq("t")))
                    .thenCallRealMethod();

            ApiResultVo<AfterSale> result =
                    JdAfterSaleApiHelper.pullAfterSaleList(1L, null, null, "k", "s", "t");

            assertEquals(0, result.getCode(), "时间窗为 null 时不能抛 NPE，应回落到默认窗口");
            assertNotNull(result.getList());
            assertEquals(1, result.getList().size());
        }
        assertNotNull(capturedStart.get(), "必须给平台一个真实开始时间");
        assertNotNull(capturedEnd.get(), "必须给平台一个真实结束时间");
        assertTrue(capturedStart.get().isBefore(capturedEnd.get()), "applyTimeBegin 必须早于 applyTimeEnd");
    }

    @Test
    void emptyMiddlePageIsReportedAsIncomplete() {
        String firstPage = "{\"jingdong_asc_query_list_responce\":{\"code\":\"0\",\"pageResult\":{"
                + "\"totalCount\":25,\"data\":[{\"serviceId\":1},{\"serviceId\":2}]}}}";
        String emptyPage = "{\"jingdong_asc_query_list_responce\":{\"code\":\"0\",\"pageResult\":{"
                + "\"totalCount\":25,\"data\":[]}}}";
        try (var api = mockStatic(JdAfterSaleApiHelper.class)) {
            api.when(() -> JdAfterSaleApiHelper.pullAfterSale(eq(1L), any(), any(), eq(1), anyInt(),
                            eq("k"), eq("s"), eq("t")))
                    .thenReturn(firstPage);
            api.when(() -> JdAfterSaleApiHelper.pullAfterSale(eq(1L), any(), any(), eq(2), anyInt(),
                            eq("k"), eq("s"), eq("t")))
                    .thenReturn(emptyPage);
            api.when(() -> JdAfterSaleApiHelper.pullAfterSaleList(eq(1L), any(), any(), eq("k"), eq("s"), eq("t")))
                    .thenCallRealMethod();
            api.when(() -> JdAfterSaleApiHelper.pullAfterSaleList(eq(1L), any(), any(), anyInt(), anyInt(),
                            eq("k"), eq("s"), eq("t")))
                    .thenCallRealMethod();

            ApiResultVo<AfterSale> result =
                    JdAfterSaleApiHelper.pullAfterSaleList(1L, null, null, "k", "s", "t");

            assertEquals(1505, result.getCode(), "totalCount 说还有 25 条却出现空页，必须报未完成，不能当成功");
        }
    }
}
