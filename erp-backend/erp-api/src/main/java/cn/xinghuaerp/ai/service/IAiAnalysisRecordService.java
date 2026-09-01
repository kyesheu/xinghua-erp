package cn.xinghuaerp.ai.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.ai.entity.AiAnalysisRecord;

public interface IAiAnalysisRecordService {
    void save(AiAnalysisRecord record);
    AiAnalysisRecord getById(Long id);
    void removeById(Long id);
    /** userId 为 null 时返回全部（仅管理员场景）；否则只返回该用户自己的记录。 */
    PageResult<AiAnalysisRecord> queryPageList(PageQuery query, Long userId);
}
