package cn.xinghuaerp.system.service.impl;

import cn.xinghuaerp.system.mapper.SysAlertChannelMapper;
import cn.xinghuaerp.system.entity.SysAlertChannel;
import cn.xinghuaerp.system.service.ISysAlertChannelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysAlertChannelServiceImpl extends ServiceImpl<SysAlertChannelMapper, SysAlertChannel>
        implements ISysAlertChannelService {
}
