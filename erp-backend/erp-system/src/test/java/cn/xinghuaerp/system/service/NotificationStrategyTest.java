package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.notify.*;
import cn.xinghuaerp.system.entity.SysAlertChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationStrategyTest {
    @Test void routesByTypeAndUnknownOrFailedChannelsRemainFailures() {
        var channels=mock(ISysAlertChannelService.class);var channel=new SysAlertChannel();channel.setChannelType("FAKE");
        when(channels.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(java.util.List.of(channel));
        var fake=mock(ChannelNotifier.class);when(fake.type()).thenReturn("FAKE");when(fake.send(channel,"title","content")).thenReturn(true);
        var service=new NotifierService(channels,java.util.List.of(fake));
        assertTrue(service.notifyAll("title","content"));verify(fake).send(channel,"title","content");
        channel.setChannelType("UNKNOWN");assertFalse(service.notifyAll("title","content"));
    }
    @Test void duplicateTypesFailAtRegistrationInsteadOfSilentlyOverwriting() {
        var fake=mock(ChannelNotifier.class);when(fake.type()).thenReturn("FAKE");
        assertThrows(IllegalStateException.class,()->new NotifierService(mock(ISysAlertChannelService.class),java.util.List.of(fake,fake)));
    }
}
