package cn.xinghuaerp.open.wei;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.wei.model.Product;
import tools.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeiGoodsApiHelper {

    /**
     * 获取商品列表（公开方法）
     * @param accessToken
     * @return
     */
    public static ApiResultVo<Product> pullGoodsList(String accessToken)  {
        List<Product> productList = new ArrayList<>();
        Integer pageSize = 30;
        String result = pullGoodsList(accessToken, null, pageSize, null);
        if (!StringUtils.isNotBlank(result)) {
            return ApiResultVo.error(500,"接口返回空");
        }
        JsonNode jsonObject = JsonUtils.readTree(result);
        if(JsonUtils.integer(jsonObject, "errcode")!=0){
            return ApiResultVo.error(JsonUtils.integer(jsonObject, "errcode"),JsonUtils.text(jsonObject, "errmsg"));
        }
        List<String> productIds = JsonUtils.list(jsonObject, "product_ids", String.class);
        if (productIds != null) {
            for(String productId : productIds){
                String detail = pullGoodsDetail(accessToken, productId, null);
                if (StringUtils.isNotBlank(detail)) {
                    JsonNode detailObj = JsonUtils.readTree(detail);
                    if (JsonUtils.integer(detailObj, "errcode") == 0) {
                        Product product = JsonUtils.convert(detailObj, "product", Product.class);
                        if (product != null) {
                            productList.add(product);
                        }
                    }
                }
            }
        }
        // 处理分页
        String nextKey = JsonUtils.text(jsonObject, "next_key");
        boolean hasMore = jsonObject.path("has_more").asBoolean(false);
        while (hasMore && StringUtils.isNotBlank(nextKey)) {
            String pageResult = pullGoodsList(accessToken, null, pageSize, nextKey);
            if (StringUtils.isNotBlank(pageResult)) {
                JsonNode pageObj = JsonUtils.readTree(pageResult);
                if (JsonUtils.integer(pageObj, "errcode") == 0) {
                    List<String> moreIds = JsonUtils.list(pageObj, "product_ids", String.class);
                    if (moreIds != null) {
                        for(String productId : moreIds){
                            String detail = pullGoodsDetail(accessToken, productId, null);
                            if (StringUtils.isNotBlank(detail)) {
                                JsonNode detailObj = JsonUtils.readTree(detail);
                                if (JsonUtils.integer(detailObj, "errcode") == 0) {
                                    Product product = JsonUtils.convert(detailObj, "product", Product.class);
                                    if (product != null) productList.add(product);
                                }
                            }
                        }
                    }
                    nextKey = JsonUtils.text(pageObj, "next_key");
                    hasMore = pageObj.path("has_more").asBoolean(false);
                } else {
                    hasMore = false;
                }
            } else {
                hasMore = false;
            }
        }
        return ApiResultVo.success(productList.size(), productList);
    }
    public static String pullGoodsDetail(String accessToken, String productId, Integer dataType){
        //https://api.weixin.qq.com/channels/ec/product/get?access_token=ACCESS_TOKEN
        //data_type 默认取1 1:获取线上数据 2:获取草稿数据 3:同时获取线上和草稿数据（注意：上架过的商品才有线上数据）
        Map<String,Object> params = new HashMap<>();
        params.put("product_id",productId);
        if(dataType!=null){
            params.put("data_type",dataType);
        }
        try {
            // 创建 ObjectMapper 实例
            ObjectMapper objectMapper = JsonUtils.mapper();
            String url = ServerUrl.serverApiUrl + ServerUrl.goodsDetailApiUrl + "?access_token=" + accessToken;
//            HttpResponse<String> stringHttpResponse = HttpUtils.doPostJson(ServerUrl.serverApiUrl + ServerUrl.goodsDetailApiUrl + "?access_token=" + accessToken, objectMapper.writeValueAsString(params));
//            return stringHttpResponse.body();
            return OkHttpClientHelper.post(url,objectMapper.writeValueAsString(params));
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }


    public static String pullGoodsList(String accessToken, Integer status, Integer pageSize, String nextKey) {
        if(pageSize==null || pageSize<=0){
            pageSize = 30;
        }
        Map<String,Object> params = new HashMap<>();
        params.put("page_size",pageSize);
        if(status!=null){
            params.put("status",status);
        }
        if(StringUtils.isNotBlank(nextKey)){
            params.put("next_key",nextKey);
        }

        //https://api.weixin.qq.com/channels/ec/product/list/get?access_token=ACCESS_TOKEN
        try {
            // 创建 ObjectMapper 实例
            ObjectMapper objectMapper = JsonUtils.mapper();
            String url = ServerUrl.serverApiUrl + ServerUrl.goodsListApiUrl + "?access_token=" + accessToken;
//            HttpResponse<String> stringHttpResponse = HttpUtils.doPostJson(ServerUrl.serverApiUrl + ServerUrl.goodsListApiUrl + "?access_token=" + accessToken, objectMapper.writeValueAsString(params));
//            return stringHttpResponse.body();
            return OkHttpClientHelper.post(url,objectMapper.writeValueAsString(params));
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

}
