package cn.xinghuaerp.system.service.impl;

import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.ISysMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageServiceImpl implements ISysMessageService {

    private final SysMessageMapper mapper;

    @Override
    public void save(SysMessage msg) {
        if (msg.getCreatedTime() == null) {
            msg.setCreatedTime(LocalDateTime.now());
        }
        if (msg.getId() != null) {
            mapper.updateById(msg);
        } else {
            try {
                if (mapper.insert(msg) != 1) throw new IllegalStateException("消息保存失败");
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (msg.getDedupKey() == null || mapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                        .eq(SysMessage::getDedupKey, msg.getDedupKey())) == 0) throw e;
            }
        }
    }

    @Override
    public List<SysMessage> getUnread() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getIsRead, 0);
        w.orderByDesc(SysMessage::getCreatedTime, SysMessage::getId);
        w.last("LIMIT 100");
        return mapper.selectList(w);
    }

    @Override
    public long countUnread() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getIsRead, 0);
        return mapper.selectCount(w);
    }

    @Override
    public boolean existsByTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getTitle, title);
        return mapper.selectCount(w) > 0;
    }

    @Override
    public void markRead(Long id) {
        SysMessage m = mapper.selectById(id);
        if (m != null) {
            m.setIsRead(1);
            m.setReadTime(LocalDateTime.now());
            mapper.updateById(m);
        }
    }

    @Override
    public List<SysMessage> getFailedNotify() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getNeedNotify, 1);
        var cutoff=LocalDateTime.now().minusMinutes(10);
        w.in(SysMessage::getNotifyStatus, 0, 2);
        w.and(query->query.lt(SysMessage::getNotifyTime,cutoff)
                .or(pending->pending.isNull(SysMessage::getNotifyTime).lt(SysMessage::getCreatedTime,cutoff)));
        w.orderByAsc(SysMessage::getNotifyTime);
        w.last("LIMIT 20");
        return mapper.selectList(w);
    }

    @Override
    public void markAllRead() {
        LambdaQueryWrapper<SysMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysMessage::getIsRead, 0);
        SysMessage update = new SysMessage();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        mapper.update(update, w);
    }
}
