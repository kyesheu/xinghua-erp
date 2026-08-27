package cn.xinghuaerp.open.xhs;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.MD5Utils;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.xhs.response.GoodsItemInfo;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class xhsGoodsApiHelper {

    // ========== 原始 String 方法 ==========

    public static String pullGoodsItemList(String appId, String appSecret, String accessToken, Integer pageNo, Integer pageSize) throws IOException {
        String serverUrl = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";
        String method = "product.searchItemList";
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

        params.put("pageNo", pageNo + "");
        params.put("pageSize", pageSize + "");
        params.put("searchParam", "");

        String jsonString = JsonUtils.write(params);
        return OkHttpClientHelper.post(serverUrl, jsonString);
    }

    public static String pullGoodsItemInfo(String appId, String appSecret, String accessToken, String itemId) throws IOException {
        String serverUrl = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";
        String method = "product.getItemInfo";
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

        params.put("itemId", itemId);

        String jsonString = JsonUtils.write(params);
        return OkHttpClientHelper.post(serverUrl, jsonString);
    }

    public static String getDetailSkuList(String appId, String appSecret, String accessToken, Integer pageNo, Integer pageSize) throws IOException {
        String serverUrl = "https://ark.xiaohongshu.com/ark/open_api/v3/common_controller";
        String method = "product.getDetailSkuList";
        Long time = System.currentTimeMillis() / 1000;
        Map<String, String> params = new HashMap<>();
        params.put("appId", appId);
        params.put("version", "2.0");
        params.put("timestamp", time.toString());
        params.put("accessToken", accessToken);
        params.put("method", method);

        String signString = method + "?appId=" + appId + "&timestamp=" + time.toString() + "&version=" + "2.0" + appSecret;
        String sign = MD5Utils.MD5Encode(signString);
        params.put("sign", sign);

        params.put("pageNo", pageNo + "");
        params.put("pageSize", pageSize + "");

        String jsonString = JsonUtils.write(params);
        return OkHttpClientHelper.post(serverUrl, jsonString);
    }

    // ========== ApiResultVo 包装方法 ==========

    /**
     * 拉取商品列表（含详情），返回 ApiResultVo<List<GoodsItemInfo>>
     */
    public static ApiResultVo<GoodsItemInfo> pullGoodsItemListVo(String appId, String appSecret, String accessToken) throws IOException {
        List<GoodsItemInfo> allItems = new ArrayList<>();
        int pageNo = 1;
        int pageSize = 30;
        boolean hasMore = true;

        while (hasMore) {
            String result = pullGoodsItemList(appId, appSecret, accessToken, pageNo, pageSize);
            if (result == null) return ApiResultVo.error(500, "接口返回空");

            JsonNode json = JsonUtils.readTree(result);
            String code = JsonUtils.text(json, "code");
            if (!"0".equals(code)) {
                return ApiResultVo.error(code != null ? Integer.parseInt(code) : 500, JsonUtils.text(json, "msg"));
            }

            JsonNode data = JsonUtils.node(json, "data");
            if (data != null) {
                JsonNode itemList = JsonUtils.node(data, "itemList");
                if (itemList != null) {
                    for (int i = 0; i < itemList.size(); i++) {
                        String itemId = JsonUtils.text(JsonUtils.node(itemList, i), "id");
                        if (itemId != null) {
                            String detail = pullGoodsItemInfo(appId, appSecret, accessToken, itemId);
                            if (detail != null) {
                                JsonNode detailJson = JsonUtils.readTree(detail);
                                if ("0".equals(JsonUtils.text(detailJson, "code"))) {
                                    JsonNode detailData = JsonUtils.node(detailJson, "data");
                                    if (detailData != null) {
                                        GoodsItemInfo item = JsonUtils.convert(detailData, "item", GoodsItemInfo.class);
                                        if (item != null) allItems.add(item);
                                    }
                                }
                            }
                        }
                    }
                }
                int currentPage = JsonUtils.intOrZero(data, "pageNo", 1);
                int totalPage = JsonUtils.intOrZero(data, "totalPage", 1);
                hasMore = currentPage < totalPage;
                pageNo = currentPage + 1;
            } else {
                hasMore = false;
            }
        }
        return ApiResultVo.success(allItems.size(), allItems);
    }
}