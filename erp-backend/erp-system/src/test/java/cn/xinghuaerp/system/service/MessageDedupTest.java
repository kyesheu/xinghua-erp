package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.service.impl.SysMessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MessageDedupTest {
    @Test void duplicateBusinessKeyIsAccepted() {
        var mapper = mock(SysMessageMapper.class);
        when(mapper.insert(any(SysMessage.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectCount(any())).thenReturn(1L);
        var message = new SysMessage(); message.setDedupKey("ORDER_CREATED:1");
        assertDoesNotThrow(() -> new SysMessageServiceImpl(mapper).save(message));
    }
    @Test void unrelatedConstraintFailureIsPropagated() {
        var mapper = mock(SysMessageMapper.class);
        when(mapper.insert(any(SysMessage.class))).thenThrow(new DuplicateKeyException("other"));
        when(mapper.selectCount(any())).thenReturn(0L);
        var message = new SysMessage(); message.setDedupKey("ORDER_CREATED:1");
        assertThrows(DuplicateKeyException.class, () -> new SysMessageServiceImpl(mapper).save(message));
    }
    @Test void zeroRowsIsFailure() {
        var mapper = mock(SysMessageMapper.class);
        assertThrows(IllegalStateException.class, () -> new SysMessageServiceImpl(mapper).save(new SysMessage()));
    }
}
