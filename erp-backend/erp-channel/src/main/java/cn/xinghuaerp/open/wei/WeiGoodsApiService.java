package cn.xinghuaerp.open.wei;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.wei.model.Product;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeiGoodsApiService {

    public ApiResultVo<Product> pullGoodsList(String accessToken)  {
        String serverUrl = "https://api.weixin.qq.com";
//        ApiResultVo<Token> token = TokenApiHelper.getToken(appId, appSecret);
//        if(token.getCode() != ApiResultVoEnum.SUCCESS.getIndex()) return ApiResultVo.error(ApiResultVoEnum.ApiException,"获取Token失败");
//        String accessToken = token.getData().getAccess_token();

        List<Product> productList = new ArrayList<>();

//        WeiGoodsApiService remoting = RemoteUtil.Remoting(serverUrl, WeiGoodsApiService.class);
        Integer pageSize = 30;
        String listResult = WeiGoodsApiHelper.pullGoodsList(accessToken, 5, pageSize, null);
        if(StringUtils.isNotBlank(listResult)){
            JsonNode jsonObject = JsonUtils.readTree(listResult);
            if(JsonUtils.integer(jsonObject, "errcode") == 0){
                JsonNode productIds = JsonUtils.node(jsonObject, "product_ids");
                if(productIds != null && productIds.size() > 0) {
                    productIds.forEach(productId -> {
                        String detailResult = WeiGoodsApiHelper.pullGoodsDetail(accessToken, JsonUtils.text(productId), null);
                        if (StringUtils.isNotBlank(detailResult)) {
                            JsonNode detailJsonObject = JsonUtils.readTree(detailResult);
                            if (JsonUtils.integer(detailJsonObject, "errcode") == 0) {
                                Product goodsDetail = JsonUtils.readValue(JsonUtils.text(detailJsonObject, "product"), Product.class);
                                productList.add(goodsDetail);
                            }
                        }
                    });
                    // 判断是否还有下一页
                    Integer totalNum = JsonUtils.integer(jsonObject, "total_num");
                    if (totalNum > pageSize) {
                        while (productList.size() < totalNum) {
                            String nextKey = JsonUtils.text(jsonObject, "next_key");
                            String pageResult = WeiGoodsApiHelper.pullGoodsList(accessToken, null, pageSize, nextKey);
                            if (StringUtils.isNotBlank(pageResult)) {
                                JsonNode jsonObject1 = JsonUtils.readTree(pageResult);
                                if(JsonUtils.integer(jsonObject1, "errcode") == 0) {
                                    JsonNode productIds1 = JsonUtils.node(jsonObject1, "product_ids");
                                    if(productIds1 != null && productIds1.size() > 0) {
                                        productIds1.forEach(productId -> {
                                            String detailResult = WeiGoodsApiHelper.pullGoodsDetail(accessToken, JsonUtils.text(productId), null);
                                            if (StringUtils.isNotBlank(detailResult)) {
                                                JsonNode detailJsonObject = JsonUtils.readTree(detailResult);
                                                if (JsonUtils.integer(detailJsonObject, "errcode") == 0) {
                                                    Product goodsDetail = JsonUtils.readValue(JsonUtils.text(detailJsonObject, "product"), Product.class);
                                                    productList.add(goodsDetail);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }


            }else if(JsonUtils.integer(jsonObject, "errcode") == 42001){
                return ApiResultVo.error(ApiResultVoEnum.TokenFail,JsonUtils.text(jsonObject, "errmsg"));
            } else {
                return ApiResultVo.error(ApiResultVoEnum.ApiException,JsonUtils.text(jsonObject, "errmsg"));
            }
        }else{
            return ApiResultVo.error(ApiResultVoEnum.ApiException,"获取商品list失败");
        }
//        int getTotal =0;//获取到了总条数

//        GoodsListApiBo apiBo = new GoodsListApiBo();
//        apiBo.setPage_size(pageSize);
//        apiBo.setStatus(5);
//        //https://api.weixin.qq.com/channels/ec/product/list/get?access_token=ACCESS_TOKEN
//        HttpResponse<String> stringHttpResponse = HttpUtils.doPostJson(ServerUrl.serverApiUrl+ServerUrl.goodsListApiUrl+"?access_token=" + accessToken, JsonNode.toJSONString(apiBo));
//        String body = stringHttpResponse.body();
//        GoodsListVo res = JsonNode.parseObject(body, GoodsListVo.class);
////        GoodsListVo res = remoting.getGoodsList(accessToken, apiBo);
//        if(res.getErrcode() == 0){
//            // 第一页数据
//            if(res.getProduct_ids()!=null&&res.getProduct_ids().length>0){
//                for (var productId:res.getProduct_ids()) {
//                    GoodsDetailApiBo apiBo1 = new GoodsDetailApiBo();
//                    apiBo1.setProduct_id(JsonUtils.text(productId));
//                    GoodsDetailVo goodsDetail = pullGoodsDetail(accessToken,apiBo1.getProduct_id());// remoting.getGoodsDetail(accessToken, apiBo1);
//                    if(goodsDetail.getErrcode()==0){
//                        productList.add(goodsDetail.getProduct());
//                    }
//                }
//            }
//
//            // 如果还有分页
//            if(res.getTotal_num()>pageSize){
//                while (productList.size()<res.getTotal_num()){
//                    String next = res.getNext_key();
//                    apiBo.setNext_key(next);
//                    GoodsListVo res1 = pullGoodsDetail(accessToken,apiBo) remoting.getGoodsList(accessToken, apiBo);
//                    if(res1.getErrcode() == 0){
//                        if(res.getProduct_ids()!=null&&res.getProduct_ids().length>0){
//                            for (var productId:res.getProduct_ids()) {
//                                GoodsDetailApiBo apiBo1 = new GoodsDetailApiBo();
//                                apiBo1.setProduct_id(JsonUtils.text(productId));
//                                GoodsDetailVo goodsDetail = remoting.getGoodsDetail(accessToken, apiBo1);
//                                if(goodsDetail.getErrcode()==0){
//                                    productList.add(goodsDetail.getProduct());
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }else {
//            return ApiResultVo.error(ApiResultVoEnum.ApiException,res.getErrmsg());
//        }

        return ApiResultVo.success(productList.size(), productList);
    }
}
