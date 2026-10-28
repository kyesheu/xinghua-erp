package cn.xinghuaerp.system.facade;

import cn.xinghuaerp.system.entity.SysMessage;

/** 系统域消息写入契约；dedupKey 非空时由数据库唯一键保证幂等。 */
public interface MessageFacade {
    void saveMessage(SysMessage message);
}
