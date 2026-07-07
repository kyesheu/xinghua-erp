package cn.xinghuaerp.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import cn.xinghuaerp.enums.HttpStatus;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    /**
     * 分页查询成功时的默认提示语
     */
    private static final String QUERY_SUCCESS_MSG = "查询成功";

    private long total;
    private List<T> records;
    private int code;
    private String msg;

    public static <T> PageResult<T> build(IPage<T> page){
        return of(page.getRecords(), page.getTotal());
    }

    public static <T> PageResult<T> build(List<T> list){
        return of(list, list.size());
    }

    public static <T> PageResult<T> build(){
        return of(null, 0L);
    }

    /**
     * 统一构造分页结果，避免三处入口各自拼装状态码与提示语。
     *
     * @param records 当前页数据，可为 null
     * @param total 总记录数
     * @param <T> 数据类型
     * @return 带默认状态码与提示语的分页结果
     */
    private static <T> PageResult<T> of(List<T> records, long total) {
        PageResult<T> result = new PageResult<>();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg(QUERY_SUCCESS_MSG);
        result.setRecords(records);
        result.setTotal(total);
        return result;
    }
}
