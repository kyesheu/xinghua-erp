package cn.xinghuaerp.open.dou;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.dou.model.WaybillAccount;
import cn.xinghuaerp.open.dou.utils.ExpressClient;
import cn.xinghuaerp.open.dou.utils.HttpUtil;
import cn.xinghuaerp.open.dou.utils.MD5Utils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DouWaybillAccountApiHelper {



    public static ApiResultVo<WaybillAccount> listWaybillAccount(String appKey, String appSercet, String accessToken)  {
        String method = "logistics.listShopNetsite";
        String paramJson = "";
        try {
            LinkedHashMap<String, String> jsonMap = new LinkedHashMap<>();
            jsonMap.put("logistics_code", "");
            ObjectMapper objectMapper = JsonUtils.mapper();
            paramJson = objectMapper.writeValueAsString(jsonMap);
        }catch (JacksonException ex){
            ex.printStackTrace();
           return ApiResultVo.error(ApiResultVoEnum.JsonProcessingException.getIndex(), "Json参数转换错误");
        }

        long timestamp = System.currentTimeMillis()/1000;

        String signStr = "app_key"+appKey+"method"+method+"param_json"+paramJson+"timestamp"+timestamp+"v"+"2";
        signStr = appSercet+signStr+appSercet;
        String sign = MD5Utils.MD5Encode(signStr);

        String sendUrl = "https://openapi-fxg.jinritemai.com/logistics/listShopNetsite";

        Map<String, String> params = new HashMap<>();
        params.put("method", method);
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("timestamp", timestamp+"");
        params.put("param_json", paramJson);
        params.put("v", "2");
        params.put("sign", sign);

        try {
            String surl = sendUrl+ "?"+ HttpUtil.map2Url(params);
            HttpResponse<String> response = ExpressClient.doPost(surl, paramJson);
            if (response.statusCode() == 200) {
                String resultStr = response.body();

                JsonNode resultObj = JsonUtils.readTree(resultStr);
                if (JsonUtils.integer(resultObj, "code") == 10000) {
                    List<WaybillAccount> list = JsonUtils.list(JsonUtils.node(resultObj, "data"), "netsites", WaybillAccount.class);


                    return ApiResultVo.success(list.size(),list);
                }else{
                    return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "请求API错误："+JsonUtils.text(resultObj, "sub_msg"));
                }
            }else{
                return  ApiResultVo.error(ApiResultVoEnum.NETWORK_ERROR.getIndex(), "网络请求错误");
            }
        } catch (Exception e) {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "系统异常：" + e.getMessage());
        }
    }
}
