package cn.xinghuaerp.system.security;


import cn.xinghuaerp.common.exception.UserException;

/**
 * 用户密码不正确或不符合规范异常类
 *
 * @author xinghua
 */
public class UserPasswordNotMatchException extends UserException
{
    private static final long serialVersionUID = 1L;

    public UserPasswordNotMatchException()
    {
        super("user.password.not.match", null, "用户密码错误");
    }
}
