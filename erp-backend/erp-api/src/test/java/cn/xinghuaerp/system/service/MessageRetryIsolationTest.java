package cn.xinghuaerp.system.service;
import cn.xinghuaerp.erp.task.MessageScheduler;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.notify.NotifierService;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MessageRetryIsolationTest {
 @Test void oneBrokenNotificationDoesNotPreventNextMessage() {
  var messages=mock(ISysMessageService.class);var notifier=mock(NotifierService.class);
  var first=new SysMessage();first.setId(1L);first.setTitle("first");first.setContent("a");
  var second=new SysMessage();second.setId(2L);second.setTitle("second");second.setContent("b");
  when(messages.getFailedNotify()).thenReturn(java.util.List.of(first,second));
  // 补偿前先抢占投递权，本用例只验证单条异常隔离，故让抢占一律成功。
  when(messages.claimNotify(any(),any())).thenReturn(true);
  when(notifier.notifyAll("first","a")).thenThrow(new IllegalStateException("offline"));when(notifier.notifyAll("second","b")).thenReturn(true);
  new MessageScheduler(null,null,messages,notifier,null).retryFailedNotify();
  verify(messages,never()).save(first);verify(messages).save(second);assertEquals(1,second.getNotifyStatus());
 }
}
