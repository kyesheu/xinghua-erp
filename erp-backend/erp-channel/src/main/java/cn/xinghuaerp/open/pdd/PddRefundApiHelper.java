package cn.xinghuaerp.open.pdd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.pdd.model.AfterSale;
import cn.xinghuaerp.open.common.PDDSignGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PddRefundApiHelper {
    private static Logger log = LoggerFactory.getLogger(PddRefundApiHelper.class);

    /**
     * 拉取商品列表
     * @param clientId
     * @param clientSecret
     * @param accessToken
     * @param pageIndex
     * @param pageSize
     * @return
     */

    public static ApiResultVo<AfterSale> pullRefundList(String clientId, String clientSecret, String accessToken, Integer startTime, Integer endTime, Integer pageIndex, Integer pageSize) throws IOException {

        String result = pullRefundListResult(clientId, clientSecret, accessToken,startTime,endTime,pageIndex,pageSize);
        if (!StringUtils.hasText(result)) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口请求错误");

        JsonNode jsonObject = JsonUtils.readTree(result);
        if(JsonUtils.text(jsonObject, "error_response") !=null)
            return ApiResultVo.error(ApiResultVoEnum.ApiException, JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "error_msg"));

        if(JsonUtils.node(jsonObject, "refund_increment_get_response") == null) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口返回数据错误");

        Integer totalCount = JsonUtils.integer(JsonUtils.node(jsonObject, "refund_increment_get_response"), "total_count");

        List<AfterSale> afterSaleList = JsonUtils.readList(JsonUtils.text(JsonUtils.node(jsonObject, "refund_increment_get_response"), "refund_list"), AfterSale.class);

        if(totalCount>pageSize){

        }

        return ApiResultVo.success(totalCount,afterSaleList);
    }



    protected static String pullRefundListResult(String clientId, String clientSecret, String accessToken,Integer startTime, Integer endTime,Integer pageIndex,Integer pageSize) throws IOException {
        String url = "https://gw-api.pinduoduo.com/api/router"; // API的URL

        Map<String, String> params = new HashMap<>();
        params.put("type", "pdd.refund.list.increment.get");
        params.put("client_id", clientId);
        params.put("access_token",accessToken);
        params.put("timestamp", System.currentTimeMillis()/1000 +"");

        // 主体字段
        params.put("after_sales_status", "1");
        params.put("after_sales_type", "1");
        params.put("end_updated_at", endTime.toString());
        params.put("page", pageIndex+"");
        params.put("page_size", pageSize+"");
        params.put("start_updated_at", startTime.toString());

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
    public static ApiResultVo<AfterSale>  pullRefundDetil(String clientId, String clientSecret, String accessToken, String afterSalesId,String orderSn) throws IOException {
        String result = pullRefundDetailResult(clientId, clientSecret, accessToken, afterSalesId, orderSn);
        if (!StringUtils.hasText(result)) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口请求错误");
        AfterSale afterSale = JsonUtils.readValue(result, AfterSale.class);
        return ApiResultVo.success(afterSale);
    }
    protected static String pullRefundDetailResult(String clientId, String clientSecret, String accessToken,String afterSalesId,String orderSn) throws IOException {
        String url = "https://gw-api.pinduoduo.com/api/router"; // API的URL

        Map<String, String> params = new HashMap<>();
        params.put("type", "pdd.refund.information.get");
        params.put("client_id", clientId);
        params.put("access_token",accessToken);
        params.put("timestamp", System.currentTimeMillis()/1000 +"");

        // 主体字段
        params.put("after_sales_id", afterSalesId);
        params.put("order_sn", orderSn);

        try {
            String sign = PDDSignGenerator.generateSign(params, clientSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            return "";//签名错误
        }
        // 调用接口
        String jsonString = JsonUtils.write(params);
        return OkHttpClientHelper.post(url,jsonString);
    }

}