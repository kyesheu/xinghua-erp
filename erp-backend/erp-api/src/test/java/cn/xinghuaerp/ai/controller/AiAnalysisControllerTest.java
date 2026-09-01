package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.ai.service.impl.AiAnalysisService;
import cn.xinghuaerp.ai.entity.AiAnalysisRecord;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.ai.service.IAiAnalysisRecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.ai.controller.AiAnalysisController;
import cn.xinghuaerp.system.service.AiAgentPermissionService;

/** 分析记录按 userId 归属隔离：列表只看自己（管理员看全部），详情与删除校验归属。 */
class AiAnalysisControllerTest {
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void login(long userId) {
        var user = new LoginUser();
        user.setUserId(userId);
        user.setPermissions(Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private AiAnalysisController controller(IAiAnalysisRecordService records) {
        var permissions = mock(cn.xinghuaerp.system.service.AiAgentPermissionService.class);
        when(permissions.allows(any(), eq("ai:analysis:list"))).thenReturn(true);
        return new AiAnalysisController(mock(AiAnalysisService.class), records, permissions);
    }

    @Test
    void listScopesToCurrentUserUnlessAdmin() {
        var records = mock(IAiAnalysisRecordService.class);
        login(7L);
        controller(records).list(new PageQuery());
        verify(records).queryPageList(any(PageQuery.class), eq(7L));
        SecurityContextHolder.clearContext();
        login(1L);
        controller(records).list(new PageQuery());
        verify(records).queryPageList(any(PageQuery.class), eq((Long) null));
    }

    @Test
    void detailAndRemoveRequireOwnership() {
        var records = mock(IAiAnalysisRecordService.class);
        var other = new AiAnalysisRecord();
        other.setId(9L);
        other.setUserId(8L);
        when(records.getById(9L)).thenReturn(other);
        login(7L);
        assertThrows(AccessDeniedException.class, () -> controller(records).getById(9L));
        assertThrows(AccessDeniedException.class, () -> controller(records).remove(9L));
        verify(records, never()).removeById(anyLong());
        var mine = new AiAnalysisRecord();
        mine.setId(10L);
        mine.setUserId(7L);
        when(records.getById(10L)).thenReturn(mine);
        assertDoesNotThrow(() -> controller(records).remove(10L));
        verify(records).removeById(10L);
    }

    @Test
    void adminCanReadOthersRecord() {
        var records = mock(IAiAnalysisRecordService.class);
        var other = new AiAnalysisRecord();
        other.setId(9L);
        other.setUserId(8L);
        when(records.getById(9L)).thenReturn(other);
        login(1L);
        AjaxResult result = controller(records).getById(9L);
        assertEquals(other, result.get(AjaxResult.DATA_TAG));
    }
}
