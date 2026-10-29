package cn.xinghuaerp.open.dou;

import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.dou.model.after.AfterSale;
import cn.xinghuaerp.open.dou.utils.ExpressClient;
import cn.xinghuaerp.open.dou.utils.HttpUtil;
import cn.xinghuaerp.open.dou.utils.MD5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DouRefundApiHelper {
    private static Logger log = LoggerFactory.getLogger(DouRefundApiHelper.class);

    /**
     * 拉取售后（按申请时间）
     * @param
     * @return
     */
    public static ApiResultVo<AfterSale> pullAfterSaleList(Long startTime, Long endTime, Integer pageIndex, Integer pageSize, String appKey, String appSecret, String accessToken) {
        String result = pullAfterSaleListStr(startTime, endTime, pageIndex, pageSize, 1, appKey, appSecret, accessToken);
        if (!StringUtils.hasLength(result)) return ApiResultVo.error("请求接口异常");

        JsonNode resultObj = JsonUtils.readTree(result);
        if (JsonUtils.integer(resultObj, "code") == 10000) {
            Integer total = JsonUtils.integer(JsonUtils.node(resultObj, "data"), "total");

            if(total==null || total<0)return ApiResultVo.error("售后总数无效，无法确认分页完整性");
            if(total==0)return ApiResultVo.success(0,java.util.List.of());

            List<AfterSale> list = JsonUtils.readList(JsonUtils.text(JsonUtils.node(resultObj, "data"), "items"), AfterSale.class);
            return ApiResultVo.success(total, list);
        } else {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "请求API错误：" + JsonUtils.text(resultObj, "sub_msg"));
        }
    }
    /**
     * 更新售后（按更新时间）
     * @param
     * @return
     */
    public static ApiResultVo<AfterSale> updateAfterSaleList(Long startTime, Long endTime, Integer pageIndex, Integer pageSize, String appKey, String appSecret, String accessToken) {
        String result = pullAfterSaleListStr(startTime, endTime, pageIndex, pageSize, 1, appKey, appSecret, accessToken);
        if (!StringUtils.hasLength(result)) return ApiResultVo.error("请求接口异常");

        JsonNode resultObj = JsonUtils.readTree(result);
        if (JsonUtils.integer(resultObj, "code") == 10000) {
            Integer total = JsonUtils.integer(JsonUtils.node(resultObj, "data"), "total");

            if(total==null || total<0)return ApiResultVo.error("售后总数无效，无法确认分页完整性");
            if(total==0)return ApiResultVo.success(0,java.util.List.of());

            List<AfterSale> list = JsonUtils.readList(JsonUtils.text(JsonUtils.node(resultObj, "data"), "items"), AfterSale.class);
            return ApiResultVo.success(total, list);
        } else {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "请求API错误：" + JsonUtils.text(resultObj, "sub_msg"));
        }
    }

    /**
     *
     * @param startTime
     * @param endTime
     * @param pageIndex
     * @param pageSize
     * @param orderBy 1、apply_time 2、update_time
     * @param appKey
     * @param appSecret
     * @param accessToken
     * @return
     */
    public static String pullAfterSaleListStr(Long startTime, Long endTime, Integer pageIndex, Integer pageSize, Integer orderBy,String appKey, String appSecret, String accessToken){
        String method = "afterSale.List";
//        String method = "afterSale.orderList";
//        String refundMethod="refund.orderList";

        LinkedHashMap<String, Object> jsonMap =new LinkedHashMap<>();

        if (orderBy == 1) {
            jsonMap.put("order_by",new String[]{"apply_time desc"});
        }else if(orderBy==2) {
            jsonMap.put("order_by",new String[]{"update_time desc"});
        }

        jsonMap.put("page",pageIndex);
        jsonMap.put("size",pageSize);
        jsonMap.put("update_end_time",endTime);//截至时间
        jsonMap.put("update_start_time",startTime);//开始时间

        ObjectNode jsonObject = JsonUtils.object();
        jsonObject.setAll(JsonUtils.object(jsonMap));

        String paramJson =jsonObject.toString();

        long timestamp = System.currentTimeMillis() / 1000 ;

//        String signStr = "app_key"+appKey+"method"+method+"param_json"+paramJson+"timestamp"+timestamp+"v"+"1";
//        signStr = appSecret+signStr+appSecret;
//
//        String sign = MD5Utils.MD5Encode(signStr);
        String signStr = "app_key" + appKey + "method" + method + "param_json" + paramJson + "timestamp" + timestamp + "v" + "2";
        signStr = appSecret + signStr + appSecret;

        String sign = MD5Utils.MD5Encode(signStr);

//        String  sendUrl= "https://openapi.jinritemai.com";
        String sendUrl = "https://openapi-fxg.jinritemai.com/afterSale/List";

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("method", method);
        params.put("param_json",paramJson);
        params.put("timestamp", timestamp+"");
        params.put("v", "2");
        params.put("sign", sign);

        try {
            String surl = sendUrl + "?" + HttpUtil.map2Url(params);
            HttpResponse<String> response = ExpressClient.doPost(surl, paramJson);
            if (response.statusCode() == 200) {
                return response.body();
            }else{
                return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

//    //售后订单详情
//    public DcDouyinOrdersRefundDetail afterSaleOrderDetail(String refundOrderId){
//        Integer shopId=8;
//        var shop = shopService.getShop(shopId);
//        String appKey = shop.getAppkey();
//        String appSercet = shop.getAppSercet();
//        String method = "afterSale.orderDetail";
//        String paramJson ="{\"order_id\":\""+refundOrderId+"\"}";
//        String timestamp = DateUtil.dateToString(new Date(),"yyyy/MM/dd HH:mm:ss");
//
//        String signStr = "app_key"+appKey+"method"+method+"param_json"+paramJson+"timestamp"+timestamp+"v"+"1";
//        signStr = appSercet+signStr+appSercet;
//
//        String sign = MD5Utils.MD5Encode(signStr);
//
//        Map<String, String> params = new HashMap<>();
//        params.put("app_key", appKey);
//        params.put("method", method);
//        params.put("param_json",paramJson);
//        params.put("timestamp", timestamp);
//        params.put("v", "1");
//        params.put("sign", sign);
//        try {
//            String  sendUrl= "https://openapi.jinritemai.com";
//            HttpResponse<String> detailResponse = ExpressClient.doPost(sendUrl+"/afterSale/orderDetail", HttpUtil.map2Url(params));
//            JsonNode detailJson = JsonNode.parseObject(detailResponse.body());
//            //System.out.println(detailJson.toString());
//            if(detailJson.getString("message").equals("success")){
//                JsonNode datailObj = detailJson.getJSONObject("data").getJSONObject("data");
//                return JsonUtil.strToObject(JSON.toJSONString(datailObj),DcDouyinOrdersRefundDetail.class);
//            }
//        }catch (Exception ex){
//            return null;
//        }
//        return null;
//    }

}
