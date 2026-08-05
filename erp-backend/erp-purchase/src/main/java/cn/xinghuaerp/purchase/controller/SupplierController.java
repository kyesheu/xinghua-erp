package cn.xinghuaerp.purchase.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.ServiceException;
import cn.xinghuaerp.common.TableDataInfo;
import cn.xinghuaerp.purchase.service.PurchaseAccessService;
import cn.xinghuaerp.purchase.entity.ErpSupplier;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.purchase.service.ErpSupplierService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 供应商档案Controller。
 *
 * <p>权限现状（重要，勿误读）：</p>
 * <ul>
 *   <li><b>写接口已接入校验</b>：{@code POST}（新增）、{@code PUT}（修改）、{@code DELETE /{ids}}（删除）
 *       都会调用 {@link PurchaseAccessService#requireHeadquarters(Long, String)}，
 *       先校验实时权限 {@code purchase:supplier:manage}，再校验业务主体为总部；
 *       校验位于任何 service 调用之前，未通过时不会触达 supplierService。</li>
 *   <li><b>读接口暂未接入权限与主体范围（显式遗留项）</b>：{@code GET /list_all}、{@code GET /list}、
 *       {@code GET /{id}} 本轮保持原样，不带任何权限或主体校验。原因是这三者被
 *       {@code views/shipping/vendor_ship/*}、采购单页、供应商商品页等页面当作供应商下拉数据源复用，
 *       非总部账号可能仍在正常使用这些页面读取下拉数据；本轮收紧读取有回归风险，
 *       待上述调用方完成权限梳理后再单独跟进。</li>
 * </ul>
 *
 * <p>因此本控制器当前的安全边界是「读放开、写收紧」：任何已登录账号仍可读取供应商档案，
 * 但只有具备 {@code purchase:supplier:manage} 的总部账号才能新增、修改、删除及设置登录账户
 * （{@code POST /setLoginName}，补齐此前前端调用无映射的遗留项）。</p>
 *
 * @author xinghua
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/supplier")
public class SupplierController extends BaseController
{
    /** 供应商档案维护权限：新增、修改、删除供应商档案（不自动授予角色） */
    private static final String MANAGE_PERMISSION = "purchase:supplier:manage";

    private final ErpSupplierService supplierService;
    private final PurchaseAccessService access;

    /**
     * 所有供应商list（下拉数据源，本轮未接入权限校验，详见类注释）。
     *
     * @param bo        查询条件，仅使用 {@code isShipper} 精确过滤
     * @param pageQuery 分页参数，当前实现未使用
     * @return 供应商列表
     */
    @GetMapping("/list_all")
    public TableDataInfo list_all(ErpSupplier bo, PageQuery pageQuery) {
        var pageList = supplierService.list(new LambdaQueryWrapper<ErpSupplier>()
                .eq(ErpSupplier::getIsDelete,0)
                .eq(bo.getIsShipper()!=null,ErpSupplier::getIsShipper,bo.getIsShipper())
        );
        return getDataTable(pageList);
    }

    /**
     * 分页查询供应商档案（本轮未接入权限校验，详见类注释）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页后的供应商列表
     */
    @GetMapping("/list")
    public TableDataInfo list(ErpSupplier bo, PageQuery pageQuery)
    {
        var pageList = supplierService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 获取供应商详细信息（本轮未接入权限校验，详见类注释）。
     *
     * @param id 供应商ID
     * @return 供应商详情
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(supplierService.getById(id));
    }

    /**
     * 新增供应商档案。
     *
     * <p>仅限具备 {@code purchase:supplier:manage} 的总部账号；请求体为空或名称空白时拒绝。</p>
     *
     * @param scmSupplier 供应商信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult add(@RequestBody ErpSupplier scmSupplier)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        if (scmSupplier == null) {
            throw new ServiceException("供应商信息不能为空");
        }
        if (!StringUtils.hasText(scmSupplier.getName())) {
            throw new ServiceException("供应商名称不能为空");
        }
        scmSupplier.setName(scmSupplier.getName().trim());
        return toAjax(supplierService.save(scmSupplier));
    }

    /**
     * 修改供应商档案。
     *
     * <p>仅限具备 {@code purchase:supplier:manage} 的总部账号；ID 为空或目标不存在时拒绝。</p>
     *
     * <p>名称策略为<b>必填</b>，与「新增」完全一致：编辑必须提交名称，去首尾空格后落库；
     * 名称为 {@code null}（缺失）或全为空白（{@code ""}、"   "）时一律拒绝
     * （「供应商名称不能为空」），不允许通过"半更新"把已有名称留空。
     * 前端编辑弹窗会先 {@code getSupplier(id)} 回填整表单再整体提交，名称必然存在，不会误伤正常编辑。</p>
     *
     * @param scmSupplier 供应商信息
     * @return 操作结果
     */
    @PutMapping
    public AjaxResult edit(@RequestBody ErpSupplier scmSupplier)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        if (scmSupplier == null || scmSupplier.getId() == null) {
            throw new ServiceException("供应商ID不能为空");
        }
        if (supplierService.getById(scmSupplier.getId()) == null) {
            throw new ServiceException("供应商不存在，无法修改");
        }
        String name = scmSupplier.getName();
        // 名称必填：缺失（null）或全空白一律拒绝，避免把已有名称改成空白
        if (!StringUtils.hasText(name)) {
            throw new ServiceException("供应商名称不能为空");
        }
        scmSupplier.setName(name.trim());
        return toAjax(supplierService.updateById(scmSupplier));
    }

    /**
     * 删除供应商档案。
     *
     * <p>仅限具备 {@code purchase:supplier:manage} 的总部账号；{@code ids} 为空或全部为 null 时拒绝，
     * 其余情况过滤 null 元素后删除。</p>
     *
     * @param ids 待删除的供应商ID数组
     * @return 操作结果
     */
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        List<Long> idList = ids == null
                ? List.of()
                : Arrays.stream(ids).filter(Objects::nonNull).toList();
        if (idList.isEmpty()) {
            throw new ServiceException("请选择要删除的供应商");
        }
        return toAjax(supplierService.removeByIds(idList));
    }

    /**
     * 设置供应商登录账户（登录名 + 密码）。
     *
     * <p>补齐此前「前端有调用但后端无映射、必然 404」的遗留项（供应商页「设置登陆账户」弹窗）。
     * 设置登录凭据属于写操作且授予门户凭据，权限门与新增/修改/删除完全一致：
     * 具备 {@code purchase:supplier:manage} 的总部账号。业务校验（存在性、登录名查重）
     * 在 {@code ErpSupplierServiceImpl#setLoginName} 中，密码由服务层 BCrypt 加密后落库。</p>
     *
     * @param body 仅使用 {@code id}、{@code loginName}、{@code loginPwd} 三个字段
     * @return 操作结果
     */
    @PostMapping("/setLoginName")
    public AjaxResult setLoginName(@RequestBody ErpSupplier body)
    {
        access.requireHeadquarters(getUserId(), MANAGE_PERMISSION);

        if (body == null || body.getId() == null) {
            throw new ServiceException("供应商ID不能为空");
        }
        if (!StringUtils.hasText(body.getLoginName())) {
            throw new ServiceException("登录账号不能为空");
        }
        if (!StringUtils.hasText(body.getLoginPwd())) {
            throw new ServiceException("登录密码不能为空");
        }
        supplierService.setLoginName(body.getId(), body.getLoginName().trim(),
                body.getLoginPwd(), String.valueOf(getUserId()));
        return success();
    }

    /**
     * 业务校验失败时返回可读提示，保持与本项目既有 Controller 的异常处理风格一致。
     *
     * <p>仅拦截 {@link ServiceException}；{@code AccessDeniedException} 必须继续向上抛出，
     * 不能被改写为成功码或普通提示。</p>
     *
     * @param exception 业务校验异常
     * @return 携带错误提示的响应
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult invalid(ServiceException exception)
    {
        return error(exception.getMessage());
    }
}
