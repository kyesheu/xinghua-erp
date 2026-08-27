package cn.xinghuaerp.open.kwai;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.SignMethodEnum;
import cn.xinghuaerp.open.common.SignUtils;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import java.net.URLEncoder;
import java.util.*;

/**
 * 快手售后API（修复版：使用 OkHttpClientHelper 发起真实HTTP调用）
 */
public class KwaiRefundApiHelper {

    /**
     * 拉取售后列表
     * @param appKey      快手开放平台appkey
     * @param appSecret   appSecret
     * @param signSecret  签名secret
     * @param token       access_token
     * @param beginTime   开始时间 毫秒时间戳
     * @param endTime     结束时间 毫秒时间戳
     * @return refund list as JsonNode
     */
    public static ApiResultVo<JsonNode> pullRefundList(String appKey, String appSecret, String signSecret, String token,
                                                         Long beginTime, Long endTime) {
        String serverUrl = "https://openapi.kwaixiaodian.com";
        List<JsonNode> allRefunds = new ArrayList<>();
        String pcursor = "";
        boolean hasMore = true;
        int page = 1;

        while (hasMore) {
            Map<String, String> params = new HashMap<>();
            params.put("appkey", appKey);
            params.put("access_token", token);
            params.put("method", "open.seller.order.refund.pcursor.list");

            Map<String, Object> p = new HashMap<>();
            p.put("beginTime", beginTime);
            p.put("endTime", endTime);
            p.put("type", "9");
            p.put("pageSize", 50);
            p.put("currentPage", page);
            p.put("pcursor", pcursor);
            String jsonString = JsonUtils.write(p);
            params.put("param", jsonString);

            try {
                String signParam = SignUtils.sign(params, signSecret, SignMethodEnum.MD5);
                params.put("sign", signParam);
            } catch (Exception e) {
                return ApiResultVo.error(500, "签名失败");
            }

            StringJoiner joiner = new StringJoiner("&");
            params.forEach((key, value) -> {
                try { joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)); } catch (Exception ignored) {}
            });
            String fullUrl = serverUrl + "/open/seller/order/refund/pcursor/list?" + joiner;

            try {
                String resultString = OkHttpClientHelper.get(fullUrl);
                JsonNode result = JsonUtils.readTree(resultString);
                if (result == null) return ApiResultVo.error(500, "接口返回空");

                if (JsonUtils.integer(result, "result") == 1) {
                    JsonNode data = JsonUtils.node(result, "data");
                    if (data != null) {
                        List<JsonNode> items = JsonUtils.readList(JsonUtils.write(data.get("items")), JsonNode.class);
                        if (items != null) allRefunds.addAll(items);
                        Integer totalPage = data.get("totalPage") != null ? Integer.parseInt(JsonUtils.text(data, "totalPage")) : 1;
                        hasMore = page < totalPage;
                        pcursor = data.get("pcursor") != null ? JsonUtils.text(data, "pcursor") : "";
                        page++;
                    } else {
                        hasMore = false;
                    }
                } else {
                    return ApiResultVo.error(JsonUtils.integer(result, "result"), JsonUtils.text(result, "error_msg"));
                }
            } catch (Exception e) {
                return ApiResultVo.error(500, "接口请求异常:" + e.getMessage());
            }
        }
        return ApiResultVo.success(allRefunds.size(), allRefunds);
    }
}