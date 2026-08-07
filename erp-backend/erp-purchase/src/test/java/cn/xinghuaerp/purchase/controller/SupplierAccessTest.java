package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ServiceException;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.purchase.service.ErpSupplierService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import cn.xinghuaerp.purchase.controller.SupplierController;

/**
 * 供应商档案（{@link SupplierController}）访问控制与入参校验单元测试。
 *
 * <p>覆盖三类断言：</p>
 * <ol>
 *   <li>写接口（新增/修改/删除）必须先过实时权限 + 总部主体校验，失败时不触达任何 service；</li>
 *   <li>写接口的入参校验可读且不会误触达写方法；</li>
 *   <li>读接口（{@code /list_all}、{@code /list}、{@code /{id}}）本轮<b>未</b>收紧，
 *       无任何权限时仍应正常返回并触达 service —— 这是回归保护，防止后续误加校验打断
 *       发货页、采购单页、供应商商品页的供应商下拉。</li>
 * </ol>
 */
class SupplierAccessTest
{
    private static final long USER_ID = 7L;
    private static final String MANAGE_PERMISSION = "purchase:supplier:manage";

    private final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    private final BusinessAccessService business = mock(BusinessAccessService.class);
    private final PurchaseAccessService access = new PurchaseAccessService(business, permissions);
    private final ErpSupplierService suppliers = mock(ErpSupplierService.class);
    private final SupplierController controller = new SupplierController(suppliers, access);

