package cn.xinghuaerp.open.tao;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.*;
import cn.xinghuaerp.open.tao.response.TaoRefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class TaoRefundApiHelper {
    private static Logger log = LoggerFactory.getLogger(TaoRefundApiHelper.class);

    private static final String REFUND_FIELDS = "refund_id, tid, title, buyer_nick, seller_nick, total_fee, status, created" +
            ",num, refund_fee, oid, good_status," +
            " company_name, sid, payment, reason, desc, has_good_return,num_iid, modified,outer_id, order_status,refund_phase,dispute_type,end_time,sku,combineItemInfo,sku_info,skuInfo,combine_item_info";
    private static final String refund_fields = "refund_id, tid, title, buyer_nick, seller_nick, total_fee, status, created" +
            ",num, refund_fee, oid, good_status, " +
            "company_name, sid, payment, reason, desc, has_good_return" +
            ", modified, order_status,refund_phase,dispute_type,end_time,new_sku,sku";
    /**
     * 拉取淘系退货单
     * @param
     * @param appKey
     * @param appSecret
     * @param sessionKey
     * @return
     * @throws
     */
    public static ApiResultVo<TaoRefundResponse> pullRefund(LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String sessionKey) throws IOException {
        int pageNo=1;
        String resultString = pullRefundList(startTime,endTime,pageNo, appKey, appSecret, sessionKey);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "接口调用错误");
        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //组合的订单列表
            List<TaoRefundResponse> taoRefundResponseList = new ArrayList<>();

            //没有错误
            JsonNode dataResult = JsonUtils.node(result, "refunds_receive_get_response");
            Boolean hasNext = JsonUtils.bool(dataResult, "has_next");
//            JsonNode refunds = JsonUtils.node(dataResult, "refunds");

            if (dataResult != null) {
//                List<Refund> refund = JsonNode.parseArray(JsonNode.toJSONString(refunds.getJSONArray("refund")), Refund.class);
                List<TaoRefundResponse> taoRefundResponse = JsonUtils.readList(JsonUtils.write(JsonUtils.node(JsonUtils.node(dataResult, "refunds"), "refund")), TaoRefundResponse.class);
                taoRefundResponseList.addAll(taoRefundResponse);
                //循环取下一页
                while (hasNext) {
                    pageNo++;
                    String resultString1 =  pullRefundList(startTime,endTime,pageNo, appKey, appSecret, sessionKey);
                    JsonNode result1 = JsonUtils.readTree(resultString1);
                    //没有错误
                    JsonNode dataResult1 = JsonUtils.node(result1, "refunds_receive_get_response");
                    hasNext = JsonUtils.bool(dataResult1, "has_next");
                    JsonNode orderListResult1 = JsonUtils.node(dataResult1, "refunds");
                    if (orderListResult1 != null) {
                        List<TaoRefundResponse> taoRefundResponse1 = JsonUtils.readList(JsonUtils.write(JsonUtils.node(JsonUtils.node(dataResult1, "refunds"), "refund")), TaoRefundResponse.class);
                        taoRefundResponseList.addAll(taoRefundResponse1);
                    }
                }
//                return new PullResult(tradeBeans.size(), tradeBeans);
//                return ApiResultVo.success(tradeBeans.size(), tradeBeans);
            } else {
//                return new PullResult(0, tradeBeans);
//                return ApiResultVo.success(0, tradeBeans);
            }
            return ApiResultVo.success(taoRefundResponseList.size(), taoRefundResponseList);
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

    protected static String pullRefundList(LocalDateTime startModified,LocalDateTime endModified,Integer pageNo, String appKey, String appSecret, String sessionKey) throws IOException {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.refunds.receive.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");
        params.put("start_modified", startModified.format(df));
        params.put("end_modified", endModified.format(df));
        params.put("page_no", pageNo.toString());
        params.put("fields", REFUND_FIELDS);
        params.put("page_size", "40");
        params.put("use_has_next", "true");
        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            return "";//签名错误
        }
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
//        TaoRefundApiService remoting = RemoteUtil.Remoting(url, TaoRefundApiService.class);
//        String result = remoting.getRefundList();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);
        return resultString;
    }

//    public static ResultVo<TaoRefund> pullRefundDetail(Long refundId,  String url, String appKey, String appSecret, String sessionKey) throws ApiException {
//        TaobaoClient client = new DefaultTaobaoClient(url, appKey, appSecret);
//        //查到了数据
//        Refund detail = pullRefundDetail(client, sessionKey, refundId);
//        if (detail != null) {
//            TaoRefund refund = assembleRefund(detail);
//            refund.setDisputeType(detail.getDisputeType());
//            refund.setNumIid(detail.getNumIid());
//            refund.setNum(detail.getNum());
//            return ResultVo.success(refund);
//        } else {
//            return ResultVo.error(ResultVoEnum.SystemException, "没有找到退款数据");
//        }
//
//    }

    /**
     * 获取退款详情
     * @param
     * @param sessionKey
     * @param refundId
     * @return
     */
    public static ApiResultVo<TaoRefundResponse> pullRefundDetail(Long refundId, String appKey, String appSecret, String sessionKey) throws IOException {
        String fields = "refund_id, alipay_no, tid, oid, buyer_nick, seller_nick, total_fee, status, created, refund_fee, good_status, has_good_return, " +
                "payment, reason, desc,modified, num_iid, title, price, num, good_return_time, company_name, sid, address, shipping_type, refund_remind_timeout, refund_phase, " +
                "refund_version, operation_contraint,order_status,sku_id, attribute, outer_id,dispute_type,sku,end_time";
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.refund.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");

        params.put("fields", fields);
        params.put("refund_id",refundId.toString());

        try {
            String sign = SignUtil.signTopRequest(params, appSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            return ApiResultVo.error(ApiResultVoEnum.SIGN_ERROR.getIndex(), "签名错误");//签名错误
        }
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        String urlP = joiner.toString();
        url = url + "?" + urlP;

        // 调用接口
//        TaoRefundApiService remoting = RemoteUtil.Remoting(url, TaoRefundApiService.class);
//        String resultString = remoting.getRefundDetail();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "接口调用错误");
        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult = JsonUtils.node(result, "refund_get_response");
            if (dataResult != null) {
                TaoRefundResponse taoRefundResponse = JsonUtils.convert(dataResult, "refund", TaoRefundResponse.class);
                return ApiResultVo.success(taoRefundResponse);
            }else {
                return ApiResultVo.error(ApiResultVoEnum.Fail,"没有获取到数据");
            }
        }
        else {
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


}
