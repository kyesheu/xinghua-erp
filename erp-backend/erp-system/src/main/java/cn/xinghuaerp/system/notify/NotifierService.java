package cn.xinghuaerp.system.notify;

import cn.xinghuaerp.system.entity.SysAlertChannel;
import cn.xinghuaerp.system.service.ISysAlertChannelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class NotifierService {

    private final ISysAlertChannelService channelService;
    private final java.util.Map<String,ChannelNotifier> strategies;

    public NotifierService(ISysAlertChannelService channelService,List<ChannelNotifier> strategies) {
        this.channelService=channelService;
        this.strategies=strategies.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ChannelNotifier::type,java.util.function.Function.identity()));
    }

    public boolean notifyAll(String title, String content) {
        List<SysAlertChannel> channels = channelService.list(
                new LambdaQueryWrapper<SysAlertChannel>().eq(SysAlertChannel::getStatus, 1));
        if (channels.isEmpty()) return true;

        boolean allOk = true;
        for (SysAlertChannel ch : channels) {
            try {
                var strategy=strategies.get(ch.getChannelType());
                boolean ok=strategy!=null && strategy.send(ch,title,content);
                if (!ok) {
                    allOk = false;
                    log.warn("渠道 {} 推送失败", ch.getChannelName());
                }
            } catch (Exception e) {
                allOk = false;
                log.error("渠道 {} 推送异常 errorType={}", ch.getChannelType(), e.getClass().getSimpleName());
            }
        }
        return allOk;
    }
}
