package cn.xinghuaerp.open.pdd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.pdd.model.Order;
import cn.xinghuaerp.open.pdd.model.OrderListResultVo;
import cn.xinghuaerp.open.common.PDDSignGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PddOrderApiHelper {
    private static Logger log = LoggerFactory.getLogger(PddOrderApiHelper.class);

    /**
     * 拉取商品列表
     * @param clientId
     * @param clientSecret
     * @param accessToken
     * @param pageIndex
     * @param pageSize
     * @return
     */

    public static ApiResultVo<OrderListResultVo> pullOrderList(String clientId, String clientSecret, String accessToken, Integer startTime, Integer endTime, Integer pageIndex, Integer pageSize) throws IOException {

        String result = pullOrderListResult(clientId, clientSecret, accessToken,startTime,endTime,pageIndex,pageSize);
        if (!StringUtils.hasText(result)) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口请求错误");

        JsonNode jsonObject = JsonUtils.readTree(result);
        if(JsonUtils.text(jsonObject, "error_response") !=null)
            return ApiResultVo.error(ApiResultVoEnum.ApiException, JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "error_msg"));

        if(JsonUtils.node(jsonObject, "order_list_get_response") == null) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口返回数据错误");

        Boolean hasNext = JsonUtils.bool(JsonUtils.node(jsonObject, "order_list_get_response"), "has_next");
//        JsonNode a = jsonObject.getJSONObject("order_list_get_response").getJSONArray("order_list");

        List<Order> orderList = JsonUtils.readList(JsonUtils.text(JsonUtils.node(jsonObject, "order_list_get_response"), "order_list"), Order.class);
//        while (hasNext){
//            pageIndex++;
//            try {
//                String result1 = pullOrderListResult(clientId, clientSecret, accessToken, startTime, endTime, pageIndex, pageSize);
//                JsonNode jsonObject1 = JsonNode.parseObject(result1);
//                hasNext = jsonObject1.getJSONObject("order_list_get_response").getBoolean("has_next");
//                orderList.addAll(JsonNode.parseArray(jsonObject1.getJSONObject("order_list_get_response").getString("order_list"), Order.class));
//            }catch (Exception e){
//                e.printStackTrace();
//                hasNext = false;
//            }
//        }
        return ApiResultVo.success(new OrderListResultVo(hasNext,orderList));
    }

    public static ApiResultVo<Order> pullOrderDetail(String clientId, String clientSecret, String accessToken, String orderSn) throws IOException {

        String result = pullOrderDetailStr(clientId, clientSecret, accessToken,orderSn);
        if (!StringUtils.hasText(result)) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口请求错误");

        JsonNode jsonObject = JsonUtils.readTree(result);
        if(JsonUtils.text(jsonObject, "error_response") !=null)
            return ApiResultVo.error(ApiResultVoEnum.ApiException, JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "error_msg"));

        if(JsonUtils.node(jsonObject, "order_info_get_response") == null) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口返回数据错误");

        Order order = JsonUtils.convert(JsonUtils.node(jsonObject, "order_info_get_response"), "order_info", Order.class);

        return ApiResultVo.success(order);
    }

    protected static String pullOrderListResult(String clientId, String clientSecret, String accessToken,Integer startTime, Integer endTime,Integer pageIndex,Integer pageSize) throws IOException {
        String url = "https://gw-api.pinduoduo.com/api/router"; // API的URL

        Map<String, String> params = new HashMap<>();
        params.put("type", "pdd.order.list.get");
        params.put("client_id", clientId);
        params.put("access_token",accessToken);
        params.put("timestamp", System.currentTimeMillis()/1000 +"");

        // 主体字段
        params.put("page", pageIndex+"");
        params.put("page_size", pageSize+"");
        params.put("order_status","5");//发货状态，1：待发货，2：已发货待签收，3：已签收 5：全部
        params.put("refund_status","5");//售后状态 1：无售后或售后关闭，2：售后处理中，3：退款中，4： 退款成功 5：全部
        params.put("use_has_next","true");
        params.put("start_confirm_at",startTime+"");
        params.put("end_confirm_at",endTime+"");

        try {
            String sign = PDDSignGenerator.generateSign(params, clientSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            return "";//签名错误
        }

        // 调用接口
//        PddOrderApiService remoting = RemoteUtil.Remoting(url, PddOrderApiService.class);
        String jsonString = JsonUtils.write(params);
//        String result = remoting.getOrderList(jsonString);
//        return result;
//        HttpResponse<String> stringHttpResponse = HttpUtils.doPost(url, jsonString);
//        return stringHttpResponse.body();
        return OkHttpClientHelper.post(url,jsonString);
    }

    protected static String pullOrderDetailStr(String clientId, String clientSecret, String accessToken,String orderSn) throws IOException {
        String url = "https://gw-api.pinduoduo.com/api/router"; // API的URL

        Map<String, String> params = new HashMap<>();
        params.put("type", "pdd.order.information.get");
        params.put("client_id", clientId);
        params.put("access_token",accessToken);
        params.put("timestamp", System.currentTimeMillis()/1000 +"");

        // 主体字段
        params.put("order_sn", orderSn);

        try {
            String sign = PDDSignGenerator.generateSign(params, clientSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            return "";//签名错误
        }

        // 调用接口
//        PddOrderApiService remoting = RemoteUtil.Remoting(url, PddOrderApiService.class);
        String jsonString = JsonUtils.write(params);
//        String result = remoting.getOrderList(jsonString);
//        return result;
//        HttpResponse<String> stringHttpResponse = HttpUtils.doPost(url, jsonString);
//        return stringHttpResponse.body();
        return OkHttpClientHelper.post(url,jsonString);
    }
}