package cn.xinghuaerp.system.controller;

import cn.xinghuaerp.common.*;
import cn.xinghuaerp.system.bo.MerchantAddBo;
import cn.xinghuaerp.system.entity.ErpMerchant;
import cn.xinghuaerp.system.query.MerchantQuery;
import cn.xinghuaerp.system.security.common.BaseController;
import cn.xinghuaerp.system.service.ErpMerchantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.LocalDateTime;
import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.common.ResultVo;
import cn.xinghuaerp.common.TableDataInfo;

@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/merchant")
public class MerchantController extends BaseController {
    private final ErpMerchantService merchantService;


    @GetMapping("/list")
    public TableDataInfo list(MerchantQuery bo, PageQuery pageQuery) {
        var list = merchantService.list(new LambdaQueryWrapper<ErpMerchant>().eq(ErpMerchant::getStatus, 0));
        ErpMerchant self = new ErpMerchant();
        self.setId(0L);
        self.setName("总部自营");
        list.add(0,self);
        return getDataTable(list);
    }

    @GetMapping("/pageList")
    public TableDataInfo pageList(MerchantQuery bo, PageQuery pageQuery) {
        // 管理员
        PageResult<ErpMerchant> pageResult = merchantService.queryPageList(bo, pageQuery);
        return getDataTable(pageResult);
    }

    /**
     * 全部商户（仅总部查询）
     * @return
     */
    @GetMapping("/list_all")
    public AjaxResult listAll() {
        var list = merchantService.list(new LambdaQueryWrapper<ErpMerchant>().eq(ErpMerchant::getStatus, 0));
        return AjaxResult.success(list);
    }


    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        if (id == null || id <= 0) return error("请选择有效商户，总部自营不是可编辑的商户记录");
        ErpMerchant merchant = merchantService.getById(id);
        return merchant == null ? error("商户不存在") : success(merchant);
    }


    @PostMapping("/add")
    public AjaxResult add(@RequestBody MerchantAddBo bo) {
        if (bo.getStatus() != null && !"0".equals(bo.getStatus()) && !"1".equals(bo.getStatus())) return error("商户状态不正确");
        ResultVo<ErpMerchant> resultVo = merchantService.add(bo, getUsername());
        if (resultVo.getCode() == 0) return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @PutMapping("/edit")
    public AjaxResult edit(@RequestBody ErpMerchant bo)
    {
        if(bo.getId()==null || bo.getId()<=0) return AjaxResult.error("请选择有效商户");
        if (bo.getStatus() != null && !"0".equals(bo.getStatus()) && !"1".equals(bo.getStatus())) return error("商户状态不正确");
        bo.setUpdateBy(getUsername());
        bo.setUpdateTime(LocalDateTime.now());
        return merchantService.updateById(bo) ? success() : error("商户不存在或保存失败");
    }

    @DeleteMapping("/del/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        if(ids == null) return AjaxResult.error("缺少参数：id");
        merchantService.removeBatchByIds(Arrays.stream(ids).toList());
        return AjaxResult.success();
    }
}
