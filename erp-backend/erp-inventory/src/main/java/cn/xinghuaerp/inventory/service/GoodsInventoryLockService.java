package cn.xinghuaerp.inventory.service;

import cn.xinghuaerp.common.PageQuery;
import cn.xinghuaerp.common.PageResult;
import cn.xinghuaerp.inventory.entity.GoodsInventoryLock;
import cn.xinghuaerp.inventory.query.GoodsInventoryLockQuery;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 库存锁定服务接口
 * @author xinghua
 * @date 2026-04-20
 */
public interface GoodsInventoryLockService extends IService<GoodsInventoryLock> {

    /**
     * 分页查询库存锁定列表
     * @param query 查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    PageResult<GoodsInventoryLock> queryPageList(GoodsInventoryLockQuery query, PageQuery pageQuery);

    /**
     * 锁定库存（用于调拨申请）
     * @param batchId 批次ID
     * @param quantity 锁定数量
     * @param sourceId 关联ID
     * @param sourceType 来源类型
     * @return 锁定单号
     */
    String lockInventory(Long batchId, Integer quantity, Long sourceId, Integer sourceType);

    /**
     * 释放库存锁定
     * @param lockNo 锁定单号
     * @return 是否成功
     */
    boolean releaseLock(String lockNo);

    /**
     * 超时自动释放库存锁定
     */
    void autoReleaseExpiredLocks();

    /**
     * 添加库存锁定
     * @param lock 锁定信息
     * @return 是否成功
     */
    boolean addLock(GoodsInventoryLock lock);
}
