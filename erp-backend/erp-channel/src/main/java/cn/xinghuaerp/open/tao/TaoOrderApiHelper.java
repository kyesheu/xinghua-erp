package cn.xinghuaerp.open.tao;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.*;
import cn.xinghuaerp.open.tao.response.TaoOrderDetailResponse;
import cn.xinghuaerp.open.tao.response.TaoOrderItem;
import cn.xinghuaerp.open.tao.response.TaoOrderListResponse;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.DateUtil;
import cn.xinghuaerp.open.common.SignUtil;

public class TaoOrderApiHelper {

    private static final String ORDER_List_FIELDS = "tid,receiver_name,receiver_country,receiver_state,receiver_city,receiver_district,receiver_town,receiver_address," +
            "receiver_mobile,receiver_phone,title,seller_flag,has_buyer_message,credit_card_fee,mark_desc,consign_time,end_time,sid,yfx_fee,has_yfx" +
            ",type,status,orders,rx_audit_status,seller_memo,buyer_memo,buyer_message,pay_time,created,modified,buyer_nick,buyer_open_uid" +
            ",alipay_no,buyer_alipay_no,buyer_email,num_iid,num,price,total_fee,adjust_fee,post_fee,discount_fee,payment,received_payment" +
            ",available_confirm_fee,commission_fee,oaid,promotion_details,tmall_coupon_fee,logistics_company,invoice_no,is_sh_ship";
    private static final String ORDER_DETAIL_FIELDS = "tid,receiver_name,receiver_country,receiver_state,receiver_city,receiver_district,receiver_town,receiver_address," +
            "receiver_mobile,receiver_phone,title,seller_flag,has_buyer_message,credit_card_fee,mark_desc,consign_time,end_time,sid,yfx_fee,has_yfx" +
            ",type,status,orders,rx_audit_status,seller_memo,buyer_memo,buyer_message,pay_time,created,modified,buyer_nick,buyer_open_uid" +
            ",alipay_no,buyer_alipay_no,buyer_email,num_iid,num,price,total_fee,adjust_fee,post_fee,discount_fee,payment,received_payment" +
            ",available_confirm_fee,commission_fee,oaid,promotion_details,tmall_coupon_fee,logistics_infos,LogisticsInfos,logistics_company,invoice_no,is_sh_ship";

