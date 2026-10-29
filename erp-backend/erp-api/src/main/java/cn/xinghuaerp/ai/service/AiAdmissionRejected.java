package cn.xinghuaerp.ai.service;

/** 可安全展示的资源准入失败，与模型服务故障分开处理。 */
public class AiAdmissionRejected extends IllegalStateException {
    public AiAdmissionRejected(String message){super(message);}
}
