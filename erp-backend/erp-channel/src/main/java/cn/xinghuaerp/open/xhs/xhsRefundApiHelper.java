package cn.xinghuaerp.open.xhs;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.MD5Utils;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.xhs.response.AfterSaleInfoResponse;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class xhsRefundApiHelper {

    // ========== 原始 String 方法 ==========

    public static String pullRefundList(String appId, String appSecret, String accessToken, LocalDateTime startTime, LocalDateTime endTime, Integer pageNo, Integer pageSize) throws IOException {
        String serverUrl = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";
        String method = "afterSale.listAfterSaleInfos";
        Long time = System.currentTimeMillis() / 1000;
        Map<String, Object> params = new HashMap<>();
        params.put("appId", appId);
        params.put("version", "2.0");
        params.put("timestamp", time.toString());
        params.put("accessToken", accessToken);
        params.put("method", method);

        String signString = method + "?appId=" + appId + "&timestamp=" + time + "&version=" + "2.0" + appSecret;
        String sign = MD5Utils.MD5Encode(signString);
        params.put("sign", sign);

        params.put("startTime", startTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli());
        params.put("endTime", endTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli());
        params.put("timeType", 1);
        params.put("pageNo", pageNo + "");
        params.put("pageSize", pageSize + "");

        String jsonString = JsonUtils.write(params);
        return OkHttpClientHelper.post(serverUrl, jsonString);
    }

    public static String getRefundDetail(String appId, String appSecret, String accessToken, String returnsId) throws IOException {
        String serverUrl = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";
        String method = "afterSale.getAfterSaleInfo";
        Long time = System.currentTimeMillis() / 1000;
        Map<String, String> params = new HashMap<>();
        params.put("appId", appId);
        params.put("version", "2.0");
        params.put("timestamp", time.toString());
        params.put("accessToken", accessToken);
        params.put("method", method);

        String signString = method + "?appId=" + appId + "&timestamp=" + time + "&version=" + "2.0" + appSecret;
        String sign = MD5Utils.MD5Encode(signString);
        params.put("sign", sign);

        params.put("returnsId", returnsId);

        String jsonString = JsonUtils.write(params);
        return OkHttpClientHelper.post(serverUrl, jsonString);
    }

    // ========== ApiResultVo 包装方法 ==========

    /**
     * 拉取售后列表，返回 ApiResultVo<List<AfterSaleInfoResponse>>
     */
    public static ApiResultVo<AfterSaleInfoResponse> pullRefundListVo(String appId, String appSecret, String accessToken,
                                                                       LocalDateTime startTime, LocalDateTime endTime) throws IOException {
        List<AfterSaleInfoResponse> allRefunds = new ArrayList<>();
        int pageNo = 1;
        int pageSize = 50;
        boolean hasMore = true;

        while (hasMore) {
            String result = pullRefundList(appId, appSecret, accessToken, startTime, endTime, pageNo, pageSize);
            if (result == null) return ApiResultVo.error(500, "接口返回空");

            JsonNode json = JsonUtils.readTree(result);
            String code = JsonUtils.text(json, "code");
            if (!"0".equals(code)) {
                return ApiResultVo.error(code != null ? Integer.parseInt(code) : 500, JsonUtils.text(json, "msg"));
            }

            JsonNode data = JsonUtils.node(json, "data");
            if (data != null) {
                JsonNode refundList = JsonUtils.node(data, "afterSaleList");
                if (refundList == null) refundList = JsonUtils.node(data, "list");
                if (refundList != null) {
                    List<AfterSaleInfoResponse> pageRefunds = JsonUtils.readList(refundList.toString(), AfterSaleInfoResponse.class);
                    allRefunds.addAll(pageRefunds);
                }
                int currentPage = JsonUtils.intOrZero(data, "pageNo", 1);
                int totalPage = JsonUtils.intOrZero(data, "totalPage", 1);
                hasMore = currentPage < totalPage;
                pageNo = currentPage + 1;
            } else {
                hasMore = false;
            }
        }
        return ApiResultVo.success(allRefunds.size(), allRefunds);
    }

    /**
     * 拉取售后详情，返回 ApiResultVo<AfterSaleInfoResponse>
     */
    public static ApiResultVo<AfterSaleInfoResponse> getRefundDetailVo(String appId, String appSecret, String accessToken, String returnsId) throws IOException {
        String result = getRefundDetail(appId, appSecret, accessToken, returnsId);
        if (result == null) return ApiResultVo.error(500, "接口返回空");

        JsonNode json = JsonUtils.readTree(result);
        String code = JsonUtils.text(json, "code");
        if (!"0".equals(code)) {
            return ApiResultVo.error(code != null ? Integer.parseInt(code) : 500, JsonUtils.text(json, "msg"));
        }

        JsonNode data = JsonUtils.node(json, "data");
        if (data == null) return ApiResultVo.error(404, "售后不存在");

        AfterSaleInfoResponse afterSale = JsonUtils.convert(data, "afterSaleInfo", AfterSaleInfoResponse.class);
        if (afterSale == null) afterSale = JsonUtils.convert(data, AfterSaleInfoResponse.class);
        return ApiResultVo.success(afterSale);
    }
}