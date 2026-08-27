package cn.xinghuaerp.open.xhs;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.common.ApiResultVoEnum;
import cn.xinghuaerp.open.xhs.response.AfterSaleInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class xhsRefundApiService {

    public ApiResultVo<AfterSaleInfoResponse> pullRefundList(String appId, String appSecret, String accessToken, LocalDateTime startTime, LocalDateTime  endTime) throws IOException {
        List<AfterSaleInfoResponse> productList = new ArrayList<>();
        Integer pageIndex = 1;
        Integer pageSize = 30;
        Integer total = 0;
        String listResult = xhsRefundApiHelper.pullRefundList(appId,appSecret,accessToken,startTime,endTime, pageIndex, pageSize);
        if(StringUtils.hasText(listResult)){
            JsonNode jsonObject = JsonUtils.readTree(listResult);
            if(JsonUtils.bool(jsonObject, "success")) {
                log.info("==========获取小红书订单LIST成功=============");
                JsonNode data = JsonUtils.node(jsonObject, "data");
                total = JsonUtils.integer(data, "totalCount");
                JsonNode dataList = JsonUtils.node(data, "afterSaleBasicInfos");
                if (dataList != null && dataList.size() > 0) {
                    for (int i = 0; i < dataList.size(); i++) {
                        AfterSaleInfoResponse refund = JsonUtils.convert(dataList, i, AfterSaleInfoResponse.class);
                        log.info("=======组装小红书售后===========");

                        String detailResult = xhsRefundApiHelper.getRefundDetail(appId,appSecret,accessToken,refund.getReturnsId());
                        JsonNode detailResultObj = JsonUtils.readTree(detailResult);
                        if(JsonUtils.bool(detailResultObj, "success")) {
                            refund = JsonUtils.convert(JsonUtils.node(detailResultObj, "data"), "afterSaleInfo", AfterSaleInfoResponse.class);
                            String orderDetail = xhsOrderApiHelper.getOrderDetail(appId,appSecret,accessToken,refund.getOrderId());
                            Integer orderStatus = JsonUtils.integer(JsonUtils.node(JsonUtils.readTree(orderDetail), "data"), "orderStatus");
                            refund.setOrderStatus(orderStatus);
                            productList.add(refund);
                        }else{
                            return ApiResultVo.error(JsonUtils.text(detailResultObj, "error_msg"));
                        }



                    }

                }
            } else {
                if(JsonUtils.integer(jsonObject, "error_code") == 401) {
                    return ApiResultVo.error( ApiResultVoEnum.TokenFail.getIndex(), JsonUtils.text(jsonObject, "error_msg"));
                }else return ApiResultVo.error(JsonUtils.text(jsonObject, "error_msg"));
            }
        }else{
            return ApiResultVo.error("请求接口失败");
        }

        return ApiResultVo.success(total, productList);
    }

}
