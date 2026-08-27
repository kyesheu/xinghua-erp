package cn.xinghuaerp.open.tao;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.*;
import cn.xinghuaerp.open.tao.model.TaoLogisticsCompany;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.DateUtil;
import cn.xinghuaerp.open.common.SignUtil;

public class TaoLogisticsApiHelper {

    /**
     *
     * @param
     * @param appKey
     * @param appSecret
     * @param
     * @return
     * @throws
     */
    public static ApiResultVo<TaoLogisticsCompany> getLogisticsCompany(String appKey, String appSecret) throws IOException {

        String resultString = getLogisticsCompanyStr( appKey, appSecret);
        if (!StringUtils.hasText(resultString))
            return ApiResultVo.error(ApiResultVoEnum.SystemException.getIndex(), "接口调用错误");
        // 获取结果
        JsonNode result = JsonUtils.readTree(resultString);
        if (result.get("error_response") == null) {
            //组合的订单列表

            List<TaoLogisticsCompany> logisticsCompanies = JsonUtils.list(JsonUtils.node(JsonUtils.node(result, "logistics_companies_get_response"), "logistics_companies"), "logistics_company", TaoLogisticsCompany.class);


            return ApiResultVo.success(logisticsCompanies);
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

    protected static String getLogisticsCompanyStr(String appKey, String appSecret) throws IOException {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String url = "https://api.taobao.com/router/rest"; // 淘宝API的URL
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("method", "taobao.logistics.companies.get");
        params.put("v", "2.0");
//        params.put("session", sessionKey);
        params.put("timestamp", DateUtil.getCurrentDateTime());
        params.put("format", "json");
        params.put("sign_method", "md5");
        params.put("fields", "id,code,name,reg_mail_no");

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
//        TaoRefundApiService remoting = RemoteUtil.Remoting(url, TaoRefundApiService.class);
//        String result = remoting.getRefundList();
//        String resultString = HttpUtils.doGet(url);
        String resultString = OkHttpClientHelper.get(url);
        return resultString;
    }

}
