package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.order.entity.ShopOrderItem;
import cn.xinghuaerp.order.query.ShopOrderQueryBo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【oms_shop_order_item】的数据库操作Service
* @createDate 2025-07-15 11:36:42
*/
public interface ShopOrderItemService extends IService<ShopOrderItem> {
    PageResult<ShopOrderItem> queryPageList(ShopOrderQueryBo bo, PageQuery pageQuery);
}
