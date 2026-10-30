package cn.xinghuaerp.open.jd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import tools.jackson.databind.ObjectMapper;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.jd.model.AfterSale;
import cn.xinghuaerp.open.jd.model.Refund;
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

public class JdAfterSaleApiHelper {
    private static Logger log = LoggerFactory.getLogger(JdAfterSaleApiHelper.class);

    /** 京东按申请时间检索，必须给时间窗；调用方没给时默认回看 90 天 */
    private static final int DEFAULT_LOOKBACK_DAYS = 90;
    /** 单次翻页上限，防止 total 异常时无限调用 */
    private static final int MAX_PAGE = 500;

    /**
     * 京东开放平台必传 applyTimeStart/applyTimeEnd，传 null 会在格式化时 NPE。
     * 这里统一兜底成 [now-90d, now]，调用方不需要自己拼时间窗。
     */
    private static LocalDateTime startOr(LocalDateTime startTime) {
        return startTime != null ? startTime : LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS);
    }

    private static LocalDateTime endOr(LocalDateTime endTime) {
        return endTime != null ? endTime : LocalDateTime.now();
    }

    public static ApiResultVo<Refund> pullRefundList(LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String accessToken) {
        return pullRefundList(startTime, endTime, 10, MAX_PAGE, appKey, appSecret, accessToken);
    }

    public static ApiResultVo<Refund> pullRefundList(LocalDateTime startTime, LocalDateTime endTime, int pageSize, int maxPage, String appKey, String appSecret, String accessToken) {
        LocalDateTime st = startOr(startTime);
        LocalDateTime et = endOr(endTime);
        String result = pullRefund(st, et, 1, pageSize, appKey, appSecret, accessToken);
        List<Refund> refundList = new ArrayList<>();
        if (!StringUtils.hasText(result)) {
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "退款首页请求失败，退款拉取不完整");
        }
        JsonNode jsonObject = JsonUtils.readTree(result);
        if (JsonUtils.node(jsonObject, "error_response") != null) {
            return ApiResultVo.error(ApiResultVoEnum.ParamsError, JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));
        }
        JsonNode resp = JsonUtils.node(jsonObject, "jingdong_pop_afs_soa_refundapply_queryPageList_responce");
        if (!"0".equals(JsonUtils.text(resp, "code"))) {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "退款首页返回异常：" + JsonUtils.text(resp, "code"));
        }
        Integer total = JsonUtils.integer(JsonUtils.node(resp, "queryResult"), "totalCount");
        if (total == null || total <= 0) return ApiResultVo.success(0, refundList);

        refundList.addAll(JsonUtils.list(JsonUtils.node(resp, "queryResult"), "result", Refund.class));
        int totalPage = (total % pageSize == 0) ? total / pageSize : (total / pageSize) + 1;
        if (totalPage > maxPage) {
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                    "退款分页需要" + totalPage + "页，超过单次上限" + maxPage + "页，请缩小时间范围重试");
        }
        for (int page = 2; page <= totalPage; page++) {
            String result1 = pullRefund(st, et, page, pageSize, appKey, appSecret, accessToken);
            if (!StringUtils.hasText(result1)) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                        "退款第" + page + "/" + totalPage + "页请求失败，退款拉取不完整");
            }
            JsonNode jsonObject1 = JsonUtils.readTree(result1);
            JsonNode resp1 = JsonUtils.node(jsonObject1, "jingdong_pop_afs_soa_refundapply_queryPageList_responce");
            if (!"0".equals(JsonUtils.text(resp1, "code"))) {
                return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(),
                        "退款第" + page + "/" + totalPage + "页返回异常：" + JsonUtils.text(resp1, "code"));
            }
            List<Refund> pageList = JsonUtils.list(JsonUtils.node(resp1, "queryResult"), "result", Refund.class);
            if (pageList == null || pageList.isEmpty()) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                        "退款第" + page + "/" + totalPage + "页为空，退款拉取不完整，请重试");
            }
            refundList.addAll(pageList);
        }
        return ApiResultVo.success(refundList.size(), refundList);
    }

    protected static String pullRefund( LocalDateTime startTime, LocalDateTime endTime,Integer pageNo,Integer pageSize,String appKey,String appSecret,String accessToken){
        log.info("========拉取京东退款（用户下单付款后取消订单）=========");
        // 拉取退款
        // https://open.jd.com/#/doc/api?apiCateId=71&apiId=925&apiName=jingdong.pop.afs.soa.refundapply.queryPageList

        String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String url="https://api.jd.com/routerjson";

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("format", "json");
        params.put("method", "jingdong.pop.afs.soa.refundapply.queryPageList");
        params.put("v", "2.0");
        params.put("timestamp", DateUtil.getCurrentDateTime());
        try {
            Map<String, Object> pmap = new TreeMap();
            // applyTimeStart 必须早于 applyTimeEnd，之前两者写反了，接口会返回空或报错
            pmap.put("applyTimeStart", startTimeStr);
            pmap.put("applyTimeEnd", endTimeStr);
            pmap.put("pageIndex", pageNo);
            pmap.put("pageSize", pageSize);
            pmap.put("showSku",true);
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

    public static ApiResultVo<AfterSale> pullAfterSaleList(Long sellerId, LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String accessToken) {
        return pullAfterSaleList(sellerId, startTime, endTime, 10, MAX_PAGE, appKey, appSecret, accessToken);
    }

    public static ApiResultVo<AfterSale> pullAfterSaleList(Long sellerId, LocalDateTime startTime, LocalDateTime endTime, int pageSize, int maxPage, String appKey, String appSecret, String accessToken) {
        LocalDateTime st = startOr(startTime);
        LocalDateTime et = endOr(endTime);
        String result = pullAfterSale(sellerId, st, et, 1, pageSize, appKey, appSecret, accessToken);
        List<AfterSale> afterSaleList = new ArrayList<>();
        if (!StringUtils.hasText(result)) {
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "售后首页请求失败，售后拉取不完整");
        }
        JsonNode jsonObject = JsonUtils.readTree(result);
        if (JsonUtils.node(jsonObject, "error_response") != null) {
            return ApiResultVo.error(ApiResultVoEnum.ParamsError, JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));
        }
        JsonNode resp = JsonUtils.node(jsonObject, "jingdong_asc_query_list_responce");
        if (!"0".equals(JsonUtils.text(resp, "code"))) {
            return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "售后首页返回异常：" + JsonUtils.text(resp, "code"));
        }
        Integer total = JsonUtils.integer(JsonUtils.node(resp, "pageResult"), "totalCount");
        if (total == null || total <= 0) return ApiResultVo.success(0, afterSaleList);

        afterSaleList.addAll(JsonUtils.list(JsonUtils.node(resp, "pageResult"), "data", AfterSale.class));
        int totalPage = (total % pageSize == 0) ? total / pageSize : (total / pageSize) + 1;
        if (totalPage > maxPage) {
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                    "售后分页需要" + totalPage + "页，超过单次上限" + maxPage + "页，请缩小时间范围重试");
        }
        for (int page = 2; page <= totalPage; page++) {
            String result1 = pullAfterSale(sellerId, st, et, page, pageSize, appKey, appSecret, accessToken);
            if (!StringUtils.hasText(result1)) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                        "售后第" + page + "/" + totalPage + "页请求失败，售后拉取不完整");
            }
            JsonNode jsonObject1 = JsonUtils.readTree(result1);
            JsonNode resp1 = JsonUtils.node(jsonObject1, "jingdong_asc_query_list_responce");
            if (!"0".equals(JsonUtils.text(resp1, "code"))) {
                return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(),
                        "售后第" + page + "/" + totalPage + "页返回异常：" + JsonUtils.text(resp1, "code"));
            }
            List<AfterSale> pageList = JsonUtils.list(JsonUtils.node(resp1, "pageResult"), "data", AfterSale.class);
            if (pageList == null || pageList.isEmpty()) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(),
                        "售后第" + page + "/" + totalPage + "页为空，售后拉取不完整，请重试");
            }
            afterSaleList.addAll(pageList);
        }
        return ApiResultVo.success(afterSaleList.size(), afterSaleList);
    }

    /**
     * 京东售后没有独立详情接口，只能按时间窗 + 分页在列表里找。
     * 这里用 100 条/页、最多 20 页（2000 条）的近 90 天窗口扫描，找到即返回。
     * 之前用默认 10 条/页只翻首页，几乎必然找不到目标售后单。
     */
    public static ApiResultVo<AfterSale> pullAfterSaleById(Long sellerId, String afterId, String appKey, String appSecret, String accessToken) {
        final int PAGE_SIZE = 100;
        final int MAX_SCAN_PAGE = 20;
        LocalDateTime et = LocalDateTime.now();
        LocalDateTime st = et.minusDays(DEFAULT_LOOKBACK_DAYS);
        for (int page = 1; page <= MAX_SCAN_PAGE; page++) {
            String result = pullAfterSale(sellerId, st, et, page, PAGE_SIZE, appKey, appSecret, accessToken);
            if (!StringUtils.hasText(result)) {
                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "售后第" + page + "页请求失败，无法定位售后单");
            }
            JsonNode jsonObject = JsonUtils.readTree(result);
            if (JsonUtils.node(jsonObject, "error_response") != null) {
                return ApiResultVo.error(ApiResultVoEnum.ParamsError, JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));
            }
            JsonNode resp = JsonUtils.node(jsonObject, "jingdong_asc_query_list_responce");
            if (!"0".equals(JsonUtils.text(resp, "code"))) {
                return ApiResultVo.error(ApiResultVoEnum.Fail.getIndex(), "售后第" + page + "页返回异常：" + JsonUtils.text(resp, "code"));
            }
            List<AfterSale> pageList = JsonUtils.list(JsonUtils.node(resp, "pageResult"), "data", AfterSale.class);
            if (pageList == null || pageList.isEmpty()) break;
            for (AfterSale rf : pageList) {
                if (rf.getServiceId() != null && afterId.equals(rf.getServiceId().toString())) return ApiResultVo.success(rf);
                if (rf.getApplyId() != null && afterId.equals(rf.getApplyId().toString())) return ApiResultVo.success(rf);
            }
            Integer total = JsonUtils.integer(JsonUtils.node(resp, "pageResult"), "totalCount");
            if (total != null && page * PAGE_SIZE >= total) break;
        }
        return ApiResultVo.error(ApiResultVoEnum.ParamsError, "未找到售后单[" + afterId + "]，已扫描近" + DEFAULT_LOOKBACK_DAYS + "天记录");
    }

    protected static String pullAfterSale(Long sellerId, LocalDateTime startTime, LocalDateTime endTime,Integer pageNo,Integer pageSize,String appKey,String appSecret,String accessToken){
        log.info("========拉取京东售后=========");
        // 拉取售后
        //  https://open.jd.com/home/home/#/doc/api?apiCateId=241&apiId=2136&apiName=jingdong.asc.query.list

        String startTimeStr = startOr(startTime).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endTimeStr = endOr(endTime).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String url="https://api.jd.com/routerjson";

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("format", "json");
        params.put("method", "jingdong.asc.query.list");
        params.put("v", "2.0");
        params.put("timestamp", DateUtil.getCurrentDateTime());
        try {
            Map<String, Object> pmap = new TreeMap();
//            pmap.put("approveTimeBegin", startTimeStr);
            pmap.put("applyTimeBegin", startTimeStr);
//            pmap.put("approveTimeEnd", endTimeStr);
            pmap.put("applyTimeEnd", endTimeStr);
            pmap.put("buId", sellerId.toString());
            pmap.put("operatePin", "testPin");
            pmap.put("operateNick", "testPin");
            pmap.put("pageNumber", pageNo);
            pmap.put("pageSize", pageSize);


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
            PlatformRateLimiter.acquire(url);
            // 客户期望(10-退货 20-换货 30-维修 40-大家电安装 50-大家电移机 60-大家电增值服务 70-上门维修 90-优鲜赔 80-补发商品 100-试用收回 11-仅退款)
            HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = send.body();
            return result;
        }catch (Exception e){
            return "";
        }
    }

   }
