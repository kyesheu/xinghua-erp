package cn.xinghuaerp.common.exception;

/**
 * 用户不存在异常类
 *
 * @author xinghua
 */
public class UserNotExistsException extends UserException
{
    private static final long serialVersionUID = 1L;

    public UserNotExistsException()
    {
        super("user.not.exists", null, "用户不存在");
    }
}
