package cn.xinghuaerp.open.wei;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.wei.model.AfterSaleOrder;
import cn.xinghuaerp.open.wei.vo.RefundDetailVo;
import tools.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class WeiRefundApiHelper {


    public  static ApiResultVo<AfterSaleOrder> pullRefundList(LocalDateTime startTime, LocalDateTime endTime, String accessToken) {
        // 确保 startTime 和 endTime 的顺序
        if (startTime.isAfter(endTime)) {
            return ApiResultVo.error(ApiResultVoEnum.ParamsError,"结束时间必须大于开始时间");
        }
        // 计算两个时间点之间的小时数差
        long hoursBetween = ChronoUnit.HOURS.between(startTime, endTime);

        // 判断时间差是否超过24小时
        if (hoursBetween > 24) {
            return ApiResultVo.error(ApiResultVoEnum.ParamsError,"开始时间与结束时间不能超过24小时");
        }
        List<AfterSaleOrder> lists = new ArrayList<>();

        String listResult = pullRefundList(1,accessToken,startTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()/1000,endTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()/1000,"");

        if(!StringUtils.isNotBlank(listResult)){
            return ApiResultVo.error(ApiResultVoEnum.ApiException);
        }
        JsonNode jsonObject = JsonUtils.readTree(listResult);
        if(JsonUtils.integer(jsonObject, "errcode") == 0) {
            JsonNode orderIds = JsonUtils.node(jsonObject, "after_sale_order_id_list");
            if (orderIds != null && orderIds.size() > 0) {
                orderIds.forEach(orderId -> {
                    String detailResult = pullRefundDetail(accessToken, JsonUtils.text(orderId));
                    if (StringUtils.isNotBlank(detailResult)) {
                        JsonNode detailJsonObject = JsonUtils.readTree(detailResult);
                        if (JsonUtils.integer(detailJsonObject, "errcode") == 0) {
                            AfterSaleOrder order = JsonUtils.readValue(JsonUtils.text(detailJsonObject, "after_sale_order"), AfterSaleOrder.class);
                            lists.add(order);
                        }
                    }
                });
                // 判断是否还有下一页
                Boolean isHas_more = JsonUtils.bool(jsonObject, "has_more");
                String next_key = JsonUtils.text(jsonObject, "next_key");
                while (isHas_more) {
                    String listResultPage = pullRefundList(1,accessToken, startTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli() / 1000,
                            endTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli() / 1000, next_key);

                }
            }
            return ApiResultVo.success(lists.size(), lists);
        }else if(JsonUtils.integer(jsonObject, "errcode") == 42001){
            return ApiResultVo.error(ApiResultVoEnum.TokenFail,JsonUtils.text(jsonObject, "errmsg"));
        } else {
            return ApiResultVo.error(ApiResultVoEnum.ApiException,JsonUtils.text(jsonObject, "errmsg"));
        }

    }
    public  static ApiResultVo<AfterSaleOrder> pullRefundDetail(Long refundId, String accessToken) {
       String result = pullRefundDetail(accessToken,refundId.toString());
        if(!StringUtils.isNotBlank(result)){
            return ApiResultVo.error(ApiResultVoEnum.ApiException);
        }

        RefundDetailVo refundDetailVo = JsonUtils.readValue(result, RefundDetailVo.class);

        if(refundDetailVo.getErrcode() == 0) {
            // 拉取到了数据 拉取详情
            return ApiResultVo.success(refundDetailVo.getAfter_sale_order());
        }else {
            return ApiResultVo.error(ApiResultVoEnum.ApiException,refundDetailVo.getErrmsg());
        }

    }

    /**
     *
     * @param orderBy 1、create_time 2、update_time
     * @param accessToken
     * @param startTime
     * @param endTime
     * @param nextKey
     * @return
     */
    protected static String pullRefundList(Integer orderBy,String accessToken ,Long startTime,Long endTime,String nextKey) {
        Map<String,Object> params = new HashMap<>();
        if(orderBy==1) {
            params.put("begin_create_time", startTime);
            params.put("end_create_time", endTime);
        }else if(orderBy==2){
            params.put("begin_update_time", startTime);
            params.put("end_update_time", endTime);
        }
        if(StringUtils.isNotBlank(nextKey)){
            params.put("next_key",nextKey);
        }

        try {
            // 创建 ObjectMapper 实例
            ObjectMapper objectMapper = JsonUtils.mapper();
            String url =ServerUrl.serverApiUrl + ServerUrl.refundListApiUrl + "?access_token=" + accessToken;
//            HttpResponse<String> stringHttpResponse = HttpUtils.doPostJson(ServerUrl.serverApiUrl + ServerUrl.refundListApiUrl + "?access_token=" + accessToken, objectMapper.writeValueAsString(params));
//            return stringHttpResponse.body();

            return OkHttpClientHelper.post(url,objectMapper.writeValueAsString(params));

        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    protected static String pullRefundDetail(String accessToken, String afterSaleOrderId) {
        Map<String,Object> params = new HashMap<>();
        params.put("after_sale_order_id",afterSaleOrderId);
        try {
            // 创建 ObjectMapper 实例
            ObjectMapper objectMapper = JsonUtils.mapper();
            String url = ServerUrl.serverApiUrl + ServerUrl.refundDetailApiUrl + "?access_token=" + accessToken;
//            HttpResponse<String> stringHttpResponse = HttpUtils.doPostJson(ServerUrl.serverApiUrl + ServerUrl.refundDetailApiUrl + "?access_token=" + accessToken, objectMapper.writeValueAsString(params));
//            return stringHttpResponse.body();

            return OkHttpClientHelper.post(url,objectMapper.writeValueAsString(params));

        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
