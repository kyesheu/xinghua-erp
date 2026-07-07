package cn.xinghuaerp.common;

import cn.xinghuaerp.common.constant.Constants;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PageQuery implements Serializable {
    private Integer pageSize;
    private Integer pageNum;
    private String orderByColumn;
    private String isAsc;

    public static final int DEFAULT_PAGE_INDEX = Constants.DEFAULT_PAGE_NUM;
    public static final int DEFAULT_PAGE_SIZE = Constants.DEFAULT_PAGE_SIZE;

    public <T> Page<T> build() {
        int pageNum = normalizePageNum(getPageNum());
        int pageSize = normalizePageSize(getPageSize());
        Page<T> page = new Page<>(pageNum, pageSize);
        // 加上排序
        List<OrderItem> orderItems = buildOrderItem();
        if(!CollectionUtils.isEmpty(orderItems)){
            page.addOrder(orderItems);
        }
        return page;
    }

    /**
     * 页码兜底：未传、传 0 或负数时回落到默认页码，避免 page 构造出非法页码。
     *
     * @param pageNum 调用方传入的页码，可为 null
     * @return 可直接用于构建 Page 的页码
     */
    private static int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum <= 0) ? Constants.DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 每页条数兜底：仅未传与 0 视为默认值，其余（含负数）按调用方语义透传。
     *
     * @param pageSize 调用方传入的每页条数，可为 null
     * @return 可直接用于构建 Page 的每页条数
     */
    private static int normalizePageSize(Integer pageSize) {
        return (pageSize == null || pageSize == 0) ? Constants.DEFAULT_PAGE_SIZE : pageSize;
    }

    private List<OrderItem> buildOrderItem(){
        List<OrderItem> list = new ArrayList<>();
        if(!StringUtils.hasLength(orderByColumn) || !StringUtils.hasLength(isAsc)){
            list.add(OrderItem.desc("id"));
            return list;
        }
        String[] orderByArr = orderByColumn.split(",");
        String[] isAscArr = isAsc.split(",");

        // 字段加上排序
        for (int i = 0; i < orderByArr.length; i++) {
            String orderByStr = orderByArr[i];
            String isAscStr = isAscArr.length ==1 ?isAscArr[0]:isAscArr[i];
            if("asc".equals(isAscStr)){
                list.add(OrderItem.asc(orderByStr));
            }else if("desc".equals(isAscStr)){
                list.add(OrderItem.desc(orderByStr));
            }else {
                throw new ServiceException("排序参数错误");
            }
        }
        return list;
    }

}