    public static ApiResultVo<TaoOrderListResponse> pullOrderList(LocalDateTime startModified, LocalDateTime endModified , String appKey, String appSecret, String sessionKey) throws IOException {
        Integer pageNo = 1;
        String resultString = pullOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名发生错误");

        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult = JsonUtils.node(result, "trades_sold_get_response");
            Boolean hasNext = JsonUtils.bool(dataResult, "has_next");
            JsonNode orderListResult = JsonUtils.node(dataResult, "trades");
            //组合的订单列表
            List<TaoOrderListResponse> tradeBeans = new ArrayList<>();
            if (orderListResult != null) {
                for (Object item : JsonUtils.node(orderListResult, "trade")) {

                    TaoOrderListResponse tradeBean = JsonUtils.readValue(item.toString(), TaoOrderListResponse.class);
                    // 转换orders
                    JsonNode s = (JsonNode) item;
                    JsonNode ss = JsonUtils.node(s, "orders");
                    List<TaoOrderItem> orders = JsonUtils.readList(ss.get("order").toString(), TaoOrderItem.class);
                    tradeBean.setOrders(orders);
                    tradeBeans.add(tradeBean);
                }
                //循环取下一页
                while (hasNext) {
                    pageNo++;
                    String resultString1 = pullOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
                    JsonNode result1 = JsonUtils.readTree(resultString1);
                    //没有错误
                    JsonNode dataResult1 = JsonUtils.node(result1, "trades_sold_increment_get_response");
                    hasNext = JsonUtils.bool(dataResult1, "has_next");
                    JsonNode orderListResult1 = JsonUtils.node(dataResult1, "trades");
                    if (orderListResult1 != null) {
                        for (Object item : JsonUtils.node(orderListResult1, "trade")) {
//                            String s = JsonNode.toJSONString(item);
                            TaoOrderListResponse tradeBean = JsonUtils.readValue(JsonUtils.write(item), TaoOrderListResponse.class);
                            // 转换orders
                            JsonNode s = (JsonNode) item;
                            JsonNode ss = JsonUtils.node(s, "orders");
                            List<TaoOrderItem> orders = JsonUtils.readList(ss.get("order").toString(), TaoOrderItem.class);
                            tradeBean.setOrders(orders);
                            tradeBeans.add(tradeBean);
                        }
                    }
                }
//                return new PullResult(tradeBeans.size(), tradeBeans);
                return ApiResultVo.success(tradeBeans.size(), tradeBeans);
            } else {
//                return new PullResult(0, tradeBeans);
                return ApiResultVo.success(0, tradeBeans);
            }

        } else {
            // 有错误
            JsonNode errorInfo = JsonUtils.node(result, "error_response");
            if (JsonUtils.text(errorInfo, "code").equals("27")) {
                // SessionKey非法
                return ApiResultVo.error(ApiResultVoEnum.TokenFail.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
            } else if (JsonUtils.text(errorInfo, "code").equals("25")) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名错误，有可能是appSecret错了");
            } else if (JsonUtils.text(errorInfo, "code").equals("29")) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "错误的AppKey");
            } else {
                return ApiResultVo.error(ApiResultVoEnum.ParamsError.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
            }
        }

    }

    protected static String pullOrderList(LocalDateTime startModified,LocalDateTime endModified,Integer pageNo,String appKey, String appSecret, String sessionKey) throws IOException {
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//        String format = LocalDateTime.now().format(df);
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.trades.sold.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");
        params.put("start_created", startModified.format(df));
        params.put("end_created", endModified.format(df));
        params.put("page_no", pageNo.toString());
        params.put("fields", ORDER_List_FIELDS);
        params.put("use_has_next", "true");
        params.put("page_size", "50");
        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        }catch (Exception e){
            return "";//签名错误
        }
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
//        TaoOrderApiService remoting = RemoteUtil.Remoting(url, TaoOrderApiService.class);
//        return remoting.getOrderList();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);
        return resultString;
    }


    /**
     * 更新订单（循环分页）
     *
     * @param appKey
     * @param appSecret
     * @param sessionKey
     * @return
     */
    public static ApiResultVo<TaoOrderListResponse> pullIncrementOrderList(LocalDateTime startModified, LocalDateTime endModified , String appKey, String appSecret, String sessionKey) throws IOException {
        Integer pageNo = 1;
        String resultString = updateOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名发生错误");

        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult = JsonUtils.node(result, "trades_sold_increment_get_response");
            Boolean hasNext = JsonUtils.bool(dataResult, "has_next");
            JsonNode orderListResult = JsonUtils.node(dataResult, "trades");
            //组合的订单列表
            List<TaoOrderListResponse> tradeBeans = new ArrayList<>();
            if (orderListResult != null) {
                for (Object item : JsonUtils.node(orderListResult, "trade")) {

                    TaoOrderListResponse tradeBean = JsonUtils.readValue(item.toString(), TaoOrderListResponse.class);
                    // 转换orders
                    JsonNode s = (JsonNode) item;
                    JsonNode ss = JsonUtils.node(s, "orders");
                    List<TaoOrderItem> orders = JsonUtils.readList(ss.get("order").toString(), TaoOrderItem.class);
                    tradeBean.setOrders(orders);
                    tradeBeans.add(tradeBean);
                }
                //循环取下一页
                while (hasNext) {
                    pageNo++;
                    String resultString1 = updateOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
                    JsonNode result1 = JsonUtils.readTree(resultString1);
                    //没有错误
                    JsonNode dataResult1 = JsonUtils.node(result1, "trades_sold_increment_get_response");
                    hasNext = JsonUtils.bool(dataResult1, "has_next");
                    JsonNode orderListResult1 = JsonUtils.node(dataResult1, "trades");
                    if (orderListResult1 != null) {
                        for (Object item : JsonUtils.node(orderListResult1, "trade")) {
//                            String s = JsonNode.toJSONString(item);
                            TaoOrderListResponse tradeBean = JsonUtils.readValue(JsonUtils.write(item), TaoOrderListResponse.class);
                            // 转换orders
                            JsonNode s = (JsonNode) item;
                            JsonNode ss = JsonUtils.node(s, "orders");
                            List<TaoOrderItem> orders = JsonUtils.readList(ss.get("order").toString(), TaoOrderItem.class);
                            tradeBean.setOrders(orders);
                            tradeBeans.add(tradeBean);
                        }
                    }
                }
//                return new PullResult(tradeBeans.size(), tradeBeans);
                return ApiResultVo.success(tradeBeans.size(), tradeBeans);
            } else {
//                return new PullResult(0, tradeBeans);
                return ApiResultVo.success(0, tradeBeans);
            }

        } else {
            // 有错误
            JsonNode errorInfo = JsonUtils.node(result, "error_response");
            if (JsonUtils.text(errorInfo, "code").equals("27")) {
                // SessionKey非法
                return ApiResultVo.error(ApiResultVoEnum.TokenFail.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
            } else if (JsonUtils.text(errorInfo, "code").equals("25")) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名错误，有可能是appSecret错了");
            } else if (JsonUtils.text(errorInfo, "code").equals("29")) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "错误的AppKey");
            } else {
                return ApiResultVo.error(ApiResultVoEnum.ParamsError.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
            }
        }

    }

    protected static String updateOrderList(LocalDateTime startModified,LocalDateTime endModified,Integer pageNo,String appKey, String appSecret, String sessionKey) throws IOException {
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//        String format = LocalDateTime.now().format(df);
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.trades.sold.increment.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");
        params.put("start_modified", startModified.format(df));
        params.put("end_modified", endModified.format(df));
        params.put("page_no", pageNo.toString());
        params.put("fields", ORDER_List_FIELDS);
        params.put("use_has_next", "true");
        params.put("page_size", "50");
        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        }catch (Exception e){
            return "";//签名错误
        }
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
//        TaoOrderApiService remoting = RemoteUtil.Remoting(url, TaoOrderApiService.class);
//        return remoting.getOrderList();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);
        return resultString;
    }

    /**
     * 获取详情
     * @param tid
     * @param appKey
     * @param appSecret
     * @param sessionKey
     * @return
     * @throws
     */
    public static ApiResultVo<TaoOrderDetailResponse> pullOrderDetail(String tid, String appKey, String appSecret, String sessionKey) throws IOException {
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.trade.fullinfo.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");
        params.put("fields", ORDER_DETAIL_FIELDS);
        params.put("tid", tid);
        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        }catch (Exception e){
            e.printStackTrace();
        }
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
//        TaoOrderApiService remoting = RemoteUtil.Remoting(url, TaoOrderApiService.class);
//        String resultString = remoting.getOrderDetail();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);

        JsonNode result = JsonUtils.readTree(resultString);
        if(result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult1 =JsonUtils.node(result, "trade_fullinfo_get_response");

            JsonNode orderResult =  JsonUtils.node(dataResult1, "trade");
            if (!orderResult.isEmpty()) {
                TaoOrderDetailResponse detail = JsonUtils.readValue(orderResult.toString(), TaoOrderDetailResponse.class);

                return ApiResultVo.success(detail);
            }
        }else {
            // 有错误
            JsonNode errorInfo = JsonUtils.node(result, "error_response");
            if (JsonUtils.text(errorInfo, "code").equals("27")) {
                // SessionKey非法
                return ApiResultVo.error(ApiResultVoEnum.TokenFail.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
            } else if (JsonUtils.text(errorInfo, "code").equals("25")) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名错误，有可能是appSecret错了");
            } else if (JsonUtils.text(errorInfo, "code").equals("29")) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "错误的AppKey");
            } else {
                return ApiResultVo.error(ApiResultVoEnum.ParamsError.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
            }
        }
        return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "未知错误");
    }


}
