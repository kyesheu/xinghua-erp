package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.ServiceException;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.system.service.AiAgentPermissionService;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.purchase.entity.ErpPurchaseLogistics;
import cn.xinghuaerp.system.security.LoginUser;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService.Scope;
import cn.xinghuaerp.purchase.service.ErpPurchaseLogisticsService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import cn.xinghuaerp.purchase.controller.PurchaseLogisticsController;

/**
 * 采购承运商目录（{@link PurchaseLogisticsController}）单元测试：
 * 覆盖查询参数生效、分页/全量双分支、真实 total 以及写操作入参校验。
 */
class PurchaseLogisticsControllerTest {

    private static final long USER_ID = 7L;
    private static final String QUERY_PERMISSION = "purchase:order:query";
    private static final String MANAGE_PERMISSION = "purchase:logistics:manage";

    private final AiAgentPermissionService permissions = mock(AiAgentPermissionService.class);
    private final BusinessAccessService business = mock(BusinessAccessService.class);
    private final PurchaseAccessService access = new PurchaseAccessService(business, permissions);
    private final ErpPurchaseLogisticsService carriers = mock(ErpPurchaseLogisticsService.class);
    private final PurchaseLogisticsController controller = new PurchaseLogisticsController(carriers, access);

    /**
     * LambdaQueryWrapper 拼接 SQL 需要实体的 TableInfo 与列缓存，纯单元测试中手动初始化。
     */
    @BeforeAll
    static void initTableInfoForWrapperAssertions() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ErpPurchaseLogistics.class);
    }

    private void login() {
        var user = new LoginUser();
        user.setUserId(USER_ID);
        user.setPermissions(Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private void grantHeadquarters(String... permissionCodes) {
        login();
        for (String code : permissionCodes) {
            when(permissions.allows(USER_ID, code)).thenReturn(true);
        }
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static PageQuery pageQuery(Integer pageNum, Integer pageSize) {
        PageQuery query = new PageQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return query;
    }

    private static ErpPurchaseLogistics carrier(Long id, String name, String code) {
        ErpPurchaseLogistics carrier = new ErpPurchaseLogistics();
        carrier.setId(id);
        carrier.setName(name);
        carrier.setCode(code);
        return carrier;
    }

    @Test
    void listDeniesBeforeTouchingServiceForBothBranches() {
        login();
        assertThrows(AccessDeniedException.class, () -> controller.list(null, null, null, new PageQuery()));
        assertThrows(AccessDeniedException.class, () -> controller.list(null, null, null, pageQuery(2, 5)));
        assertThrows(AccessDeniedException.class, () -> controller.getInfo(1L));
        verifyNoInteractions(carriers, business);
    }

    @Test
    void readEndpointsAcceptMaintenancePermissionOnly() {
        login();
        // 只授予主数据维护权限（菜单「采购承运商维护」），未授予采购订单查询权限
        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(true);
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));
        when(carriers.list(ArgumentMatchers.<Wrapper<ErpPurchaseLogistics>>any()))
                .thenReturn(List.of(carrier(1L, "顺丰速运", "SF")));
        Page<ErpPurchaseLogistics> dbPage = new Page<>(2, 5);
        dbPage.setTotal(12L);
        dbPage.setRecords(List.of(carrier(7L, "顺丰速运", "SF")));
        when(carriers.page(any(), any())).thenReturn(dbPage);

        TableDataInfo fullList = controller.list(null, null, null, new PageQuery());
        TableDataInfo pagedList = controller.list(null, null, null, pageQuery(2, 5));
        controller.getInfo(1L);

        assertEquals(1L, fullList.getTotal());
        assertEquals(12L, pagedList.getTotal(), "分页分支同样应可访问并返回真实 total");
        verify(carriers).list(ArgumentMatchers.<Wrapper<ErpPurchaseLogistics>>any());
        verify(carriers).page(any(), any());
        verify(carriers).getById(1L);
    }

    @Test
    void readEndpointsStillRequireHeadquartersEvenWithMaintenancePermission() {
        login();
        // 仅有维护权限（无查询权限），非总部主体一律拒绝
        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(true);
        for (String type : List.of("SHOP", "MERCHANT", "SUPPLIER", "WAREHOUSE")) {
            when(business.scope(USER_ID)).thenReturn(new Scope(type, 10L, 7L, 10L));
            assertThrows(AccessDeniedException.class, () -> controller.list(null, null, null, new PageQuery()));
            assertThrows(AccessDeniedException.class, () -> controller.list(null, null, null, pageQuery(2, 5)));
            assertThrows(AccessDeniedException.class, () -> controller.getInfo(1L));
        }
        verifyNoInteractions(carriers);

        // 两个权限都具备时，非总部主体同样不允许
        when(permissions.allows(USER_ID, QUERY_PERMISSION)).thenReturn(true);
        when(business.scope(USER_ID)).thenReturn(new Scope("SHOP", 10L, 7L, 10L));
        assertThrows(AccessDeniedException.class, () -> controller.list(null, null, null, new PageQuery()));
        assertThrows(AccessDeniedException.class, () -> controller.getInfo(1L));
        verifyNoInteractions(carriers);
    }

    @Test
    void requireHeadquartersAnyDeniesWhenNoPermissionCodeGiven() {
        login();
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));

        assertThrows(AccessDeniedException.class, () -> access.requireHeadquartersAny(USER_ID));
        assertThrows(AccessDeniedException.class, () -> access.requireHeadquartersAny(USER_ID, (String[]) null));

        verifyNoInteractions(business);
    }

    /**
     * 权限码为 null 时必须被忽略（不 NPE），全为 null 时不得放行；
     * userId 为 null 时也必须直接拒绝而不是抛 NPE 或误放行。
     */
    @Test
    void requireHeadquartersAnyIgnoresNullCodeAmongValidOnes() {
        login();
        when(permissions.allows(USER_ID, MANAGE_PERMISSION)).thenReturn(true);
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));

        access.requireHeadquartersAny(USER_ID, null, MANAGE_PERMISSION);

        verify(permissions).allows(USER_ID, MANAGE_PERMISSION);
        verify(business).scope(USER_ID);
    }

    @Test
    void requireHeadquartersAnyRejectsAllNullCodesAndNullUser() {
        login();
        when(business.scope(USER_ID)).thenReturn(new Scope("HEADQUARTERS", null, null, null));

        assertThrows(AccessDeniedException.class,
                () -> access.requireHeadquartersAny(USER_ID, (String) null, null));
        assertThrows(AccessDeniedException.class,
                () -> access.requireHeadquartersAny(null, MANAGE_PERMISSION));

        verifyNoInteractions(business);
    }

    @Test
    void queryPermissionStillCannotMaintainCarriers() {
        grantHeadquarters(QUERY_PERMISSION);

        assertThrows(AccessDeniedException.class, () -> controller.add(carrier(null, "顺丰速运", "SF")));
        assertThrows(AccessDeniedException.class, () -> controller.edit(carrier(1L, "顺丰速运", "SF")));
        assertThrows(AccessDeniedException.class, () -> controller.logisticsUpdateStatus(carrier(1L, "顺丰速运", "SF")));
        assertThrows(AccessDeniedException.class, () -> controller.remove(new Long[]{1L}));

        verify(carriers, never()).save(any());
        verify(carriers, never()).updateById(any());
        verify(carriers, never()).updateStatus(any(), any());
        verify(carriers, never()).removeBatchByIds(any());
    }

    @Test
    void pagedListReturnsRealTotalInsteadOfCurrentPageRows() {
        grantHeadquarters(QUERY_PERMISSION);
        Page<ErpPurchaseLogistics> dbPage = new Page<>(2, 5);
        dbPage.setTotal(12L);
        dbPage.setRecords(List.of(carrier(7L, "顺丰速运", "SF"), carrier(6L, "圆通速递", "YTO")));
        when(carriers.page(any(), any())).thenReturn(dbPage);

        TableDataInfo result = controller.list(null, null, null, pageQuery(2, 5));

        assertEquals(12L, result.getTotal(), "total 必须是总记录数，而不是当页行数");
        assertEquals(2, result.getRows().size());
        verify(carriers).page(any(), any());
        verify(carriers, never()).list(ArgumentMatchers.<Wrapper<ErpPurchaseLogistics>>any());
    }

    @Test
    void listWithoutPageParamsKeepsFullListForDropdownCompatibility() {
        grantHeadquarters(QUERY_PERMISSION);
        when(carriers.list(ArgumentMatchers.<Wrapper<ErpPurchaseLogistics>>any())).thenReturn(List.of(
                carrier(3L, "顺丰速运", "SF"),
                carrier(2L, "圆通速递", "YTO"),
                carrier(1L, "中通快递", "ZTO")));

        TableDataInfo result = controller.list("  ", null, null, new PageQuery());

        assertEquals(3L, result.getTotal());
        assertEquals(3, result.getRows().size());
        verify(carriers).list(ArgumentMatchers.<Wrapper<ErpPurchaseLogistics>>any());
        verify(carriers, never()).page(any(), any());
    }

    @Test
    void buildQueryAppliesFuzzyNameAndCodeWithExactStatus() {
        LambdaQueryWrapper<ErpPurchaseLogistics> wrapper =
                PurchaseLogisticsController.buildQuery(" 顺丰 ", "SF", 1);

        String sqlSegment = wrapper.getSqlSegment();
        Map<String, Object> params = wrapper.getParamNameValuePairs();

        assertFalse(sqlSegment.isBlank());
        assertTrue(sqlSegment.contains("name"), "名称必须参与过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("code"), "编码必须参与过滤: " + sqlSegment);
        assertTrue(sqlSegment.contains("status"), "状态必须参与过滤: " + sqlSegment);
        assertTrue(sqlSegment.toUpperCase().contains("ORDER BY"), "必须按 id 倒序: " + sqlSegment);
        assertEquals(3, params.size(), "三个过滤条件都应带上参数: " + params);
        assertTrue(params.containsValue("%顺丰%"), "名称应模糊匹配且已去空格: " + params);
        assertTrue(params.containsValue("%SF%"), "编码应模糊匹配: " + params);
        assertTrue(params.containsValue(1), "状态应精确匹配: " + params);
    }

    @Test
    void buildQueryIgnoresBlankAndNullFilters() {
        LambdaQueryWrapper<ErpPurchaseLogistics> wrapper =
                PurchaseLogisticsController.buildQuery("   ", null, null);

        assertTrue(wrapper.getParamNameValuePairs().isEmpty(), "空过滤条件不应产生参数");
        assertFalse(wrapper.getSqlSegment().toLowerCase().contains("like"));
    }

    @Test
    void isPagedOnlyWhenPageParamsPresent() {
        assertFalse(PurchaseLogisticsController.isPaged(null));
        assertFalse(PurchaseLogisticsController.isPaged(new PageQuery()));
        assertTrue(PurchaseLogisticsController.isPaged(pageQuery(1, 10)));
        assertTrue(PurchaseLogisticsController.isPaged(pageQuery(1, null)));
        assertTrue(PurchaseLogisticsController.isPaged(pageQuery(null, 10)));
    }

    @Test
    void addRejectsBlankName() {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.add(carrier(null, "   ", "SF")));

        assertEquals("承运商名称不能为空", error.getMessage());
        verify(carriers, never()).save(any());
    }

    @Test
    void addRejectsBlankCode() {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.add(carrier(null, "顺丰速运", "  ")));

        assertEquals("承运商编码不能为空", error.getMessage());
        verify(carriers, never()).save(any());
    }

    @Test
    void addRejectsDuplicatedCode() {
        grantHeadquarters(MANAGE_PERMISSION);
        when(carriers.existsByCode("SF", null)).thenReturn(true);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.add(carrier(null, "顺丰速运", " SF ")));

        assertTrue(error.getMessage().contains("SF"));
        verify(carriers, never()).save(any());
    }

    @Test
    void addTrimsTextAndForcesHeadquartersOwnership() {
        grantHeadquarters(MANAGE_PERMISSION);
        ErpPurchaseLogistics request = carrier(null, " 顺丰速运 ", " SF ");
        request.setMerchantId(99L);
        request.setShopId(88L);

        controller.add(request);

        assertEquals("顺丰速运", request.getName());
        assertEquals("SF", request.getCode());
        assertEquals(0L, request.getMerchantId());
        assertEquals(0L, request.getShopId());
        verify(carriers).existsByCode("SF", null);
        verify(carriers).save(request);
    }

    @Test
    void addRejectsIllegalStatus() {
        grantHeadquarters(MANAGE_PERMISSION);
        ErpPurchaseLogistics illegal = carrier(null, "顺丰速运", "SF");
        illegal.setStatus(7);

        ServiceException error = assertThrows(ServiceException.class, () -> controller.add(illegal));

        assertEquals("承运商状态不合法", error.getMessage());
        verify(carriers, never()).save(any());

        // status 为 null（走库默认/前端默认）与 1（启用）必须照常保存，避免误伤前端默认传值
        ErpPurchaseLogistics nullStatus = carrier(null, "顺丰速运", "SF");
        controller.add(nullStatus);
        verify(carriers).save(nullStatus);

        ErpPurchaseLogistics enabled = carrier(null, "圆通速递", "YTO");
        enabled.setStatus(1);
        controller.add(enabled);
        verify(carriers).save(enabled);
    }

    @Test
    void editForcesHeadquartersOwnership() {
        grantHeadquarters(MANAGE_PERMISSION);
        when(carriers.getById(1L)).thenReturn(carrier(1L, "顺丰速运", "SF"));
        ErpPurchaseLogistics request = carrier(1L, "顺丰速运", "SF");
        request.setMerchantId(99L);
        request.setShopId(88L);

        controller.edit(request);

        assertEquals(0L, request.getMerchantId(), "编辑不得改写归属商户");
        assertEquals(0L, request.getShopId(), "编辑不得改写归属店铺");
        verify(carriers).updateById(request);
    }

    @Test
    void editRejectsMissingId() {
        grantHeadquarters(MANAGE_PERMISSION);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.edit(carrier(null, "顺丰速运", "SF")));

        assertEquals("承运商ID不能为空", error.getMessage());
        verify(carriers, never()).updateById(any());
    }

    @Test
    void editRejectsNotExistingCarrier() {
        grantHeadquarters(MANAGE_PERMISSION);
        when(carriers.getById(99L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.edit(carrier(99L, "顺丰速运", "SF")));

        assertEquals("承运商不存在，无法修改", error.getMessage());
        verify(carriers, never()).updateById(any());
    }

    @Test
    void editRejectsCodeUsedByAnotherCarrier() {
        grantHeadquarters(MANAGE_PERMISSION);
        when(carriers.getById(1L)).thenReturn(carrier(1L, "顺丰速运", "SF"));
        when(carriers.existsByCode("YTO", 1L)).thenReturn(true);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.edit(carrier(1L, "顺丰速运", "YTO")));

        assertTrue(error.getMessage().contains("YTO"));
        verify(carriers, never()).updateById(any());
    }

    @Test
    void updateStatusRejectsMissingId() {
        grantHeadquarters(MANAGE_PERMISSION);
        ErpPurchaseLogistics request = new ErpPurchaseLogistics();
        request.setStatus(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.logisticsUpdateStatus(request));

        assertEquals("承运商ID不能为空", error.getMessage());
        verify(carriers, never()).updateStatus(any(), any());
    }

    @Test
    void updateStatusRejectsIllegalStatusButKeepsToggleSemantics() {
        grantHeadquarters(MANAGE_PERMISSION);
        ErpPurchaseLogistics illegal = carrier(1L, "顺丰速运", "SF");
        illegal.setStatus(5);
        assertThrows(ServiceException.class, () -> controller.logisticsUpdateStatus(illegal));
        verify(carriers, never()).updateStatus(any(), any());

        ErpPurchaseLogistics disabled = carrier(1L, "顺丰速运", "SF");
        disabled.setStatus(0);
        controller.logisticsUpdateStatus(disabled);
        verify(carriers).updateStatus(1L, 1);

        ErpPurchaseLogistics enabled = carrier(2L, "圆通速递", "YTO");
        enabled.setStatus(1);
        controller.logisticsUpdateStatus(enabled);
        verify(carriers).updateStatus(2L, 0);
    }

    @Test
    void removeRejectsEmptyIds() {
        grantHeadquarters(MANAGE_PERMISSION);

        assertThrows(ServiceException.class, () -> controller.remove(new Long[]{}));
        assertThrows(ServiceException.class, () -> controller.remove(new Long[]{null, null}));
        assertThrows(ServiceException.class, () -> controller.remove(null));

        verify(carriers, never()).removeBatchByIds(any());
    }

    @Test
    void removeIgnoresNullElementsOfIds() {
        grantHeadquarters(MANAGE_PERMISSION);

        controller.remove(new Long[]{1L, null, 2L});

        verify(carriers).removeBatchByIds(List.of(1L, 2L));
    }

    /**
     * 只传 code 时不应凭空多出 name/status 条件：模糊参数个数必须与有效过滤项一致，
     * 否则 SQL 参数与占位符错位会导致真实环境报错或过滤错误。
     */
    @Test
    void buildQueryWithOnlyCodeKeepsSingleParameter() {
        LambdaQueryWrapper<ErpPurchaseLogistics> wrapper =
                PurchaseLogisticsController.buildQuery(null, "  SF  ", null);

        String sqlSegment = wrapper.getSqlSegment();
        Map<String, Object> params = wrapper.getParamNameValuePairs();

        assertEquals(1, params.size(), "只传编码时应有且仅有一个参数: " + params);
        assertTrue(params.containsValue("%SF%"), "编码需去空格并模糊匹配: " + params);
        assertTrue(sqlSegment.contains("code"), sqlSegment);
        assertFalse(sqlSegment.contains("name"), "未传名称不应出现名称条件: " + sqlSegment);
        assertFalse(sqlSegment.contains("status"), "未传状态不应出现状态条件: " + sqlSegment);
        assertTrue(sqlSegment.toUpperCase().contains("ORDER BY"), sqlSegment);
    }

    /**
     * 分页分支遇到空页：total 必须是 0 且 rows 为空集合，不能退化成 null 或全量数据。
     */
    @Test
    void pagedListWithEmptyPageReturnsZeroTotalAndEmptyRows() {
        grantHeadquarters(QUERY_PERMISSION);
        Page<ErpPurchaseLogistics> emptyPage = new Page<>(1, 5);
        emptyPage.setTotal(0L);
        emptyPage.setRecords(List.of());
        when(carriers.page(any(), any())).thenReturn(emptyPage);

        TableDataInfo result = controller.list(null, null, null, pageQuery(1, 5));

        assertEquals(0L, result.getTotal());
        assertEquals(0, result.getRows().size());
        verify(carriers).page(any(), any());
        verify(carriers, never()).list(ArgumentMatchers.<Wrapper<ErpPurchaseLogistics>>any());
    }

    /**
     * 请求体不带 status（页面未设置状态）时，与改动前一致：切换为启用。
     */
    @Test
    void updateStatusWithoutStatusTogglesToEnabled() {
        grantHeadquarters(MANAGE_PERMISSION);
        ErpPurchaseLogistics request = new ErpPurchaseLogistics();
        request.setId(3L);

        controller.logisticsUpdateStatus(request);

        verify(carriers).updateStatus(3L, 1);
    }

    /**
     * 本地 {@code @ExceptionHandler} 只能处理 ServiceException：权限拒绝必须继续向上抛，
     * 不能被改写成 200/错误提示（否则权限校验形同虚设）。
     */
    @Test
    void localExceptionHandlerCannotSwallowAccessDenied() throws Exception {
        Method handler = PurchaseLogisticsController.class
                .getMethod("invalid", ServiceException.class);
        ExceptionHandler annotation = handler.getAnnotation(ExceptionHandler.class);

        assertNotNull(annotation, "本地处理器必须显式声明 @ExceptionHandler");
        assertArrayEquals(new Class<?>[]{ServiceException.class}, annotation.value(),
                "只能声明 ServiceException，禁止用 Exception/RuntimeException 扩大拦截范围");
        assertFalse(ServiceException.class.isAssignableFrom(AccessDeniedException.class),
                "AccessDeniedException 不是 ServiceException 子类，权限拒绝不会被吞");

        AjaxResult response = controller.invalid(new ServiceException("承运商编码已存在：SF"));
        assertEquals("承运商编码已存在：SF", response.get("msg"));
        assertTrue(((Number) response.get("code")).intValue() != 200,
                "业务校验失败不应返回成功码");
    }
}
