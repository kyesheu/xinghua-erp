package cn.xinghuaerp.open.dou;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.dou.model.WaybillCode;
import cn.xinghuaerp.open.dou.model.WaybillPrintData;
import cn.xinghuaerp.open.dou.request.WaybillApplyRequest;
import cn.xinghuaerp.open.dou.request.WaybillCodeRequest;
import cn.xinghuaerp.open.dou.utils.ExpressClient;
import cn.xinghuaerp.open.dou.utils.HttpUtil;
import cn.xinghuaerp.open.dou.utils.MD5Utils;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class DouWaybillApiHelper {


    /**
     * 取号
     * @param appKey
     * @param appSercet
     * @param accessToken
     * @param request
     * @return
     */
    public static ApiResultVo<WaybillCode> getWaybillCode(String appKey, String appSercet, String accessToken, WaybillCodeRequest request)  {
        String method = "logistics.newCreateOrder";

        String paramJson = JsonUtils.write(request);

        long timestamp = System.currentTimeMillis()/1000;
//        String timestamp = "2024-06-18 11:20:13";
        String signStr = "app_key"+appKey+"method"+method+"param_json"+paramJson+"timestamp"+timestamp+"v"+"2";
        signStr = appSercet+signStr+appSercet;
        String sign = MD5Utils.MD5Encode(signStr);

        String sendUrl = "https://openapi-fxg.jinritemai.com/logistics/newCreateOrder";

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
                    if(JsonUtils.node(JsonUtils.node(resultObj, "data"), "err_infos") != null &&JsonUtils.node(JsonUtils.node(resultObj, "data"), "err_infos").size()>0){
                        return ApiResultVo.error(ApiResultVoEnum.ApiException.getIndex(), JsonUtils.text(JsonUtils.node(JsonUtils.node(JsonUtils.node(resultObj, "data"), "err_infos"), 0), "err_msg"));
                    }
                    var list = JsonUtils.list(JsonUtils.node(resultObj, "data"), "ebill_infos", WaybillCode.class);

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

    public static ApiResultVo<WaybillPrintData> pullWaybillPrintData(String appKey, String appSercet, String accessToken, WaybillApplyRequest request)  {
        String method = "logistics.waybillApply";

        String paramJson = JsonUtils.write(request);

        long timestamp = System.currentTimeMillis()/1000;
//        String timestamp = "2024-06-18 11:20:13";
        String signStr = "app_key"+appKey+"method"+method+"param_json"+paramJson+"timestamp"+timestamp+"v"+"2";
        signStr = appSercet+signStr+appSercet;
        String sign = MD5Utils.MD5Encode(signStr);

        String sendUrl = "https://openapi-fxg.jinritemai.com/logistics/waybillApply";

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
                    if(JsonUtils.node(JsonUtils.node(resultObj, "data"), "err_infos") != null &&JsonUtils.node(JsonUtils.node(resultObj, "data"), "err_infos").size()>0){
                        return ApiResultVo.error(ApiResultVoEnum.ApiException.getIndex(), JsonUtils.text(JsonUtils.node(JsonUtils.node(JsonUtils.node(resultObj, "data"), "err_infos"), 0), "err_msg"));
                    }
                    var list = JsonUtils.list(JsonUtils.node(resultObj, "data"), "waybill_infos", WaybillPrintData.class);

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
