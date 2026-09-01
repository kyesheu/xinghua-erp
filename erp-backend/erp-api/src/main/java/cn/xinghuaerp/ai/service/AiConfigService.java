package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.ai.entity.AiConfig;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AiConfigService extends IService<AiConfig> {
    boolean setDefault(Long id);
    AiConfig getDefaultConfig();
}
