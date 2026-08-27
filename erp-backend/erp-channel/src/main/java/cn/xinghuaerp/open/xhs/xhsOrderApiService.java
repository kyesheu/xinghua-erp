package cn.xinghuaerp.open.xhs;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.xhs.response.OrderItem;
import cn.xinghuaerp.open.xhs.response.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class xhsOrderApiService {

    public ApiResultVo<OrderResponse> pullOrderList(String appId, String appSecret, String accessToken, LocalDateTime startTime, LocalDateTime  endTime) throws IOException {
        List<OrderResponse> productList = new ArrayList<>();
        Integer pageIndex = 1;
        Integer pageSize = 30;
        Integer total = 0;
        Integer maxPageNo = 1;
        String listResult = xhsOrderApiHelper.pullOrderList(appId,appSecret,accessToken,startTime,endTime, pageIndex, pageSize);
        log.info("=======接口返回：{}",listResult);
        if(StringUtils.hasText(listResult)){
            JsonNode jsonObject = JsonUtils.readTree(listResult);
            if(JsonUtils.bool(jsonObject, "success")) {
                log.info("==========获取小红书订单LIST成功=============");
                JsonNode data = JsonUtils.node(jsonObject, "data");
                total = JsonUtils.integer(data, "total");
                maxPageNo = JsonUtils.integer(data, "maxPageNo");

                JsonNode dataList = JsonUtils.node(data, "orderList");
                if (dataList != null && dataList.size() > 0) {
                    for (int i = 0; i < dataList.size(); i++) {
                        OrderResponse order = JsonUtils.convert(dataList, i, OrderResponse.class);
                        log.info("=======组装小红书订单===========");

                        String detailResult = xhsOrderApiHelper.getOrderDetail(appId,appSecret,accessToken,order.getOrderId());
                        JsonNode detailResultObj = JsonUtils.readTree(detailResult);
                        if(JsonUtils.integer(detailResultObj, "error_code") == 0) {
                            order = JsonUtils.convert(detailResultObj, "data", OrderResponse.class);
                            List<OrderItem> skuList = JsonUtils.list(JsonUtils.node(detailResultObj, "data"), "skuList", OrderItem.class);
                            order.setItemList(skuList);

                            log.info("=======组装小红书订单ITEM=======开始获取订单收货地址====");
                            String receiverResult = xhsOrderApiHelper.getOrderReceiverInfo(appId,appSecret,accessToken,order.getOrderId(),order.getOpenAddressId());
                            log.info("=======收货地址：{}",receiverResult);
                            JsonNode receiverResultObj = JsonUtils.readTree(receiverResult);
                            if(JsonUtils.integer(receiverResultObj, "error_code") == 0) {
                                // 补充收货信息
                                JsonNode jsonArray = JsonUtils.node(JsonUtils.node(receiverResultObj, "data"), "receiverInfos");
                                order.setReceiver(JsonUtils.text(JsonUtils.node(jsonArray, 0), "receiverName"));
                                order.setPhone(JsonUtils.text(JsonUtils.node(jsonArray, 0), "receiverPhone"));
                                order.setAddress(JsonUtils.text(JsonUtils.node(jsonArray, 0), "receiverAddress"));
                            }
                            productList.add(order);
                        }else{
                            return ApiResultVo.error(JsonUtils.text(detailResultObj, "error_msg"));
                        }

                    }
                }
            }
            else {
                return ApiResultVo.error(JsonUtils.text(jsonObject, "error_msg"));
            }
        }else{
            return ApiResultVo.error("获取商品list失败");
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
//                    apiBo1.setProduct_id(productId.toString());
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
//                                apiBo1.setProduct_id(productId.toString());
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

        return ApiResultVo.success(total, productList);
    }

    /**
     * product.getDetailSkuList
     * 商品列表完整版
     * @param appId
     * @param appSecret
     * @param accessToken
     * @return
     * @throws IOException
     */
    public ApiResultVo<OrderResponse> getOrderDetail(String appId,String appSecret,String accessToken, String orderId) throws IOException {

        String listResult = xhsOrderApiHelper.getOrderDetail(appId,appSecret,accessToken, orderId);
        if(StringUtils.hasText(listResult)){
            JsonNode jsonObject = JsonUtils.readTree(listResult);
            if(JsonUtils.bool(jsonObject, "success")) {
                log.info("==========获取小红书商品LIST成功=============");
                OrderResponse orderResponse = JsonUtils.convert(jsonObject, "data", OrderResponse.class);

                List<OrderItem> skuList = JsonUtils.list(JsonUtils.node(jsonObject, "data"), "skuList", OrderItem.class);
                orderResponse.setItemList(skuList);
                return ApiResultVo.success(orderResponse);
            }
            else {
                return ApiResultVo.error(ApiResultVoEnum.ApiException,JsonUtils.text(jsonObject, "error_msg"));
            }
        }else{
            return ApiResultVo.error(ApiResultVoEnum.ApiException,"获取商品list失败");
        }
    }
}
