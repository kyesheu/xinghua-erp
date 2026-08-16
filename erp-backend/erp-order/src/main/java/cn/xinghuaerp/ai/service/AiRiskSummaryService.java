package cn.xinghuaerp.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import cn.xinghuaerp.system.service.BusinessSubjectScopeService;

@Service
@RequiredArgsConstructor
public class AiRiskSummaryService {
    private final JdbcTemplate jdbc;
    public record Overview(String scope, long refunds, String limitation) {}

    @Transactional(readOnly=true)
    public Overview overview(int days, BusinessSubjectScopeService.Scope scope) {
        if (days != 7 && days != 30) throw new IllegalArgumentException("仅支持7或30个完整自然日");
        var end = LocalDate.now().atStartOfDay();
        var args = new ArrayList<Object>();
        args.add(end.minusDays(days)); args.add(end);
        String where = " WHERE create_time>=? AND create_time<?";
        String label;
        switch (scope.type()) {
            case "HEADQUARTERS" -> label = "总部";
            case "MERCHANT" -> {
                if (scope.merchantId()==null || scope.merchantId()<=0) throw new IllegalArgumentException("商户范围无效");
                where += " AND merchant_id=?"; args.add(scope.merchantId()); label = "绑定商户";
            }
            case "SHOP" -> {
                if (scope.merchantId()==null || scope.merchantId()<0 || scope.shopId()==null || scope.shopId()<=0) throw new IllegalArgumentException("店铺范围无效");
                where += " AND merchant_id=? AND shop_id=?"; args.add(scope.merchantId()); args.add(scope.shopId()); label = "绑定店铺";
            }
            default -> throw new IllegalArgumentException("当前账号不支持售后资料查询");
        }
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM o_refund" + where, Long.class, args.toArray());
        return new Overview(label, count, "按售后记录创建时间统计全部类型和当前状态，记录数不代表已确认风险、退款完成数或财务结算；跟卖、合规及平台异常数据未接入。仅供人工核对。");
    }
}
