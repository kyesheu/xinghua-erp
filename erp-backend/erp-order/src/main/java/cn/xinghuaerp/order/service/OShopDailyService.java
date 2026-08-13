package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.inventory.query.ShopDailyRequest;
import cn.xinghuaerp.system.entity.OShopDaily;
import cn.xinghuaerp.order.vo.GoodsSaleReport;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author qilip
* @description 针对表【o_shop_daily(店铺日报)】的数据库操作Service
* @createDate 2025-02-11 16:37:22
*/
public interface OShopDailyService extends IService<OShopDaily> {
    ResultVo<Long> saveShopDaily(OShopDaily bo);
    ResultVo<Long> delShopDaily(Long id);
    ResultVo<Long> updateShopDaily(OShopDaily bo);
    List<OShopDaily> searchList(ShopDailyRequest request);
    List<GoodsSaleReport> goodsSaleReport(ShopDailyRequest request);
    List<GoodsSaleReport> goodsSaleRegionReport(ShopDailyRequest request);

    List<OShopDaily> shopRegionList(ShopDailyRequest request);
}
