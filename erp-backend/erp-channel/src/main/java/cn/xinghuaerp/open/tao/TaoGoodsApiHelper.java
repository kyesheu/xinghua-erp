package cn.xinghuaerp.open.tao;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.*;
import cn.xinghuaerp.open.tao.response.TaoGoodsResponse;
import cn.xinghuaerp.open.tao.response.TaoGoodsSkuResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.DateUtil;
import cn.xinghuaerp.open.common.SignUtil;

public class TaoGoodsApiHelper {
    private static Logger log = LoggerFactory.getLogger(TaoGoodsApiHelper.class);
    private static final String LIST_FIELDS = "approve_status,num_iid,title,nick,type,cid,pic_url,num,props,props_name,valid_thru,list_time,price,has_discount,has_invoice,has_warranty," +
            "has_showcase,modified,delist_time,postage_id,seller_cids,outer_id,sold_quantity,skus";
    private static final String SKU_FIELDS = "sku_id,iid,num_iid,properties,properties_name,quantity,price,outer_id,created,modified,status,sku_spec_id,barcode";

    public static ApiResultVo<TaoGoodsResponse> pullGoodsList(String appKey, String appSecret, String sessionKey) throws IOException {
        log.info("=======开始全量拉取商品数据{}=========", LocalDateTime.now());
        Integer pageNo = 1;
        String resultString = pullGoodsList(pageNo, appKey, appSecret, sessionKey);
        if (!StringUtils.isNotBlank(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名发生错误");

        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult = JsonUtils.node(result, "items_onsale_get_response");
            int totalResults = JsonUtils.integer(dataResult, "total_results");
            // 计算总页数
            int totalPage = (totalResults % 40 == 0)
                    ? totalResults / 40
                    : (totalResults / 40) + 1;

            JsonNode itemListResult = JsonUtils.node(dataResult, "items");
            //组合的订单列表
            List<TaoGoodsResponse> taoGoodsResponseList = new ArrayList<>();

            if (itemListResult != null) {
                for (Object item : JsonUtils.node(itemListResult, "item")) {
                    TaoGoodsResponse bean = JsonUtils.readValue(item.toString(), TaoGoodsResponse.class);
                    // 查询sku list
                    ApiResultVo<TaoGoodsSkuResponse> goodsItemSkuApiResultVo = pullSku(bean.getNum_iid(), appKey, appSecret, sessionKey);
                    if (goodsItemSkuApiResultVo.getCode() == ApiResultVoEnum.SUCCESS.getIndex()) {
                        bean.setSkuList(goodsItemSkuApiResultVo.getList());
                    }
                    taoGoodsResponseList.add(bean);
                }
                if (totalPage > 1) {
                    //循环取下一页
                    while (pageNo <= totalPage) {
                        pageNo++;
                        String resultString1 = pullGoodsList(pageNo, appKey, appSecret, sessionKey);
                        JsonNode result1 = JsonUtils.readTree(resultString1);
                        //没有错误
                        JsonNode dataResult1 = JsonUtils.node(result1, "items_onsale_get_response");

                        JsonNode orderListResult1 = JsonUtils.node(dataResult1, "items");
                        if (orderListResult1 != null) {
                            for (Object item : JsonUtils.node(orderListResult1, "item")) {
                                TaoGoodsResponse bean = JsonUtils.readValue(item.toString(), TaoGoodsResponse.class);
                                // 查询sku list
                                ApiResultVo<TaoGoodsSkuResponse> goodsItemSkuApiResultVo = pullSku(bean.getNum_iid(), appKey, appSecret, sessionKey);
                                if (goodsItemSkuApiResultVo.getCode() == ApiResultVoEnum.SUCCESS.getIndex()) {
                                    bean.setSkuList(goodsItemSkuApiResultVo.getList());
                                }
                                taoGoodsResponseList.add(bean);
                            }
                        }
                    }
                }
//                return new PullResult(tradeBeans.size(), tradeBeans);
                return ApiResultVo.success(taoGoodsResponseList.size(), taoGoodsResponseList);
            } else {
//                return new PullResult(0, tradeBeans);
                return ApiResultVo.success(0, taoGoodsResponseList);
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

    protected static String pullGoodsList(Integer pageNo, String appKey, String appSecret, String sessionKey) throws IOException {
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.items.onsale.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");

        params.put("page_no", pageNo.toString());
        params.put("fields", LIST_FIELDS);
        params.put("page_size", "40");
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
//        String result = HttpUtils.doGet(url);
        String result = OkHttpClientHelper.get(url);
//        TaoGoodsApiService remoting = RemoteUtil.Remoting(url, TaoGoodsApiService.class);
//        String result = remoting.getGoodsList();
        return result;
    }

    protected static ApiResultVo<TaoGoodsSkuResponse> pullSku(Long numIid, String appKey, String appSecret, String sessionKey) throws IOException {
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.item.skus.get");
        params.put("v", "2.0");
        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");

        params.put("fields", SKU_FIELDS);
        params.put("num_iids", numIid.toString());
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
//        TaoGoodsApiService remoting = RemoteUtil.Remoting(url, TaoGoodsApiService.class);
//        String resultString = remoting.getGoodsList();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);
        if (!StringUtils.isNotBlank(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名发生错误");

        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult = JsonUtils.node(result, "item_skus_get_response");
            JsonNode itemListResult = JsonUtils.node(dataResult, "skus");
            //组合的订单列表
            List<TaoGoodsSkuResponse> skuList = new ArrayList<>();

            if (itemListResult != null) {
                if (itemListResult.get("sku") != null) {
                    skuList = JsonUtils.readList(itemListResult.get("sku").toString(), TaoGoodsSkuResponse.class);
                }
//                for (Object item : JsonUtils.node(itemListResult, "sku")) {
//                    GoodsItemSku bean = JsonNode.parseObject(item.toString(), GoodsItemSku.class);
//                    // 查询sku list
//                    skuList.add(bean);
//                }
                return ApiResultVo.success(skuList.size(), skuList);
            } else {
//                return new PullResult(0, tradeBeans);
                return ApiResultVo.success(0, new ArrayList<>());
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

}