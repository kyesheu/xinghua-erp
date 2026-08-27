package cn.xinghuaerp.open.dou;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.dou.model.Token;
import cn.xinghuaerp.open.dou.utils.ExpressClient;
import cn.xinghuaerp.open.dou.utils.HttpUtil;
import cn.xinghuaerp.open.dou.utils.MD5Utils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DouTokenApiHelper {
    public static ApiResultVo<Token> getToken(String appKey,String appSercet,Long shopId) {
        String method = "token.create";

        String paramJson = "";
        try {
            LinkedHashMap<String, String> jsonMap = new LinkedHashMap<>();
            jsonMap.put("code", "");
            jsonMap.put("grant_type", "authorization_self");
            jsonMap.put("shop_id", shopId + "");

            ObjectMapper objectMapper = JsonUtils.mapper();
            paramJson = objectMapper.writeValueAsString(jsonMap);
        } catch (JacksonException ex) {
            ex.printStackTrace();
            return ApiResultVo.error(ApiResultVoEnum.JsonProcessingException.getIndex(), "Json参数转换错误");
        }
        long timestamp = System.currentTimeMillis() / 1000 ;
        String signStr = "app_key" + appKey + "method" + method + "param_json" + paramJson + "timestamp" + timestamp + "v" + "2";
        signStr = appSercet + signStr + appSercet;
        String sign = MD5Utils.MD5Encode(signStr);

        String sendUrl = "https://openapi-fxg.jinritemai.com/token/create";

        Map<String, String> params = new HashMap<>();
        params.put("method", method);
        params.put("app_key", appKey);
        params.put("timestamp", timestamp + "");
        params.put("param_json", paramJson);
        params.put("v", "2");
        params.put("sign", sign);

        try {
            String surl = sendUrl + "?" + HttpUtil.map2Url(params);
            HttpResponse<String> response = ExpressClient.doPost(surl, paramJson);
            if (response.statusCode() == 200) {
                String resultStr = response.body();

                JsonNode resultObj = JsonUtils.readTree(resultStr);
                if (JsonUtils.integer(resultObj, "code") == 10000) {
                    Token data = JsonUtils.readValue(JsonUtils.text(resultObj, "data"), Token.class);
                    return ApiResultVo.success(data);
                } else {
                    return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "请求API错误：" + JsonUtils.text(resultObj, "sub_msg"));
                }
            } else {
                return ApiResultVo.error(ApiResultVoEnum.NETWORK_ERROR.getIndex(), "网络请求错误");
            }
        } catch (Exception e) {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "系统异常：" + e.getMessage());
        }
    }
    public static ApiResultVo<Token> refreshToken(String appKey, String appSecret,String token, String refreshToken) {
        String method = "token.refresh";

        String paramJson = "";
        try {
            LinkedHashMap<String, String> jsonMap = new LinkedHashMap<>();
            jsonMap.put("grant_type", "refresh_token");
            jsonMap.put("refresh_token", refreshToken);

            ObjectMapper objectMapper = JsonUtils.mapper();
            paramJson = objectMapper.writeValueAsString(jsonMap);
        } catch (JacksonException ex) {
            ex.printStackTrace();
            return ApiResultVo.error(ApiResultVoEnum.JsonProcessingException.getIndex(), "Json参数转换错误");
        }
        long timestamp = System.currentTimeMillis() / 1000 ;
        String signStr = "app_key" + appKey + "method" + method + "param_json" + paramJson + "timestamp" + timestamp + "v" + "2";
        signStr = appSecret + signStr + appSecret;
        String sign = cn.xinghuaerp.open.common.MD5Utils.MD5Encode(signStr);

        String sendUrl = "https://openapi-fxg.jinritemai.com/token/refresh";

        Map<String, String> params = new HashMap<>();
        params.put("method", method);
        params.put("access_token", token);
        params.put("app_key", appKey);
        params.put("timestamp", timestamp + "");
        params.put("param_json", paramJson);
        params.put("v", "2");
        params.put("sign", sign);

        try {
            String surl = sendUrl + "?" + HttpUtil.map2Url(params);
            HttpResponse<String> response = cn.xinghuaerp.open.common.ExpressClient.doPost(surl, paramJson);
            if (response.statusCode() == 200) {
                String resultStr = response.body();

                JsonNode resultObj = JsonUtils.readTree(resultStr);
                if (JsonUtils.integer(resultObj, "code") == 10000) {
                    Token data = JsonUtils.readValue(JsonUtils.text(resultObj, "data"), Token.class);
                    return ApiResultVo.success(data);
                } else {
                    return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "请求API错误：" + JsonUtils.text(resultObj, "sub_msg"));
                }
            } else {
                return ApiResultVo.error(ApiResultVoEnum.NETWORK_ERROR.getIndex(), "网络请求错误");
            }
        } catch (Exception e) {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "系统异常：" + e.getMessage());
        }
    }
}
