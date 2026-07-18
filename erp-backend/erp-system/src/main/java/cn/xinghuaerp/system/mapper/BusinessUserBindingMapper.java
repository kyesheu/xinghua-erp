package cn.xinghuaerp.system.mapper;

import cn.xinghuaerp.system.entity.BusinessUserBinding;
import cn.xinghuaerp.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BusinessUserBindingMapper extends BaseMapper<BusinessUserBinding> {
    /** 每次解析读取当前账号状态，避免身份变更后只依赖登录缓存。 */
    @Select("SELECT user_id AS userId, user_type AS userType, status, del_flag AS delFlag FROM sys_user WHERE user_id = #{id}")
    SysUser selectCurrentUser(@Param("id") Long id);
    @Select("SELECT user_id AS userId, user_type AS userType, status, del_flag AS delFlag FROM sys_user WHERE user_id = #{id} FOR UPDATE")
    SysUser lockCurrentUser(@Param("id") Long id);
}
