package cn.xinghuaerp.system.service;
import cn.xinghuaerp.system.entity.SysMessage;
import cn.xinghuaerp.system.mapper.SysMessageMapper;
import cn.xinghuaerp.system.service.impl.SysMessageServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MessageRetrySelectionTest {
 @Test void pendingRowsWithMissingAttemptTimeCanRecoverAndBatchIsBounded() {
  TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"retry-test"),SysMessage.class);
  var mapper=mock(SysMessageMapper.class);
  when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call->{
   com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysMessage> query=call.getArgument(0);
   var sql=query.getSqlSegment();assertTrue(sql.contains("notify_time IS NULL"));assertTrue(sql.contains("created_time <"));assertTrue(sql.contains("LIMIT 20"));
   assertTrue(query.getParamNameValuePairs().containsValue(0));assertTrue(query.getParamNameValuePairs().containsValue(2));return java.util.List.of();
  });
  new SysMessageServiceImpl(mapper).getFailedNotify();
 }
}
