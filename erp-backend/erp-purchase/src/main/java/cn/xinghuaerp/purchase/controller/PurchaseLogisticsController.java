package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ServiceException;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.purchase.entity.ErpPurchaseLogistics;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.purchase.service.ErpPurchaseLogisticsService;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 采购承运商（物流公司）目录Controller。
 *
 * <p>全部端点仅限总部账号访问：读接口允许「采购订单查询」或「承运商维护」任一权限，
 * 写接口只认「承运商维护」权限；{@link PurchaseAccessService#requireHeadquarters(Long, String)}
 * 与 {@link PurchaseAccessService#requireHeadquartersAny(Long, String...)} 都会先校验实时权限，
 * 再校验业务主体为总部，未通过时不会触达任何 service。</p>
 *
 * @author xinghua
 * @date 2023-12-29
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/erp/logistics")
public class PurchaseLogisticsController extends BaseController
{
    /** 未启用 */
    private static final int STATUS_DISABLED = 0;
    /** 已启用 */
    private static final int STATUS_ENABLED = 1;

    /** 承运商读权限：采购订单查询 */
    private static final String READ_PERMISSION = "purchase:order:query";
    /** 承运商维护权限：主数据维护（同时具备读能力，避免维护页 403） */
    private static final String MANAGE_PERMISSION = "purchase:logistics:manage";

    private final ErpPurchaseLogisticsService logisticsService;
    private final PurchaseAccessService access;

    /**
     * 查询承运商列表。
     *
     * <p>分页规则：仅当请求带上 {@code pageNum} 或 {@code pageSize} 时才分页，此时返回真实总记录数；
     * 两者都不传（例如采购订单页「供应商发货」弹窗调用 {@code listLogistics({})}）时返回全量列表，
     * 保持既有调用方“下拉框拿到全部承运商”的行为。</p>
     *
     * @param name      承运商名称，模糊匹配，可为空
     * @param code      承运商编码，模糊匹配，可为空
     * @param status    状态（0禁用 1启用），精确匹配，可为空
     * @param pageQuery 分页参数，可为空
     * @return 列表数据，按 id 倒序
     */
    @GetMapping("/list")
    public TableDataInfo list(String name, String code, Integer status, PageQuery pageQuery)
    {
        access.requireHeadquartersAny(getUserId(), READ_PERMISSION, MANAGE_PERMISSION);

        LambdaQueryWrapper<ErpPurchaseLogistics> query = buildQuery(name, code, status);
        if (!isPaged(pageQuery)) {
            return getDataTable(logisticsService.list(query));
        }
        Page<ErpPurchaseLogistics> page = logisticsService.page(pageQuery.build(), query);
        return getDataTable(PageResult.build(page));
    }

    /**
     * 构建承运商列表查询条件：名称/编码模糊匹配、状态精确匹配，结果按 id 倒序。
     *
     * <p>包内可见，便于单元测试直接断言过滤语义。</p>
     *
     * @param name   承运商名称，空白时不参与过滤
     * @param code   承运商编码，空白时不参与过滤
     * @param status 状态，为 null 时不参与过滤
     * @return 已装配好过滤条件与排序的查询条件对象
     */
    static LambdaQueryWrapper<ErpPurchaseLogistics> buildQuery(String name, String code, Integer status)
    {
        return new LambdaQueryWrapper<ErpPurchaseLogistics>()
                .like(StringUtils.hasText(name), ErpPurchaseLogistics::getName, trim(name))
                .like(StringUtils.hasText(code), ErpPurchaseLogistics::getCode, trim(code))
                .eq(status != null, ErpPurchaseLogistics::getStatus, status)
                .orderByDesc(ErpPurchaseLogistics::getId);
    }

    /**
     * 判断本次请求是否要求分页：{@code pageNum}、{@code pageSize} 至少一个非空即视为分页。
     *
     * @param pageQuery 分页参数，可为空
     * @return 需要分页返回 {@code true}
     */
    static boolean isPaged(PageQuery pageQuery)
    {
        return pageQuery != null && (pageQuery.getPageNum() != null || pageQuery.getPageSize() != null);
    }

    /**
     * 获取物流公司详细信息
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        access.requireHeadquartersAny(getUserId(), READ_PERMISSION, MANAGE_PERMISSION);
        return success(logisticsService.getById(id));
    }

    /**
     * 修改物流公司
     */
    @PutMapping("/update")
    public AjaxResult edit(@RequestBody ErpPurchaseLogistics carrier)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        if (carrier == null || carrier.getId() == null) {
            throw new ServiceException("承运商ID不能为空");
        }
        if (logisticsService.getById(carrier.getId()) == null) {
            throw new ServiceException("承运商不存在，无法修改");
        }
        String name = requireText(carrier.getName(), "承运商名称不能为空");
        String code = requireText(carrier.getCode(), "承运商编码不能为空");
        if (logisticsService.existsByCode(code, carrier.getId())) {
            throw new ServiceException("承运商编码已存在：" + code);
        }
        carrier.setName(name);
        carrier.setCode(code);
        // 与新增策略保持一致：承运商目录是总部专属主数据，归属列不允许由客户端改写
        carrier.setMerchantId(0L);
        carrier.setShopId(0L);
        return toAjax(logisticsService.updateById(carrier));
    }


    /**
     * 切换承运商启用状态。
     *
     * <p>请求体携带的是页面上的当前状态：未启用（或未设置）时切换为启用，已启用时切换为禁用。</p>
     */
    @PutMapping("/updateStatus")
    public AjaxResult logisticsUpdateStatus(@RequestBody ErpPurchaseLogistics carrier)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        if (carrier == null || carrier.getId() == null) {
            throw new ServiceException("承运商ID不能为空");
        }
        Integer status = carrier.getStatus();
        requireValidStatus(status);
        Integer newStatus = (status == null || status == STATUS_DISABLED) ? STATUS_ENABLED : STATUS_DISABLED;
        return toAjax(logisticsService.updateStatus(carrier.getId(), newStatus));
    }

    /**
     * 新增物流公司
     */
    @PostMapping("/add")
    public AjaxResult add(@RequestBody ErpPurchaseLogistics carrier)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        if (carrier == null) {
            throw new ServiceException("承运商信息不能为空");
        }
        String name = requireText(carrier.getName(), "承运商名称不能为空");
        String code = requireText(carrier.getCode(), "承运商编码不能为空");
        requireValidStatus(carrier.getStatus());
        if (logisticsService.existsByCode(code, null)) {
            throw new ServiceException("承运商编码已存在：" + code);
        }
        carrier.setName(name);
        carrier.setCode(code);
        carrier.setShopId(0L);
        carrier.setMerchantId(0L);
        return toAjax(logisticsService.save(carrier));
    }

    /**
     * 删除物流公司
     */
    @DeleteMapping("/del/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        List<Long> idList = ids == null
                ? List.of()
                : Arrays.stream(ids).filter(Objects::nonNull).toList();
        if (idList.isEmpty()) {
            throw new ServiceException("请选择要删除的承运商");
        }
        return toAjax(logisticsService.removeBatchByIds(idList));
    }

    /**
     * 业务校验失败时返回可读提示，保持与本项目既有 Controller 的异常处理风格一致。
     *
     * @param exception 业务校验异常
     * @return 携带错误提示的响应
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult invalid(ServiceException exception)
    {
        return error(exception.getMessage());
    }

    /**
     * 校验文本必填，并返回去空格后的值。
     *
     * @param value   待校验文本
     * @param message 校验失败提示
     * @return 去空格后的文本
     */
    private static String requireText(String value, String message)
    {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    /**
     * 校验状态取值合法：仅允许 {@code null}（沿用数据库/前端默认值）、{@code 0}（禁用）、{@code 1}（启用）。
     *
     * @param status 待校验状态
     */
    private static void requireValidStatus(Integer status)
    {
        if (status != null && status != STATUS_DISABLED && status != STATUS_ENABLED) {
            throw new ServiceException("承运商状态不合法");
        }
    }

    /**
     * 去除首尾空白，{@code null} 原样返回。
     *
     * @param value 待处理文本
     * @return 去空格后的文本
     */
    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }

}
