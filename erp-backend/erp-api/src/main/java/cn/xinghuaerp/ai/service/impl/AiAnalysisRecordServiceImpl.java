package cn.xinghuaerp.ai.service.impl;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.ai.mapper.AiAnalysisRecordMapper;
import cn.xinghuaerp.ai.entity.AiAnalysisRecord;
import cn.xinghuaerp.ai.service.IAiAnalysisRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisRecordServiceImpl implements IAiAnalysisRecordService {

    private final AiAnalysisRecordMapper mapper;

    @Override
    public void save(AiAnalysisRecord record) {
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.updateById(record);
        }
    }

    @Override
    public AiAnalysisRecord getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public PageResult<AiAnalysisRecord> queryPageList(PageQuery query, Long userId) {
        LambdaQueryWrapper<AiAnalysisRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, AiAnalysisRecord::getUserId, userId);
        wrapper.orderByDesc(AiAnalysisRecord::getCreatedTime);
        Page<AiAnalysisRecord> page = mapper.selectPage(query.build(), wrapper);
        return PageResult.build(page);
    }
}
