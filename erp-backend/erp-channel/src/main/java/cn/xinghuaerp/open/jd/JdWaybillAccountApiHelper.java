package cn.xinghuaerp.open.jd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import tools.jackson.databind.ObjectMapper;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.jd.model.WaybillAccount;
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
import java.util.*;

public class JdWaybillAccountApiHelper {
    private static Logger log = LoggerFactory.getLogger(JdWaybillAccountApiHelper.class);


    public static ApiResultVo<WaybillAccount> getWaybillAccount(String appKey, String appSecret, String accessToken, String vendorCode) {
        // 拉取接口
        String result = pullWaybillAccount(appKey, appSecret, accessToken, vendorCode);

        if (!StringUtils.hasText(result)) return ApiResultVo.error(ApiResultVoEnum.ApiException, "接口请求错误");


        JsonNode jsonObject = JsonUtils.readTree(result);

        if (JsonUtils.node(jsonObject, "error_response") != null)
            return ApiResultVo.error(ApiResultVoEnum.ApiException,JsonUtils.text(JsonUtils.node(jsonObject, "error_response"), "zh_desc"));

        if (JsonUtils.integer(JsonUtils.node(jsonObject, "jingdong_ldop_alpha_provider_sign_success_responce"), "code") != 0)
            return ApiResultVo.error(ApiResultVoEnum.ApiException, JsonUtils.text(jsonObject, "statusMessage"));

        List<WaybillAccount> list = JsonUtils.list(JsonUtils.node(JsonUtils.node(jsonObject, "jingdong_ldop_alpha_provider_sign_success_responce"), "resultInfo"), "data", WaybillAccount.class);


        return ApiResultVo.success(list.size(),list);
    }

    protected static String pullWaybillAccount (String appKey,String appSecret,String accessToken,String vendorCode){
        String url="https://api.jd.com/routerjson";

        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("format", "json");
        params.put("method", "jingdong.ldop.alpha.provider.sign.success");
        params.put("v", "2.0");
        params.put("timestamp", DateUtil.getCurrentDateTime());
        try {

            Map<String, Object> pmap = new TreeMap();
            pmap.put("vendorCode", vendorCode);



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


   }
