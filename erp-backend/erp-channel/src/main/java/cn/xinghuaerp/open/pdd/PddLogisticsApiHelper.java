package cn.xinghuaerp.open.pdd;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.common.PDDSignGenerator;
import cn.xinghuaerp.open.pdd.model.LogisticsCompany;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PddLogisticsApiHelper {

    public static ApiResultVo<LogisticsCompany> getLogisticsCompany(String clientId, String clientSecret) throws IOException {

        String resultString = getLogisticsCompanyStr(clientId, clientSecret);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名发生错误");

        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //没有错误
            JsonNode dataResult =JsonUtils.node(result, "logistics_companies_get_response");
            List<LogisticsCompany> list =  JsonUtils.list(dataResult, "logistics_companies", LogisticsCompany.class);

            if (list != null) {
                return ApiResultVo.success(list.size(),list);
            } else {
                return ApiResultVo.success(0,new ArrayList<>());
            }

        } else {
            // 有错误

            JsonNode errorInfo = JsonUtils.node(result, "error_response");
//            if (JsonUtils.text(errorInfo, "code").equals("27")) {
//                // SessionKey非法
//                return ApiResultVo.error(ApiResultVoEnum.TokenFail.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
//            } else if (JsonUtils.text(errorInfo, "code").equals("25")) {
//                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "签名错误，有可能是appSecret错了");
//            } else if (JsonUtils.text(errorInfo, "code").equals("29")) {
//                return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "错误的AppKey");
//            } else {
//                return ApiResultVo.error(ApiResultVoEnum.ParamsError.getIndex(), JsonUtils.text(errorInfo, "sub_msg"));
//            }
            return ApiResultVo.error(JsonUtils.integer(errorInfo, "error_code"), JsonUtils.text(errorInfo, "error_msg"));
        }
    }

    protected static String getLogisticsCompanyStr(String clientId, String clientSecret) throws IOException {
        String url = "https://gw-api.pinduoduo.com/api/router"; // API的URL

        Map<String, String> params = new HashMap<>();
        params.put("type", "pdd.logistics.companies.get");
        params.put("client_id", clientId);
        params.put("timestamp", System.currentTimeMillis()/1000 +"");



        try {
            String sign = PDDSignGenerator.generateSign(params, clientSecret);
            params.put("sign", sign);
        } catch (Exception e) {
            return "";//签名错误
        }

        // 调用接口
//        PddWaybillApiService remoting = RemoteUtil.Remoting(url, PddWaybillApiService.class);
        String jsonString = JsonUtils.write(params);
//        String result = remoting.getWaybillAccount(jsonString);
//        System.out.println(result);
//        return result;
//        HttpResponse<String> stringHttpResponse = HttpUtils.doPost(url, jsonString);
//        return stringHttpResponse.body();
        return OkHttpClientHelper.post(url,jsonString);
    }
}
