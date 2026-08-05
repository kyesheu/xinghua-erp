package cn.xinghuaerp.purchase.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author qilip
* @description 针对表【o_goods_supplier】的数据库操作Service
* @createDate 2024-09-07 16:35:43
*/
public interface ErpSupplierService extends IService<ErpSupplier> {
    PageResult<ErpSupplier> queryPageList(ErpSupplier bo, PageQuery pageQuery);
    ErpSupplier getByLoginName(String loginName);

    /**
     * 设置供应商登录账户（登录名 + 密码）。
     *
     * <p>校验：供应商必须存在且未删除；登录名不得与其他<b>未删除</b>供应商重复；
     * 密码使用 BCrypt 加密后落库（表内 {@code login_pwd} 为 varchar(100)，可容纳 60 位哈希）。</p>
     *
     * <p>说明：当前系统尚无供应商门户登录消费方，此处选择 BCrypt 而非明文存储，
     * 与商户 {@code setLoginName} 的明文实现<b>刻意不同</b>——后续若建供应商登录流程，
     * 应按 BCrypt 校验，不要复制商户的明文比较。</p>
     *
     * @param id       供应商ID
     * @param loginName 登录名（调用方保证非空白）
     * @param loginPwd  明文密码（调用方保证非空白；方法内加密后存储）
     * @param updateBy  操作人
     */
    void setLoginName(Long id, String loginName, String loginPwd, String updateBy);

}
