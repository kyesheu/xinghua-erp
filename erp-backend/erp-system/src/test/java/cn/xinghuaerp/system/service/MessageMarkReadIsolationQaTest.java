package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.service.impl.SysMessageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QA 独立复核：{@code markRead} 的反向丢失更新。
 *
 * <p>原缺陷：{@code selectById} 拿到整行 → 改两列 → {@code updateById} 整实体回写。
 * 如果读与写之间补偿任务把 {@code notify_status} 从 0 推成 3 再结算成 1，
 * 回写会把 0 写回去，消息重新落进 {@code getFailedNotify} 的 (0,2,3) 窗口 → 已读消息被再推一次。</p>
 */
class MessageMarkReadIsolationQaTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "mark-read-qa"), SysMessage.class);
    }

    @Test
    @DisplayName("markRead 的 SQL 只 set is_read/read_time，绝不出现 notify_status")
    void markReadOnlyWritesReadColumns() {
        SysMessageMapper mapper = mock(SysMessageMapper.class);
        when(mapper.update(isNull(), any())).thenReturn(1);

        new SysMessageServiceImpl(mapper).markRead(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<SysMessage> wrapper = (UpdateWrapper<SysMessage>) captor.getValue();

        String set = wrapper.getSqlSet();
        assertTrue(set.contains("is_read"), "必须 set is_read，实际：" + set);
        assertTrue(set.contains("read_time"), "必须 set read_time，实际：" + set);
        assertEquals(1, countChar(set, ','), "只允许两列，实际：" + set);
        for (String forbidden : new String[]{"notify_status", "notify_time", "title", "content", "dedup_key"}) {
            assertFalse(set.contains(forbidden), "已读操作不得写 " + forbidden + "，实际：" + set);
        }
        // 绝不能走整实体回写（updateById 会把读到的 notify_status 一起写回去）
        verify(mapper, never()).updateById(any(SysMessage.class));
        verify(mapper, never()).selectById(any());
    }

    @Test
    @DisplayName("markRead(null) 直接返回，不抛异常也不碰数据库")
    void markReadWithNullIdIsNoOp() {
        SysMessageMapper mapper = mock(SysMessageMapper.class);
        SysMessageServiceImpl service = new SysMessageServiceImpl(mapper);

        assertDoesNotThrow(() -> service.markRead(null));

        verify(mapper, never()).update(any(), any());
        verify(mapper, never()).updateById(any(SysMessage.class));
    }

    @Test
    @DisplayName("markRead 不影响 notify_status（即使读的时候是「待推送」）")
    void markReadKeepsNotifyStatusIntact() {
        SysMessageMapper mapper = mock(SysMessageMapper.class);
        when(mapper.update(isNull(), any())).thenReturn(1);

        // 读到一个「待推送」的旧快照，随后补偿任务把它结算成 1，再点已读
        SysMessage staleSnapshot = new SysMessage();
        staleSnapshot.setId(9L);
        staleSnapshot.setNotifyStatus(0);
        staleSnapshot.setIsRead(0);
        new SysMessageServiceImpl(mapper).markRead(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysMessage>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        UpdateWrapper<SysMessage> wrapper = (UpdateWrapper<SysMessage>) captor.getValue();

        assertTrue(wrapper.getSqlSet().contains("is_read"), "已读必须落库");
        assertFalse(wrapper.getSqlSet().contains("notify_status"),
                "notify_status 绝不能出现在 set 子句，否则已读会把已推送的消息推回补偿窗口");
    }

    private static int countChar(String text, char target) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == target) count++;
        }
        return count;
    }
}
