package cn.xinghuaerp.open.wei;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.common.OkHttpClientHelper;
import cn.xinghuaerp.open.wei.vo.ewaybill.DeliveryVo;
import tools.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeiLogisticsApiHelper {

    public static ApiResultVo<DeliveryVo> getLogisticsCompanyList(String accessToken)  {
        String serverUrl = "https://api.weixin.qq.com";
        Map<String,Object> params = new HashMap<>();

        try {
            ObjectMapper objectMapper = JsonUtils.mapper();
            String url =ServerUrl.serverApiUrl + ServerUrl.logisticsApiUrl + "?access_token=" + accessToken;
//            HttpResponse<String> result = HttpUtils.doPostJson(ServerUrl.serverApiUrl + ServerUrl.goodsDetailApiUrl + "?access_token=" + accessToken, objectMapper.writeValueAsString(params));
            String result= OkHttpClientHelper.post(url,objectMapper.writeValueAsString(params));
            JsonNode jsonObject = JsonUtils.readTree(result);
            if(JsonUtils.integer(jsonObject, "errcode")!=0){
                return ApiResultVo.error(1500,JsonUtils.text(jsonObject, "errmsg"));
            }

            List<DeliveryVo> list = JsonUtils.list(jsonObject, "company_list", DeliveryVo.class);

            return ApiResultVo.success(list);
        }catch (Exception e){
            return ApiResultVo.error(ApiResultVoEnum.ApiException, e.getMessage());
        }

    }

}
