package cn.xinghuaerp.order.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.order.entity.ShopMember;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【oms_shop_member】的数据库操作Service
* @createDate 2025-07-18 16:27:49
*/
public interface ShopMemberService extends IService<ShopMember> {
    PageResult<ShopMember> queryPageList(ShopMember bo, PageQuery pageQuery);
    ShopMember querySingleBy(String mobile,String name);
    ResultVo<Long> addShopMember(ShopMember shopMember);
}
