package cn.xinghuaerp.open.jd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import tools.jackson.databind.ObjectMapper;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.jd.model.AfterSale;
import cn.xinghuaerp.open.jd.model.Refund;
import cn.xinghuaerp.open.common.DateUtil;
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

    public static ApiResultVo<Refund> pullRefundList(LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String accessToken) {
        Integer page = 1;
        Integer pageSize = 10;
        // 拉取接口
        String result = pullRefund(startTime,endTime,1,pageSize,appKey,appSecret,accessToken);
        List<Refund> refundList = new ArrayList<>();
        if(StringUtils.hasText(result)) {
            JsonNode jsonObject = JsonUtils.readTree(result);
            if (JsonUtils.node(jsonObject, "error_response") == null) {
//            String code=  jsonObject.getJSONObject("jingdong_pop_order_enSearch_responce").get("code").toString();
                if ("0".equals(JsonUtils.text(JsonUtils.node(jsonObject, "jingdong_pop_afs_soa_refundapply_queryPageList_responce"), "code"))) {
//                JsonNode orderResult= (JsonNode)(JsonUtils.node(jsonObject, "jingdong_pop_order_enSearch_responce")).get("searchorderinfo_result");
//                Integer orderTotal = orderResult.getInteger("orderTotal");
                    Integer total = JsonUtils.integer(JsonUtils.node(JsonUtils.node(jsonObject, "jingdong_pop_afs_soa_refundapply_queryPageList_responce"), "queryResult"), "totalCount");
                    if (total != null && total > 0) {
                        //计算分页
                        int totalPage = (total % pageSize == 0) ? total / pageSize.intValue() : (total / pageSize.intValue()) + 1;

                        // 获取结果
//                    JsonNode orderInfoList = orderResult.getJSONArray("orderInfoList");
                        refundList.addAll(JsonUtils.list(JsonUtils.node(JsonUtils.node(jsonObject, "jingdong_pop_afs_soa_refundapply_queryPageList_responce"), "queryResult"), "result", Refund.class));
                        if (total > pageSize) {
                            while (totalPage > page) {
                                page++;
                                String result1 = pullRefund(startTime, endTime, page, pageSize, appKey, appSecret, accessToken);
                                if (StringUtils.hasText(result1)) {
                                    JsonNode jsonObject1 = JsonUtils.readTree(result1);
                                    if ("0".equals(JsonUtils.text(JsonUtils.node(jsonObject1, "jingdong_pop_afs_soa_refundapply_queryPageList_responce"), "code"))) {
                                        refundList.addAll(JsonUtils.list(JsonUtils.node(JsonUtils.node(jsonObject1, "jingdong_pop_afs_soa_refundapply_queryPageList_responce"), "queryResult"), "result", Refund.class));
                                    }
                                }
                            }
                        }
                    }
                }
            }else {
                return ApiResultVo.error(ApiResultVoEnum.ParamsError,JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));
            }
        }

        return ApiResultVo.success(refundList.size(),refundList);
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
            pmap.put("applyTimeStart", endTimeStr);
            pmap.put("applyTimeEnd", startTimeStr);
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
            HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = send.body();
            return result;
        }catch (Exception e){
            return "";
        }
    }

    public static ApiResultVo<AfterSale> pullAfterSaleList(Long sellerId, LocalDateTime startTime, LocalDateTime endTime, String appKey, String appSecret, String accessToken) {
        Integer page = 1;
        Integer pageSize = 10;
        // 拉取接口
        String result = pullAfterSale(sellerId,startTime,endTime,1,pageSize,appKey,appSecret,accessToken);
        List<AfterSale> afterSaleList = new ArrayList<>();
        if(StringUtils.hasText(result)) {
            JsonNode jsonObject = JsonUtils.readTree(result);
            if (JsonUtils.node(jsonObject, "error_response") == null) {
//            String code=  jsonObject.getJSONObject("jingdong_pop_order_enSearch_responce").get("code").toString();
                if ("0".equals(JsonUtils.text(JsonUtils.node(jsonObject, "jingdong_asc_query_list_responce"), "code"))) {
//                JsonNode orderResult= (JsonNode)(JsonUtils.node(jsonObject, "jingdong_pop_order_enSearch_responce")).get("searchorderinfo_result");
//                Integer orderTotal = orderResult.getInteger("orderTotal");
                    Integer total = JsonUtils.integer(JsonUtils.node(JsonUtils.node(jsonObject, "jingdong_asc_query_list_responce"), "pageResult"), "totalCount");
                    if (total != null && total > 0) {
                        //计算分页
                        int totalPage = (total % pageSize == 0) ? total / pageSize.intValue() : (total / pageSize.intValue()) + 1;

                        // 获取结果
//                    JsonNode orderInfoList = orderResult.getJSONArray("orderInfoList");
                        afterSaleList.addAll(JsonUtils.list(JsonUtils.node(JsonUtils.node(jsonObject, "jingdong_asc_query_list_responce"), "pageResult"), "data", AfterSale.class));
                        if (total > pageSize) {
                            while (totalPage > page) {
                                page++;
                                String result1 = pullAfterSale(sellerId,startTime, endTime, page, pageSize, appKey, appSecret, accessToken);
                                if (StringUtils.hasText(result1)) {
                                    JsonNode jsonObject1 = JsonUtils.readTree(result1);
                                    if ("0".equals(JsonUtils.text(JsonUtils.node(jsonObject1, "jingdong_asc_query_list_responce"), "code"))) {
                                        afterSaleList.addAll(JsonUtils.list(JsonUtils.node(JsonUtils.node(jsonObject1, "jingdong_asc_query_list_responce"), "pageResult"), "data", AfterSale.class));
                                    }
                                }
                            }
                        }
                    }
                }
            }else {
                return ApiResultVo.error(ApiResultVoEnum.ParamsError,JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));
            }
        }

        return ApiResultVo.success(afterSaleList.size(),afterSaleList);
    }

    protected static String pullAfterSale(Long sellerId, LocalDateTime startTime, LocalDateTime endTime,Integer pageNo,Integer pageSize,String appKey,String appSecret,String accessToken){
        log.info("========拉取京东售后=========");
        // 拉取售后
        //  https://open.jd.com/home/home/#/doc/api?apiCateId=241&apiId=2136&apiName=jingdong.asc.query.list

        String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

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
            // 客户期望(10-退货 20-换货 30-维修 40-大家电安装 50-大家电移机 60-大家电增值服务 70-上门维修 90-优鲜赔 80-补发商品 100-试用收回 11-仅退款)
            HttpResponse<String> send = client.send(request, HttpResponse.BodyHandlers.ofString());
            String result = send.body();
            return result;
        }catch (Exception e){
            return "";
        }
    }

   }