    /**
     * {@code /list_all} 会用 {@code LambdaQueryWrapper} 拼接 SQL，纯单元测试需手动初始化实体的 TableInfo。
     */
    @BeforeAll
    static void initTableInfoForWrapperAssertions()
    {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ErpSupplier.class);
    }

    private void login()
    {
        LoginUser user = new LoginUser();
        user.setUserId(USER_ID);
        user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    /**
     * 具备指定权限且主体为总部。
     *
     * @param permissionCodes 需要放行的权限标识
     */
    private void grantHeadquarters(String... permissionCodes)
    {
        login();
        for (String code : permissionCodes) {
            when(permissions.allows(USER_ID, code)).thenReturn(true);
        }
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    private static ErpSupplier supplier(Long id, String name)
    {
        ErpSupplier supplier = new ErpSupplier();
        supplier.setId(id);
        supplier.setName(name);
        supplier.setNumber("S" + id);
        return supplier;
    }

    // ------------------------------------------------------------------ 权限

    @Test
    void writeEndpointsDenyMissingLivePermissionBeforeAnyServiceCall()
    {
        login();

        assertThrows(AccessDeniedException.class, () -> controller.add(supplier(null, "顺丰供应链")));
        assertThrows(AccessDeniedException.class, () -> controller.edit(supplier(1L, "顺丰供应链")));
        assertThrows(AccessDeniedException.class, () -> controller.remove(new Long[]{1L, 2L}));

        verifyNoInteractions(suppliers, business);
    }

    @Test
    void writeEndpointsDenyNonHeadquartersSubjectEvenWithPermission()
    {
        login();
        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(true);

        for (String type : List.of("SHOP", "MERCHANT", "WAREHOUSE", "SUPPLIER")) {
            when(business.scope(USER_ID)).thenReturn(new Scope(type, 10L, 7L, 10L));
            assertThrows(AccessDeniedException.class, () -> controller.add(supplier(null, "顺丰供应链")));
            assertThrows(AccessDeniedException.class, () -> controller.edit(supplier(1L, "顺丰供应链")));
            assertThrows(AccessDeniedException.class, () -> controller.remove(new Long[]{1L}));
        }

        verifyNoInteractions(suppliers);
    }

    @Test
    void writeEndpointsDenyPermissionRevocationImmediately()
    {
        grantHeadquarters(MANAGE_PERMISSION);
        when(suppliers.getById(1L)).thenReturn(supplier(1L, "顺丰供应链"));

        controller.remove(new Long[]{1L});
        verify(suppliers).removeByIds(List.of(1L));

        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> controller.remove(new Long[]{2L}));
        assertThrows(AccessDeniedException.class, () -> controller.add(supplier(null, "圆通供应链")));
        assertThrows(AccessDeniedException.class, () -> controller.edit(supplier(1L, "圆通供应链")));

        verify(suppliers, never()).removeByIds(List.of(2L));
        verify(suppliers, never()).save(ArgumentMatchers.any());
        verify(suppliers, never()).updateById(ArgumentMatchers.any());
    }

    @Test
    void headquartersWithPermissionCanAddEditAndRemove()
    {
        grantHeadquarters(MANAGE_PERMISSION);
        when(suppliers.getById(1L)).thenReturn(supplier(1L, "顺丰供应链"));

        ErpSupplier created = supplier(null, "  顺丰供应链  ");
        controller.add(created);
        assertEquals("顺丰供应链", created.getName(), "新增前必须去掉名称首尾空格");
        verify(suppliers).save(created);

        ErpSupplier updated = supplier(1L, " 圆通供应链 ");
        controller.edit(updated);
        assertEquals("圆通供应链", updated.getName(), "修改时名称非空必须去空格");
        verify(suppliers).getById(1L);
        verify(suppliers).updateById(updated);

        controller.remove(new Long[]{1L, 2L});
        verify(suppliers).removeByIds(List.of(1L, 2L));
    }

    // -------------------------------------------------------------- 入参校验

    @Test
    void addRejectsNullBodyAndBlankName()
    {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException nullBody = assertThrows(ServiceException.class, () -> controller.add(null));
        assertEquals("供应商信息不能为空", nullBody.getMessage());

        ServiceException blankName = assertThrows(ServiceException.class,
                () -> controller.add(supplier(null, "   ")));
        assertEquals("供应商名称不能为空", blankName.getMessage());

        ServiceException nullName = assertThrows(ServiceException.class,
                () -> controller.add(supplier(null, null)));
        assertEquals("供应商名称不能为空", nullName.getMessage());

        verify(suppliers, never()).save(ArgumentMatchers.any());
    }

    @Test
    void editRejectsMissingIdAndNotExistingSupplier()
    {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException nullBody = assertThrows(ServiceException.class, () -> controller.edit(null));
        assertEquals("供应商ID不能为空", nullBody.getMessage());

        ServiceException missingId = assertThrows(ServiceException.class,
                () -> controller.edit(supplier(null, "顺丰供应链")));
        assertEquals("供应商ID不能为空", missingId.getMessage());

        when(suppliers.getById(99L)).thenReturn(null);
        ServiceException notFound = assertThrows(ServiceException.class,
                () -> controller.edit(supplier(99L, "顺丰供应链")));
        assertEquals("供应商不存在，无法修改", notFound.getMessage());

        verify(suppliers, never()).updateById(ArgumentMatchers.any());
    }

    /**
     * 名称在 edit 中为必填，与 add 一致：缺失（{@code null}）、空串、纯空白三种输入都必须拒绝，
     * 且不得触达 {@code updateById}，杜绝通过"半更新"把已有名称留空。
     */
    @Test
    void editRejectsMissingOrBlankName()
    {
        grantHeadquarters(MANAGE_PERMISSION);
        when(suppliers.getById(1L)).thenReturn(supplier(1L, "顺丰供应链"));

        ErpSupplier missingName = new ErpSupplier();
        missingName.setId(1L);
        missingName.setContact("13800000000");
        ServiceException missing = assertThrows(ServiceException.class, () -> controller.edit(missingName));
        assertEquals("供应商名称不能为空", missing.getMessage());

        ServiceException empty = assertThrows(ServiceException.class, () -> controller.edit(supplier(1L, "")));
        assertEquals("供应商名称不能为空", empty.getMessage());

        ServiceException blank = assertThrows(ServiceException.class, () -> controller.edit(supplier(1L, "   ")));
        assertEquals("供应商名称不能为空", blank.getMessage());

        verify(suppliers, never()).updateById(ArgumentMatchers.any());
    }

    @Test
    void removeRejectsEmptyOrAllNullIds()
    {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException empty = assertThrows(ServiceException.class, () -> controller.remove(new Long[]{}));
        assertEquals("请选择要删除的供应商", empty.getMessage());

        ServiceException allNull = assertThrows(ServiceException.class,
                () -> controller.remove(new Long[]{null, null}));
        assertEquals("请选择要删除的供应商", allNull.getMessage());

        ServiceException nullArray = assertThrows(ServiceException.class,
                () -> controller.remove((Long[]) null));
        assertEquals("请选择要删除的供应商", nullArray.getMessage());

        verify(suppliers, never()).removeByIds(ArgumentMatchers.any());
    }

    @Test
    void removeIgnoresNullElementsOfIds()
    {
        grantHeadquarters(MANAGE_PERMISSION);

        controller.remove(new Long[]{1L, null, 2L});

        verify(suppliers).removeByIds(List.of(1L, 2L));
    }

    // ------------------------------------------------------ 设置登录账户

    /**
     * {@code POST /setLoginName} 是写操作且授予门户凭据，权限门必须与新增/修改/删除完全一致：
     * 缺实时权限或非总部主体都拒绝，且不触达 service。
     */
    @Test
    void setLoginNameDeniesMissingPermissionAndNonHeadquarters()
    {
        login();

        assertThrows(AccessDeniedException.class, () -> controller.setLoginName(loginBody(1L)));
        verifyNoInteractions(suppliers, business);

        login();
        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(true);
        for (String type : List.of("SHOP", "MERCHANT", "WAREHOUSE", "SUPPLIER")) {
            when(business.scope(USER_ID)).thenReturn(new Scope(type, 10L, 7L, 10L));
            assertThrows(AccessDeniedException.class, () -> controller.setLoginName(loginBody(1L)));
        }
        verifyNoInteractions(suppliers);
    }

    /**
     * 入参校验：ID、登录名、密码三者任一缺失都拒绝，且不触达 service。
     */
    @Test
    void setLoginNameRejectsMissingIdLoginNameOrPassword()
    {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException nullBody = assertThrows(ServiceException.class, () -> controller.setLoginName(null));
        assertEquals("供应商ID不能为空", nullBody.getMessage());

        ErpSupplier missingId = new ErpSupplier();
        missingId.setLoginName("supplier01");
        missingId.setLoginPwd("Passw0rd!");
        ServiceException noId = assertThrows(ServiceException.class, () -> controller.setLoginName(missingId));
        assertEquals("供应商ID不能为空", noId.getMessage());

        ErpSupplier missingName = new ErpSupplier();
        missingName.setId(1L);
        missingName.setLoginPwd("Passw0rd!");
        ServiceException noName = assertThrows(ServiceException.class, () -> controller.setLoginName(missingName));
        assertEquals("登录账号不能为空", noName.getMessage());

        ErpSupplier blankName = new ErpSupplier();
        blankName.setId(1L);
        blankName.setLoginName("   ");
        blankName.setLoginPwd("Passw0rd!");
        ServiceException blank = assertThrows(ServiceException.class, () -> controller.setLoginName(blankName));
        assertEquals("登录账号不能为空", blank.getMessage());

        ErpSupplier missingPwd = new ErpSupplier();
        missingPwd.setId(1L);
        missingPwd.setLoginName("supplier01");
        ServiceException noPwd = assertThrows(ServiceException.class, () -> controller.setLoginName(missingPwd));
        assertEquals("登录密码不能为空", noPwd.getMessage());

        verifyNoInteractions(suppliers);
    }

    /**
     * 合法请求委托给服务层：登录名去首尾空格，操作人传当前用户ID；
     * 明文密码原样传递（加密在服务层完成）。
     */
    @Test
    void setLoginNameDelegatesWithTrimmedLoginName()
    {
        grantHeadquarters(MANAGE_PERMISSION);

        ErpSupplier body = loginBody(1L);
        body.setLoginName("  supplier01  ");
        body.setLoginPwd("Passw0rd!");

        controller.setLoginName(body);

        verify(suppliers).setLoginName(1L, "supplier01", "Passw0rd!", String.valueOf(USER_ID));
    }

    private static ErpSupplier loginBody(Long id)
    {
        ErpSupplier body = new ErpSupplier();
        body.setId(id);
        body.setLoginName("supplier01");
        body.setLoginPwd("Passw0rd!");
        return body;
    }

    // -------------------------------------------------------- 读接口回归保护

    /**
     * 读接口本轮明确未收紧：无任何权限（{@code allows} 全 false、无主体范围）时
     * 三个读端点仍必须正常返回并触达 service，否则会打断发货页等页面的供应商下拉。
     */
    @Test
    void readEndpointsStayOpenWithoutAnyPermission()
    {
        login();
        when(suppliers.list(ArgumentMatchers.<Wrapper<ErpSupplier>>any()))
                .thenReturn(List.of(supplier(1L, "顺丰供应链")));
        when(suppliers.queryPageList(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(PageResult.build(List.of(supplier(1L, "顺丰供应链"), supplier(2L, "圆通供应链"))));
        when(suppliers.getById(1L)).thenReturn(supplier(1L, "顺丰供应链"));

        TableDataInfo all = controller.list_all(new ErpSupplier(), new PageQuery());
        TableDataInfo paged = controller.list(new ErpSupplier(), new PageQuery());
        AjaxResult detail = controller.getInfo(1L);

        assertEquals(1L, all.getTotal(), "list_all 未被收紧，应照常返回供应商下拉数据");
        assertEquals(2L, paged.getTotal(), "list 未被收紧，应照常返回分页数据");
        assertNotNull(detail.get("data"), "getInfo 未被收紧，应照常返回详情");

        verify(suppliers).list(ArgumentMatchers.<Wrapper<ErpSupplier>>any());
        verify(suppliers).queryPageList(ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(suppliers).getById(1L);
        verifyNoInteractions(business);
    }

    /**
     * 本地 {@code @ExceptionHandler} 只能处理 ServiceException：权限拒绝必须继续向上抛，
     * 不能被改写成 200/错误提示（否则权限校验形同虚设）。
     */
    @Test
    void localExceptionHandlerCannotSwallowAccessDenied() throws Exception
    {
        Method handler = SupplierController.class.getMethod("invalid", ServiceException.class);
        ExceptionHandler annotation = handler.getAnnotation(ExceptionHandler.class);

        assertNotNull(annotation, "本地处理器必须显式声明 @ExceptionHandler");
        assertArrayEquals(new Class<?>[]{ServiceException.class}, annotation.value(),
                "只能声明 ServiceException，禁止用 Exception/RuntimeException 扩大拦截范围");
        assertFalse(ServiceException.class.isAssignableFrom(AccessDeniedException.class),
                "AccessDeniedException 不是 ServiceException 子类，权限拒绝不会被吞");

        AjaxResult response = controller.invalid(new ServiceException("供应商名称不能为空"));
        assertEquals("供应商名称不能为空", response.get("msg"));
        assertTrue(((Number) response.get("code")).intValue() != 200, "业务校验失败不应返回成功码");
    }
}
