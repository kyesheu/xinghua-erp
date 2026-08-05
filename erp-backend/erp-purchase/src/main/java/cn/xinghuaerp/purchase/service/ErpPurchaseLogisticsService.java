package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.purchase.entity.ErpPurchaseLogistics;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【erp_purchase_logistics(采购承运商/物流公司表)】的数据库操作Service
* @createDate 2024-12-08 18:17:15
*/
public interface ErpPurchaseLogisticsService extends IService<ErpPurchaseLogistics> {

    /**
     * 更新承运商启用状态。
     *
     * @param id     承运商主键
     * @param status 目标状态（0禁用 1启用）
     * @return 受影响行数
     */
    int updateStatus(Long id, Integer status);

    /**
     * 判断承运商编码是否已被其他记录占用，用于新增/修改时的重复编码校验。
     *
     * @param code      承运商编码（调用方需保证已去空格且非空）
     * @param excludeId 修改场景需排除的自身主键，新增场景传 {@code null}
     * @return 已存在同编码记录时返回 {@code true}
     */
    boolean existsByCode(String code, Long excludeId);
}
