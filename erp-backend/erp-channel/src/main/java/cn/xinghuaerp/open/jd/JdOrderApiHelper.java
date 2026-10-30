package cn.xinghuaerp.open.jd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import tools.jackson.databind.ObjectMapper;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.jd.model.JiaMi;
import cn.xinghuaerp.open.jd.response.JdOrderDetailResponse;
import cn.xinghuaerp.open.jd.response.JdOrderListResponse;
import cn.xinghuaerp.open.common.DateUtil;
import cn.xinghuaerp.open.common.PlatformRateLimiter;
import cn.xinghuaerp.open.common.SignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JdOrderApiHelper {
    private static Logger log = LoggerFactory.getLogger(JdOrderApiHelper.class);

    private static final String ORDER_FIELDS = "venderId,orderId,orderType,payType,orderTotalPrice,orderSellerPrice,orderPayment,freightPrice,sellerDiscount," +
            "orderState,orderStateRemark,deliveryType,invoiceCode,orderRemark,orderStartTime,orderEndTime,venderRemark,balanceUsed,pin,returnOrder," +
            "paymentConfirmTime,waybill,logisticsId,modified,directParentOrderId,parentOrderId,orderSource,storeOrder,realPin,open_id,open_id_buyer," +
            "invoiceInfo,invoiceEasyInfo,itemInfoList,isShipmenttype,scDT,idSopShipmenttype,orderStartTime,consigneeInfo,orderMarkDesc,couponDetailList";

    private static final String ORDER_DETAIL_FIELDS = "venderId,orderId,orderType,payType,orderTotalPrice,orderSellerPrice,orderPayment,freightPrice,sellerDiscount," +
            "orderState,orderStateRemark,deliveryType,invoiceCode,orderRemark,orderStartTime,orderEndTime,venderRemark,balanceUsed,pin,returnOrder," +
            "paymentConfirmTime,waybill,logisticsId,modified,directParentOrderId,parentOrderId,orderSource,storeOrder,realPin,open_id,open_id_buyer," +
            "invoiceInfo,invoiceEasyInfo,itemInfoList,isShipmenttype,scDT,idSopShipmenttype,orderStartTime,consigneeInfo,orderMarkDesc,couponDetailList";

    /**
     * 分页壳的取数入口。真实实现就是下面的 {@link #pullOrder} 重载（发 HTTP）。
     *
     * <p>抽出这一层的唯一理由是测试：京东地址 {@code https://api.jd.com/routerjson} 是写死的，
     * 若不替换取数入口，「第 2 页失败」这条分支在无外网环境里永远走不到（第 1 页会先联网失败）。
     * 生产路径永远是方法引用 {@code JdOrderApiHelper::pullOrder}，没有任何分支能改变这一点。
     */
    @FunctionalInterface
    interface PageFetcher {
        String fetch(LocalDateTime startTime, LocalDateTime endTime, Integer pageNo, Integer pageSize,
                     String appKey, String appSecret, String accessToken);
    }

    protected static String pullOrder( LocalDateTime startTime, LocalDateTime endTime,Integer pageNo,Integer pageSize,String appKey,String appSecret,String accessToken){
        String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String url="https://api.jd.com/routerjson";

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("format", "json");
        params.put("method", "jingdong.pop.order.enSearch");
        params.put("v", "2.0");
        params.put("timestamp", DateUtil.getCurrentDateTime());
        try {
            Map<String, Object> pmap = new TreeMap();
            pmap.put("start_date", startTimeStr);
            pmap.put("end_date", endTimeStr);
            pmap.put("order_state", "ALL");
            pmap.put("optional_fields", ORDER_FIELDS);
            pmap.put("page", pageNo);
            pmap.put("page_size", pageSize);
            pmap.put("sortType", 1);
            pmap.put("dateType", 0);
            ObjectMapper mapper = JsonUtils.mapper();
            String p = mapper.writeValueAsString(pmap);
//        String param = JsonNode.toJSONString(pmap);
            params.put("360buy_param_json", p);
        }catch (Exception e){
            return "";
        }

        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        } catch (Exception e) {

        }
//        params.put("360buy_param_json", URLEncoder.encode(param));
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            try {
                joiner.add(key + "=" + URLEncoder.encode(value,"UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
        HttpClient client = HttpClient.newBuilder().build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .GET()
                .build();
        try {
            // 京东官方未公开 QPS，按 erp.platform.rate-limit.jd.qps 保守兜底。
            PlatformRateLimiter.acquire(url);
            HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = send.body();
            return result;
        }catch (Exception e){
            return "";
        }
    }
    /**
     * 更新订单（循环分页）
     *
     * @param
     * @param
     * @param accessToken
     * @return
     */
    public static ApiResultVo<JdOrderListResponse> pullOrder(LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String accessToken) {
        return pullOrder(startTime, endTime, appKey, appSecret, accessToken, JdOrderApiHelper::pullOrder);
    }

    /**
     * 分页壳的正体（{@code fetcher} 只用于测试替换，见 {@link PageFetcher}）。
     *
     * <p>为什么空响应必须报错：内层 {@code pullOrder} 的 {@code catch (Exception e) { return ""; }}
     * 会把限流拒绝、网络异常、签名失败统统吞成空串。如果这里再「判空跳过」，上层
     * {@code ShopPullApiServiceImpl.pullJdOrder} 收到的是 code=0，于是记录「成功，新增0」，
     * 操作员完全看不出这批订单需要重拉（退款/商品链路早就是「空响应=失败」，只有订单壳不一致）。
     *
     * <p>为什么中途某页失败要整批中止：订单落库是 upsert，中止后按同一时间窗重拉是幂等的；
     * 悄悄少几页则会让「成功」这个结论长期失真，比整体失败难发现得多。
     */
    static ApiResultVo<JdOrderListResponse> pullOrder(LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String accessToken, PageFetcher fetcher) {
        int page = 1;
        int pageSize = 10;
        // 拉取接口
        String result = fetcher.fetch(startTime, endTime, 1, pageSize, appKey, appSecret, accessToken);
        List<JdOrderListResponse> orderList = new ArrayList<>();
        if (!StringUtils.hasText(result)) {
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                    "京东订单接口无响应（可能被限流或网络异常），请稍后重试");
        }

        JsonNode jsonObject = JsonUtils.readTree(result);
        JsonNode errorResponse = JsonUtils.node(jsonObject, "error_response");
        if (errorResponse != null) {
            return ApiResultVo.error(ApiResultVoEnum.ParamsError, JsonUtils.text(errorResponse, "zh_desc"));
        }
        JsonNode response = JsonUtils.node(jsonObject, "jingdong_pop_order_enSearch_responce");
        if (!"0".equals(JsonUtils.text(response, "code"))) {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(),
                    "京东订单首页返回异常：" + JsonUtils.text(response, "code"));
        }
        Integer orderTotal = JsonUtils.integer(JsonUtils.node(response, "searchorderinfo_result"), "orderTotal");
        // 确实没订单（code=0 且 orderTotal<=0）仍然是成功，不要为了防「静默失败」把正常空结果误判成错误
        if (orderTotal != null && orderTotal > 0) {
            //计算分页
            int totalPage = (orderTotal % pageSize == 0) ? orderTotal / pageSize : (orderTotal / pageSize) + 1;
            orderList.addAll(JsonUtils.list(JsonUtils.node(response, "searchorderinfo_result"), "orderInfoList", JdOrderListResponse.class));
            while (totalPage > page) {
                page++;
                String result1 = fetcher.fetch(startTime, endTime, page, pageSize, appKey, appSecret, accessToken);
                if (!StringUtils.hasText(result1)) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "京东订单第" + page + "/" + totalPage + "页无响应（可能被限流或网络异常），订单拉取不完整，请重试");
                }
                JsonNode jsonObject1 = JsonUtils.readTree(result1);
                JsonNode response1 = JsonUtils.node(jsonObject1, "jingdong_pop_order_enSearch_responce");
                if (!"0".equals(JsonUtils.text(response1, "code"))) {
                    return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(),
                            "京东订单第" + page + "/" + totalPage + "页返回异常：" + JsonUtils.text(response1, "code"));
                }
                orderList.addAll(JsonUtils.list(JsonUtils.node(response1, "searchorderinfo_result"), "orderInfoList", JdOrderListResponse.class));
            }
        }

        if (!orderList.isEmpty()) {
            for (var ord : orderList) {
                ord.getConsigneeInfo().setMobile(JiaMi.serialize(ord.getConsigneeInfo().getMobile()));
                ord.getConsigneeInfo().setTelephone(JiaMi.serialize(ord.getConsigneeInfo().getTelephone()));
            }
        }
        return ApiResultVo.success(orderList.size(), orderList);
    }

    public static ApiResultVo<JdOrderDetailResponse> pullOrderDetail(Long orderId, String appKey, String appSecret, String accessToken) {
        log.info("=======开始拉取JD订单详情{}，参数：{}=========", LocalDateTime.now(), orderId);

        String url="https://api.jd.com/routerjson";

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("format", "json");
        params.put("method", "jingdong.pop.order.enGet");
        params.put("v", "2.0");
        params.put("timestamp", DateUtil.getCurrentDateTime());
        try {
            Map<String, Object> pmap = new TreeMap();
            pmap.put("optional_fields", ORDER_DETAIL_FIELDS);
            pmap.put("order_id", orderId);
            ObjectMapper mapper = JsonUtils.mapper();
            String p = mapper.writeValueAsString(pmap);
//        String param = JsonNode.toJSONString(pmap);
            params.put("360buy_param_json", p);
        }catch (Exception e){
            e.printStackTrace();
        }
        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            try {
                joiner.add(key + "=" + URLEncoder.encode(value,"UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
        HttpClient client = HttpClient.newBuilder().build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .GET()
                .build();
        try {
            PlatformRateLimiter.acquire(url);
            HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = send.body();
            if(StringUtils.hasText(result)) {
                JsonNode jsonObject = JsonUtils.readTree(result);
                if (JsonUtils.node(jsonObject, "error_response") == null) {

                    if ("0".equals(JsonUtils.text(JsonUtils.node(jsonObject, "jingdong_pop_order_enGet_responce"), "code"))) {
                        JdOrderDetailResponse jdOrderDetailResponse = JsonUtils.convert(JsonUtils.node(JsonUtils.node(jsonObject, "jingdong_pop_order_enGet_responce"), "orderDetailInfo"), "orderInfo", JdOrderDetailResponse.class);
                        return ApiResultVo.success(jdOrderDetailResponse);
                    }
                }
                else {
                    return ApiResultVo.error(ApiResultVoEnum.ParamsError,JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));
                }
            }
        }catch (Exception e){
        }
        return ApiResultVo.error(ApiResultVoEnum.ApiException,"未知错误");

    }

   }
