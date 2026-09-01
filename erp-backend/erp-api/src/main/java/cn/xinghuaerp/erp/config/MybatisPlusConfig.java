package cn.xinghuaerp.erp.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@MapperScan({
        "cn.xinghuaerp.system.mapper",
        "cn.xinghuaerp.product.mapper",
        "cn.xinghuaerp.inventory.mapper",
        "cn.xinghuaerp.purchase.mapper",
        "cn.xinghuaerp.purchase.mapper",
        "cn.xinghuaerp.order.mapper",
        "cn.xinghuaerp.finance.mapper",
        "cn.xinghuaerp.fulfillment.mapper",
        "cn.xinghuaerp.ai.mapper",
        "cn.xinghuaerp.report.mapper"
})
public class MybatisPlusConfig {
    @Primary
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // MyBatis-Plus 3.5.16 移除了 PaginationInnerInterceptor，使用自定义拦截器
        interceptor.addInnerInterceptor(new MyBatisPaginationInterceptor());
        return interceptor;
    }
}
