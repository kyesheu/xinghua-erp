package cn.xinghuaerp.system.service;

/**
 * 业务主体校验扩展点。
 *
 * <p>系统模块只负责账号与绑定关系的解析，供应商、仓库等主体是否存在、是否停用，
 * 由对应业务模块实现本接口提供，避免系统模块反向依赖业务模块。</p>
 */
public interface BusinessSubjectValidator {

    /** 主体类型：SUPPLIER、WAREHOUSE 等。 */
    String subjectType();

    /** 校验主体存在且可用，不满足时抛出 IllegalArgumentException。 */
    void requireActive(Long subjectId);
}
