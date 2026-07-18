package cn.xinghuaerp.system.service;

import cn.xinghuaerp.system.service.BusinessSubjectScopeService;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.xinghuaerp.system.service.BusinessAccessService;

class BusinessAccessServiceTest {
    @Test void headquartersGuardRejectsOtherSubjects() {
        var scopes = mock(BusinessSubjectScopeService.class);
        var access = new BusinessAccessService(scopes);
        when(scopes.resolve(1L)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        assertDoesNotThrow(() -> access.requireHeadquarters(1L));
        for (String type : new String[]{"MERCHANT", "SHOP", "SUPPLIER", "WAREHOUSE"}) {
            when(scopes.resolve(2L)).thenReturn(new Scope(type, 2L, 2L, 2L));
            assertThrows(AccessDeniedException.class, () -> access.requireHeadquarters(2L));
        }
        when(scopes.resolve(3L)).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> access.requireHeadquarters(3L));
    }
}
