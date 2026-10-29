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

    /** 单次拉取最多翻这么多页，防止 has_next 恒为 true 时无限调用平台接口 */
    private static final int MAX_PAGE = 500;

    public static ApiResultVo<TaoOrderListResponse> pullOrderList(LocalDateTime startModified, LocalDateTime endModified , String appKey, String appSecret, String sessionKey) throws IOException {
        int pageNo = 1;
        String resultString = pullOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名发生错误");

        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            // 注意：翻页与首页用的是同一个 method（taobao.trades.sold.get），响应节点固定为 trades_sold_get_response；
            // 只有 taobao.trades.sold.increment.get 才返回 trades_sold_increment_get_response。
            JsonNode dataResult = JsonUtils.node(result, "trades_sold_get_response");
            if (dataResult == null) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "响应缺少 trades_sold_get_response 节点，请核对接口返回");
            }
            //组合的订单列表
            List<TaoOrderListResponse> tradeBeans = new ArrayList<>();
            boolean hasNext = Boolean.TRUE.equals(JsonUtils.bool(dataResult, "has_next"));
            appendTrades(dataResult, tradeBeans);

            //循环取下一页
            while (hasNext) {
                if (pageNo >= MAX_PAGE) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "订单分页达到上限（" + MAX_PAGE + "页）仍未取完，请缩小时间范围后重试");
                }
                pageNo++;
                String resultString1 = pullOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
                if (!StringUtils.hasText(resultString1)) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "第" + pageNo + "页请求失败，订单拉取不完整，请重试");
                }
                JsonNode result1 = JsonUtils.readTree(resultString1);
                if (result1.get("error_response") != null) {
                    return ApiResultVo.error(ApiResultVoEnum.ParamsError.getIndex(),
                            "第" + pageNo + "页返回错误：" + JsonUtils.text(JsonUtils.node(result1, "error_response"), "sub_msg"));
                }
                JsonNode dataResult1 = JsonUtils.node(result1, "trades_sold_get_response");
                if (dataResult1 == null) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "第" + pageNo + "页响应缺少 trades_sold_get_response 节点，订单拉取不完整");
                }
                hasNext = Boolean.TRUE.equals(JsonUtils.bool(dataResult1, "has_next"));
                int before = tradeBeans.size();
                appendTrades(dataResult1, tradeBeans);
                if (tradeBeans.size() == before && hasNext) {
                    // 声明还有下一页但本页为空，继续翻只会空转，直接判为不完整
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "第" + pageNo + "页为空但仍声明下一页（has_next=true），订单拉取不完整，请核对");
                }
            }
            return ApiResultVo.success(tradeBeans.size(), tradeBeans);

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

    /**
     * 把一页响应里的 trade 列表追加到 out，首页与翻页共用，避免两处解析逻辑漂移。
     */
    private static void appendTrades(JsonNode dataResult, List<TaoOrderListResponse> out) {
        JsonNode trades = JsonUtils.node(dataResult, "trades");
        if (trades == null) return;
        for (Object item : JsonUtils.node(trades, "trade")) {
            TaoOrderListResponse tradeBean = JsonUtils.readValue(item.toString(), TaoOrderListResponse.class);
            JsonNode s = (JsonNode) item;
            JsonNode ss = JsonUtils.node(s, "orders");
            if (ss != null && ss.get("order") != null) {
                List<TaoOrderItem> orders = JsonUtils.readList(ss.get("order").toString(), TaoOrderItem.class);
                tradeBean.setOrders(orders);
            }
            out.add(tradeBean);
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
            if (dataResult == null) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "响应缺少 trades_sold_increment_get_response 节点，请核对接口返回");
            }
            List<TaoOrderListResponse> tradeBeans = new ArrayList<>();
            boolean hasNext = Boolean.TRUE.equals(JsonUtils.bool(dataResult, "has_next"));
            appendTrades(dataResult, tradeBeans);
            while (hasNext) {
                if (pageNo >= MAX_PAGE) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "增量订单分页达到上限（" + MAX_PAGE + "页）仍未取完，请缩小时间范围后重试");
                }
                pageNo++;
                String resultString1 = updateOrderList(startModified, endModified, pageNo, appKey, appSecret, sessionKey);
                if (!StringUtils.hasText(resultString1)) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "增量订单第" + pageNo + "页请求失败，拉取不完整，请重试");
                }
                JsonNode result1 = JsonUtils.readTree(resultString1);
                if (result1.get("error_response") != null) {
                    return ApiResultVo.error(ApiResultVoEnum.ParamsError.getIndex(),
                            "增量订单第" + pageNo + "页返回错误：" + JsonUtils.text(JsonUtils.node(result1, "error_response"), "sub_msg"));
                }
                JsonNode dataResult1 = JsonUtils.node(result1, "trades_sold_increment_get_response");
                if (dataResult1 == null) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "增量订单第" + pageNo + "页响应缺少 trades_sold_increment_get_response 节点，拉取不完整");
                }
                hasNext = Boolean.TRUE.equals(JsonUtils.bool(dataResult1, "has_next"));
                int before = tradeBeans.size();
                appendTrades(dataResult1, tradeBeans);
                if (tradeBeans.size() == before && hasNext) {
                    return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                            "增量订单第" + pageNo + "页为空但仍声明下一页（has_next=true），拉取不完整");
                }
            }
            return ApiResultVo.success(tradeBeans.size(), tradeBeans);

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
