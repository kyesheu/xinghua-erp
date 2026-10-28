package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.ISysMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageFacadeImpl implements MessageFacade {
    private final ISysMessageService messages;
    @Override
    public void saveMessage(SysMessage message) { messages.save(message); }
}
