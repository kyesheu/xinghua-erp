package cn.xinghuaerp.common.exception;

/**
 * 验证码错误异常类
 * 
 * @author xinghua
 */
public class CaptchaException extends UserException
{
    private static final long serialVersionUID = 1L;

    public CaptchaException()
    {
        super("user.jcaptcha.error", null, "验证码错误");
    }
}
