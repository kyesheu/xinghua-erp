package cn.xinghuaerp.purchase.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import cn.xinghuaerp.common.ServiceException;
import cn.xinghuaerp.system.mapper.ErpMerchantMapper;
import cn.xinghuaerp.purchase.mapper.ErpSupplierMapper;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.purchase.service.impl.ErpSupplierServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ErpSupplierServiceImpl#setLoginName} 单元测试。
 *
 * <p>覆盖：供应商不存在/已删除拒绝、登录名被其他未删除供应商占用拒绝、
 * 保留原登录名不查重不误伤、已删除供应商不占用登录名、
 * 密码必须以 BCrypt 哈希落库（不存明文）并记录操作人与时间。</p>
 */
class ErpSupplierSetLoginNameTest
{
    private final ErpSupplierMapper mapper = mock(ErpSupplierMapper.class);
    private final ErpSupplierServiceImpl service = new ErpSupplierServiceImpl(mapper, mock(cn.xinghuaerp.system.facade.SystemFacade.class));

    private static ErpSupplier supplier(Long id, String loginName, Integer isDelete)
    {
        ErpSupplier supplier = new ErpSupplier();
        supplier.setId(id);
        supplier.setName("顺丰供应链");
        supplier.setLoginName(loginName);
        supplier.setIsDelete(isDelete);
        return supplier;
    }

    @Test
    void rejectsMissingSupplier()
    {
        when(mapper.selectById(99L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.setLoginName(99L, "supplier01", "Passw0rd!", "7"));
        assertEquals("供应商不存在，无法设置登录账户", ex.getMessage());
        verify(mapper, never()).updateById(any(ErpSupplier.class));
    }

    @Test
    void rejectsDeletedSupplier()
    {
        when(mapper.selectById(2L)).thenReturn(supplier(2L, "old", 1));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.setLoginName(2L, "supplier01", "Passw0rd!", "7"));
        assertEquals("供应商不存在，无法设置登录账户", ex.getMessage());
        verify(mapper, never()).updateById(any(ErpSupplier.class));
    }

    @Test
    void rejectsLoginNameHeldByAnotherActiveSupplier()
    {
        when(mapper.selectById(1L)).thenReturn(supplier(1L, "old", 0));
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(supplier(2L, "supplier01", 0));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.setLoginName(1L, "supplier01", "Passw0rd!", "7"));
        assertEquals("登录账号已存在，请重新输入", ex.getMessage());
        verify(mapper, never()).updateById(any(ErpSupplier.class));
    }

    @Test
    void keepsOwnLoginNameWithoutDuplicateCheck()
    {
        when(mapper.selectById(1L)).thenReturn(supplier(1L, "supplier01", 0));

        service.setLoginName(1L, "supplier01", "NewPassw0rd!", "7");

        // 保留原登录名时不查重（selectOne 仅用于查重）
        verify(mapper, never()).selectOne(any(Wrapper.class));
        assertStoredPasswordIsBcrypt("NewPassw0rd!");
    }

    @Test
    void loginNameHeldOnlyByDeletedSupplierIsAllowed()
    {
        when(mapper.selectById(1L)).thenReturn(supplier(1L, "old", 0));
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.setLoginName(1L, "supplier01", "Passw0rd!", "7");

        assertStoredPasswordIsBcrypt("Passw0rd!");
    }

    /**
     * 密码必须以 BCrypt 哈希落库：与明文不同、{@code $2} 开头、且能被 BCrypt 校验通过；
     * 同时记录操作人与更新时间。
     */
    private void assertStoredPasswordIsBcrypt(String rawPassword)
    {
        ArgumentCaptor<ErpSupplier> captor = ArgumentCaptor.forClass(ErpSupplier.class);
        verify(mapper).updateById(captor.capture());
        ErpSupplier updated = captor.getValue();

        assertNotNull(updated.getLoginPwd());
        assertNotEquals(rawPassword, updated.getLoginPwd(), "密码不得明文落库");
        assertTrue(updated.getLoginPwd().startsWith("$2"), "密码必须是 BCrypt 哈希");
        assertTrue(new BCryptPasswordEncoder().matches(rawPassword, updated.getLoginPwd()),
                "BCrypt 哈希必须能校验回原明文");
        assertEquals("7", updated.getUpdateBy());
        assertNotNull(updated.getUpdateTime());
        assertTrue(!updated.getUpdateTime().isAfter(LocalDateTime.now().plusSeconds(5)));
    }
}
