package cn.xinghuaerp.open.pdd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.*;
import cn.xinghuaerp.open.pdd.model.Goods;
import cn.xinghuaerp.open.pdd.model.GoodsResultVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.PDDSignGenerator;

public class PddGoodsApiHelper {
    private static Logger log = LoggerFactory.getLogger(PddGoodsApiHelper.class);

    /**
     * 拉取商品列表
     * @param clientId
     * @param clientSecret
     * @param accessToken
     * @param pageIndex
     * @param pageSize
     * @return
     */

    public static ApiResultVo<GoodsResultVo> pullGoodsList(String clientId, String clientSecret, String accessToken, Integer pageIndex, Integer pageSize) throws IOException {

        String result = pullGoodsListResult(clientId, clientSecret, accessToken,pageIndex,pageSize);
        if (!StringUtils.hasText(result)) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口请求错误");

        JsonNode jsonObject = JsonUtils.readTree(result);
        if(JsonUtils.text(jsonObject, "error_response") !=null)
            return ApiResultVo.error(JsonUtils.integer(JsonUtils.node(jsonObject, "error_response"), "error_code"), JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "error_msg"));

        if(JsonUtils.node(jsonObject, "goods_list_get_response") == null) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口返回数据错误");
        Integer totalItem = JsonUtils.integer(JsonUtils.node(jsonObject, "goods_list_get_response"), "total_count");

        List<Goods> goodsList = JsonUtils.readList(JsonUtils.text(JsonUtils.node(jsonObject, "goods_list_get_response"), "goods_list"), Goods.class);
        return ApiResultVo.success(new GoodsResultVo(totalItem,goodsList));
    }

    protected static String pullGoodsListResult(String clientId, String clientSecret, String accessToken,Integer pageIndex,Integer pageSize) throws IOException {
        String url = "https://gw-api.pinduoduo.com/api/router";
        Map<String, String> params = new HashMap();
        params.put("type", "pdd.goods.list.get");
        params.put("client_id", clientId);
        params.put("access_token", accessToken);
        long var10002 = System.currentTimeMillis();
        params.put("timestamp", "" + var10002 / 1000L);
        params.put("page", "" + pageIndex);
        params.put("page_size", "" + pageSize);

        try {
            String sign = PDDSignGenerator.generateSign(params, clientSecret);
            params.put("sign", sign);
        } catch (Exception var9) {
            return "";
        }

        // 调用接口

//        PddGoodsApiService remoting = RemoteUtil.Remoting(url, PddGoodsApiService.class);
        String jsonString = JsonUtils.write(params);
//        String result = remoting.getGoodsList(jsonString);
//        return result;
//        HttpResponse<String> stringHttpResponse1 = ExpressClient.doPost(url, jsonString);
//        String resultStr = stringHttpResponse1.body();
        String post = OkHttpClientHelper.post(url, jsonString);
        return post;
//        HttpResponse<String> stringHttpResponse = HttpUtils.doPost(url, jsonString);
//        return stringHttpResponse.body();
    }


}