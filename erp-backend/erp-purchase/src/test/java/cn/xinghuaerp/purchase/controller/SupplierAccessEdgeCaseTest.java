package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.ServiceException;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.purchase.service.ErpSupplierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import cn.xinghuaerp.purchase.controller.SupplierController;

/**
 * 供应商档案写接口的补充边界用例（QA 独立补充，覆盖工程师用例未覆盖的三处行为）。
 *
 * <ol>
 *   <li>未登录（无 {@code SecurityContext}）时 {@code getUserId()} 的实际行为：
 *       抛 401 {@link ServiceException} 而不是返回 null / NPE；</li>
 *   <li>{@code edit} 的名称策略为必填：纯空白名称必须拒绝并抛「供应商名称不能为空」，
 *       与 {@code add} 的「空白即非法」策略完全一致（{@code name == null} 同样拒绝，见 SupplierAccessTest）；</li>
 *   <li>{@code add} 只传 name 时其余字段保持原样（不被控制器改写）。</li>
 * </ol>
 *
 * @author qa
 */
class SupplierAccessEdgeCaseTest
{
    private static final long USER_ID = 7L;
    private static final String MANAGE_PERMISSION = "purchase:supplier:manage";

    private final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    private final BusinessAccessService business = mock(BusinessAccessService.class);
    private final PurchaseAccessService access = new PurchaseAccessService(business, permissions);
    private final ErpSupplierService suppliers = mock(ErpSupplierService.class);
    private final SupplierController controller = new SupplierController(suppliers, access);

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    /**
     * 具备维护权限且主体为总部。
     */
    private void grantHeadquarters()
    {
        LoginUser user = new LoginUser();
        user.setUserId(USER_ID);
        user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(true);
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
    }

    private static ErpSupplier supplier(Long id, String name)
    {
        ErpSupplier supplier = new ErpSupplier();
        supplier.setId(id);
        supplier.setName(name);
        return supplier;
    }

    /**
     * 未登录时 {@code getUserId()} 抛 {@link ServiceException}（code=401），
     * 既不是 NPE，也不会把 null 传给权限服务；未通过校验时不触达 supplierService。
     */
    @Test
    void writeEndpointsWithoutLoginFailWithUnauthorizedServiceExceptionInsteadOfNpe()
    {
        ServiceException denied = assertThrows(ServiceException.class,
                () -> controller.add(supplier(null, "顺丰供应链")));

        assertEquals("获取用户信息异常", denied.getMessage());
        assertEquals(401, denied.getCode(), "未登录必须以 401 业务码失败，不能被降级成普通错误");

        verifyNoInteractions(permissions, business, suppliers);
    }

    /**
     * 修改时传入空白名称必须被拒绝，与 {@code add} 的「空白即非法」策略保持一致。
     *
     * <p>覆盖「非 null 但无有效字符」的全部形态：空串 {@code ""}、半角空格、制表符、换行。
     * 名称在 edit 中是<b>必填</b>项：{@code name == null}（未携带该字段）同样拒绝
     * （见 {@code SupplierAccessTest.editRejectsMissingOrBlankName}）；
     * 任何空白名称都必须抛「供应商名称不能为空」且不得触达 {@code updateById}，
     * 避免把已有名称改成 "   " 这类脏数据。</p>
     */
    @Test
    void editWithWhitespaceOnlyNameIsRejectedBeforeAnyWrite()
    {
        grantHeadquarters();
        when(suppliers.getById(1L)).thenReturn(supplier(1L, "顺丰供应链"));

        for (String blankName : List.of("", " ", "   ", "\t", "\n  ")) {
            ServiceException denied = assertThrows(ServiceException.class,
                    () -> controller.edit(supplier(1L, blankName)),
                    "空白名称必须被拒绝：" + blankName.replace("\t", "\\t").replace("\n", "\\n"));
            assertEquals("供应商名称不能为空", denied.getMessage());
        }

        verify(suppliers, never()).updateById(any());
    }

    /**
     * 新增只传 name：name 被 trim，其余字段保持调用方传入的原样（不被控制器补默认值）。
     */
    @Test
    void addWithOnlyNameTrimsNameAndLeavesOtherFieldsUntouched()
    {
        grantHeadquarters();

        ErpSupplier onlyName = supplier(null, "  顺丰供应链  ");
        controller.add(onlyName);

        ArgumentCaptor<ErpSupplier> saved = ArgumentCaptor.forClass(ErpSupplier.class);
        verify(suppliers).save(saved.capture());
        ErpSupplier actual = saved.getValue();

        assertEquals("顺丰供应链", actual.getName(), "名称必须去首尾空格");
        assertNull(actual.getNumber(), "未传编码时控制器不得补值");
        assertNull(actual.getMerchantId(), "未传商户时控制器不得补值");
        assertNull(actual.getIsDelete(), "控制器不写 isDelete，依赖数据库默认值 0");
        assertNull(actual.getId(), "新增不应携带 id（自增主键由数据库生成）");
    }

    /**
     * {@code add} 与 {@code edit} 对「纯空白名称」的策略完全统一：两者都拒绝，
     * 名称在新增与编辑中均为必填项，不存在"编辑可以跳过名称"的差异。
     */
    @Test
    void addAndEditBothRejectWhitespaceOnlyName()
    {
        grantHeadquarters();
        when(suppliers.getById(1L)).thenReturn(supplier(1L, "顺丰供应链"));

        assertThrows(ServiceException.class, () -> controller.add(supplier(null, "   ")));
        assertThrows(ServiceException.class, () -> controller.edit(supplier(1L, "   ")));
        verify(suppliers, never()).save(any());
        verify(suppliers, never()).updateById(any());
    }
}
