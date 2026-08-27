package cn.xinghuaerp.open.xhs;

import tools.jackson.databind.JsonNode;
import cn.xinghuaerp.json.JsonUtils;
import cn.xinghuaerp.open.common.ApiResultVo;
import cn.xinghuaerp.open.xhs.response.GoodsItemInfo;
import cn.xinghuaerp.open.xhs.response.GoodsItemSku;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
@Slf4j
@Component
public class xhsGoodsApiService {

    public ApiResultVo<GoodsItemInfo> pullGoodsItemList(String appId,String appSecret, String accessToken) throws IOException {
        List<GoodsItemInfo> productList = new ArrayList<>();
        Integer pageIndex = 1;
        Integer pageSize = 30;
        Integer total = 0;
        String listResult = xhsGoodsApiHelper.pullGoodsItemList(appId,appSecret,accessToken, pageIndex, pageSize);
        if(StringUtils.hasText(listResult)){
            JsonNode jsonObject = JsonUtils.readTree(listResult);
            if(JsonUtils.bool(jsonObject, "success")) {
                log.info("==========获取小红书商品LIST成功=============");
                JsonNode data = JsonUtils.node(jsonObject, "data");
                total = JsonUtils.integer(data, "total");

                JsonNode dataList = JsonUtils.node(data, "itemDetailV3s");
                if (dataList != null && dataList.size() > 0) {
                    for (int i = 0; i < dataList.size(); i++) {
                        GoodsItemInfo itemInfo = JsonUtils.convert(dataList, i, GoodsItemInfo.class);
                        log.info("=======组装小红书商品===========");
                        String itemResult = xhsGoodsApiHelper.pullGoodsItemInfo(appId,appSecret,accessToken,itemInfo.getId());
                        JsonNode itemResultObj = JsonUtils.readTree(itemResult);
                        if(JsonUtils.integer(itemResultObj, "error_code") == 0) {

                            itemInfo = JsonUtils.convert(JsonUtils.node(itemResultObj, "data"), "itemInfo", GoodsItemInfo.class);
                            List<GoodsItemSku> skuList = JsonUtils.list(JsonUtils.node(itemResultObj, "data"), "skuInfos", GoodsItemSku.class);
                            itemInfo.setSkus(skuList);
                            productList.add(itemInfo);
                            log.info("=======组装小红书商品ITEM===========");
                        }else{
                            return ApiResultVo.error(JsonUtils.text(itemResultObj, "error_msg"));
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
        return ApiResultVo.success(total, productList);
    }

    /**
     *
     * 商品详情
     * @param appId
     * @param appSecret
     * @param accessToken
     * @return
     * @throws IOException
     */
    public ApiResultVo<GoodsItemInfo> getGoodsItemInfo(String appId,String appSecret,String accessToken, String itemId) throws IOException {
        GoodsItemInfo itemInfo = new GoodsItemInfo();

        String itemResult = xhsGoodsApiHelper.pullGoodsItemInfo(appId,appSecret,accessToken,itemId);
        JsonNode itemResultObj = JsonUtils.readTree(itemResult);

        if(JsonUtils.integer(itemResultObj, "error_code") == 0) {

            itemInfo = JsonUtils.convert(JsonUtils.node(itemResultObj, "data"), "itemInfo", GoodsItemInfo.class);

            List<GoodsItemSku> skuList = JsonUtils.list(JsonUtils.node(itemResultObj, "data"), "skuInfos", GoodsItemSku.class);
            itemInfo.setSkus(skuList);

            log.info("=======组装小红书商品ITEM===========");
            return ApiResultVo.success(itemInfo);
        }else{
            return ApiResultVo.error(JsonUtils.text(itemResultObj, "error_msg"));
        }
    }
}
