package cn.xinghuaerp.ai.controller;

import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.system.service.BusinessAccessService;
import cn.xinghuaerp.ai.service.impl.AiBriefService;
import cn.xinghuaerp.system.security.common.BaseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI工作台简报 - 管理者首页智能看板数据。
 * <p>简报数据为全平台汇总（店铺数、全部订单销量），当前未做主体范围过滤，
 * 因此仅对总部主体开放；商户/店铺/供应商账号一律拒绝，避免跨主体泄露。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiBriefController extends BaseController {

    private final AiBriefService aiBriefService;
    private final BusinessAccessService access;

    @GetMapping("/brief")
    public AjaxResult getBrief() {
        var subject = access.scope(getUserId());
        if (subject == null || !"HEADQUARTERS".equals(subject.type())) {
            throw new AccessDeniedException("AI 简报仅对总部账号开放");
        }
        AiBriefService.AiBriefResponse brief = aiBriefService.generateBrief();
        return AjaxResult.success(brief);
    }
}