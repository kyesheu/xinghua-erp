package cn.xinghuaerp.purchase.service.impl;

import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.system.facade.SystemFacade;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.purchase.service.ErpSupplierService;
import cn.xinghuaerp.common.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author qilip
* @description 针对表【o_goods_supplier】的数据库操作Service实现
* @createDate 2024-09-07 16:35:43
*/
@Slf4j
@AllArgsConstructor
@Service
public class ErpSupplierServiceImpl extends ServiceImpl<ErpSupplierMapper, ErpSupplier>
    implements ErpSupplierService {
    private final ErpSupplierMapper mapper;
    private final SystemFacade merchantMapper;

    @Override
    public ErpSupplier getByLoginName(String loginName) {
        LambdaQueryWrapper<ErpSupplier> eq = new LambdaQueryWrapper<ErpSupplier>().eq(ErpSupplier::getLoginName, loginName);
        List<ErpSupplier> ErpSuppliers = mapper.selectList(eq);
        if(ErpSuppliers.isEmpty()) return null;
        else return ErpSuppliers.get(0);
    }

    @Override
    public PageResult<ErpSupplier> queryPageList(ErpSupplier bo, PageQuery pageQuery) {
        LambdaQueryWrapper<ErpSupplier> queryWrapper = new LambdaQueryWrapper<ErpSupplier>();
        queryWrapper.eq(bo.getMerchantId()!=null, ErpSupplier::getMerchantId,bo.getMerchantId());
        queryWrapper.eq(bo.getShopId()!=null, ErpSupplier::getShopId,bo.getShopId());
        queryWrapper.eq(bo.getIsShipper()!=null, ErpSupplier::getIsShipper,bo.getIsShipper());
        queryWrapper.like(StringUtils.hasText(bo.getName()), ErpSupplier::getName, bo.getName());
        queryWrapper.like(StringUtils.hasText(bo.getNumber()), ErpSupplier::getNumber, bo.getNumber());
        queryWrapper.like(StringUtils.hasText(bo.getLinkMan()), ErpSupplier::getLinkMan, bo.getLinkMan());

        Page<ErpSupplier> pages = mapper.selectPage(pageQuery.build(), queryWrapper);

        return PageResult.build(pages);
    }

    /**
     * 设置供应商登录账户。
     *
     * <p>与商户版 {@code ErpMerchantServiceImpl.setLoginName} 的差异（均为有意为之）：</p>
     * <ul>
     *   <li>失败用 {@link ServiceException} 抛出而非返回错误码，便于控制器统一转可读提示；</li>
     *   <li>登录名唯一性检查排除<b>已删除</b>供应商（已删除账号不占用登录名）；</li>
     *   <li>密码 BCrypt 加密后落库，不存明文。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void setLoginName(Long id, String loginName, String loginPwd, String updateBy) {
        ErpSupplier supplier = mapper.selectById(id);
        if (supplier == null || Integer.valueOf(1).equals(supplier.getIsDelete())) {
            throw new ServiceException("供应商不存在，无法设置登录账户");
        }
        if (loginName != null && !loginName.equals(supplier.getLoginName())) {
            // 仅当登录名变化时查重；已删除供应商不占用登录名
            ErpSupplier existing = mapper.selectOne(new LambdaQueryWrapper<ErpSupplier>()
                    .eq(ErpSupplier::getLoginName, loginName)
                    .eq(ErpSupplier::getIsDelete, 0)
                    .last("limit 1"));
            if (existing != null && !existing.getId().equals(id)) {
                throw new ServiceException("登录账号已存在，请重新输入");
            }
        }
        ErpSupplier up = new ErpSupplier();
        up.setId(id);
        up.setLoginName(loginName);
        up.setLoginPwd(new BCryptPasswordEncoder().encode(loginPwd));
        up.setUpdateBy(updateBy);
        up.setUpdateTime(LocalDateTime.now());
        mapper.updateById(up);
    }
}




