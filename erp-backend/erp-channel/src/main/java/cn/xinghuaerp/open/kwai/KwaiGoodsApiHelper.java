package cn.xinghuaerp.open.kwai;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.*;
import cn.xinghuaerp.open.kwai.model.KwaiGoodsItem;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.SignMethodEnum;
import cn.xinghuaerp.open.common.SignUtils;

public class KwaiGoodsApiHelper {


    public static ApiResultVo<KwaiGoodsItem> pullGoodsAll(String appKey,String appSecret,String signSecret,String token) throws IOException {
        //https://open.kwaixiaodian.com/oauth/authorize?appId=ks700872692254768517&redirect_uri=xxx&scope=xxx&response_type=code&state=xxx
        Integer pageNumber = 1;
        JsonNode result = pullGoodsList(pageNumber,appKey,appSecret,signSecret,token);
        List<KwaiGoodsItem> goodsList = new ArrayList<>();

        if (JsonUtils.integer(result, "result") == 1) {
            // 成功
            JsonNode data = JsonUtils.node(result, "data");
            Integer totalPage = Integer.parseInt(JsonUtils.text(data, "totalPage"));
            List<KwaiGoodsItem> items = JsonUtils.readList(JsonUtils.write(data.get("items")), KwaiGoodsItem.class);
            if(items!=null && items.size()>0) {
                goodsList.addAll(items);
            }

            while (totalPage>pageNumber) {
                try {
                    pageNumber++;
                    JsonNode result1 = pullGoodsList(pageNumber, appKey, appSecret, signSecret, token);
                    if (JsonUtils.integer(result1, "result") == 1) {
                        JsonNode data1 = JsonUtils.node(result1, "data");
                        List<KwaiGoodsItem> items1 = JsonUtils.readList(JsonUtils.write(data1.get("items")), KwaiGoodsItem.class);
                        if (items1 != null && items1.size() > 0) {
                            goodsList.addAll(items1);
                        }
                    }
                }catch (Exception e){
                }
            }
            return ApiResultVo.success(goodsList);
        } else {
            // 失败
            return ApiResultVo.error(JsonUtils.integer(result, "result"), JsonUtils.text(result, "error_msg"));
        }
    }
    protected static JsonNode pullGoodsList(Integer pageNumber,String appKey ,String appSecret,String signSecret,String token ) throws IOException {
        String serverUrl = "https://openapi.kwaixiaodian.com";
        Map<String, String> params = new HashMap<>();
        params.put("appkey", appKey);
//        params.put("version", "1");
        params.put("access_token", token);
//        params.put("timestamp", "1714528708000");
        params.put("method", "open.item.list.get");
//        params.put("signMethod", SignMethodEnum.MD5.toString());

        Map<String, Object> p = new HashMap<>();
        p.put("pageNumber", pageNumber);
        p.put("pageSize", 10);
        String jsonString = JsonUtils.write(p);
        params.put("param", jsonString);
        try {
            String signParam = SignUtils.sign(params, signSecret, SignMethodEnum.MD5);
            params.put("sign", signParam);
        } catch (Exception e) {
            e.printStackTrace();
        }

        serverUrl += "/open/item/list/get";
        // 组合url参数
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> joiner.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        String urlP = joiner.toString();
        serverUrl = serverUrl + "?" + urlP;

        // 调用接口
//        KwaiGoodsApiService remoting = RemoteUtil.Remoting(serverUrl, KwaiGoodsApiService.class);
//        JsonNode resultString = remoting.getGoodsList(params);
//        JsonNode result = remoting.getGoodsList(params);
//        return result;

//        String resultString = HttpUtils.doGet(serverUrl);
        String resultString = OkHttpClientHelper.get(serverUrl);
        return JsonUtils.readTree(resultString);
    }




}
