package cn.xinghuaerp.system.service.impl;

import cn.xinghuaerp.system.mapper.OShopPullLasttimeMapper;
import cn.xinghuaerp.system.service.OShopPullLasttimeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.xinghuaerp.system.entity.OShopPullLasttime;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author TW
* @description 针对表【sys_shop_pull_lasttime(店铺更新最后时间记录)】的数据库操作Service实现
* @createDate 2024-06-04 16:13:03
*/
@AllArgsConstructor
@Service
public class OShopPullLasttimeServiceImpl extends ServiceImpl<OShopPullLasttimeMapper, OShopPullLasttime>
    implements OShopPullLasttimeService {
    private final OShopPullLasttimeMapper mapper;
    private final cn.xinghuaerp.system.mapper.OShopMapper shops;

    @Override
    public OShopPullLasttime getLasttimeByShop(Long shopId, String pullType) {
        List<OShopPullLasttime> oShopPullLasttimes = mapper.selectList(new LambdaQueryWrapper<OShopPullLasttime>().eq(OShopPullLasttime::getShopId, shopId).eq(OShopPullLasttime::getPullType, pullType).orderByDesc(OShopPullLasttime::getLasttime,OShopPullLasttime::getId).last("LIMIT 1"));
        if(oShopPullLasttimes != null && !oShopPullLasttimes.isEmpty()) return oShopPullLasttimes.get(0);
        else return null;
    }

    @Override
    public List<OShopPullLasttime> getLasttimeByShop(Long shopId) {
        List<OShopPullLasttime> list = mapper.selectList(new LambdaQueryWrapper<OShopPullLasttime>()
                .eq(OShopPullLasttime::getShopId, shopId));

        return list;
    }
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public boolean advance(Long shopId,String pullType,java.time.LocalDateTime time) {
        if(shopId==null || shopId<=0 || time==null || !java.util.Set.of("ORDER","REFUND","GOODS").contains(pullType))throw new IllegalArgumentException("拉取进度参数无效");
        if(shops.selectOne(new LambdaQueryWrapper<cn.xinghuaerp.system.entity.OShop>().eq(cn.xinghuaerp.system.entity.OShop::getId,shopId).last("FOR UPDATE"))==null)throw new IllegalStateException("店铺不存在");
        var current=getLasttimeByShop(shopId,pullType);
        if(current!=null && current.getLasttime()!=null && !current.getLasttime().isBefore(time))return true;
        var row=new OShopPullLasttime();row.setShopId(shopId);row.setPullType(pullType);row.setLasttime(time);
        if(current==null){row.setCreateTime(java.time.LocalDateTime.now());return mapper.insert(row)==1;}
        row.setId(current.getId());row.setUpdateTime(java.time.LocalDateTime.now());return mapper.updateById(row)==1;
    }
}




