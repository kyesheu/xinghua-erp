package cn.xinghuaerp.inventory.service.impl;

import cn.xinghuaerp.inventory.mapper.OGoodsInventoryBatchMapper;
import cn.xinghuaerp.inventory.entity.OGoodsInventoryBatch;
import cn.xinghuaerp.inventory.service.OGoodsInventoryBatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 主系统商品库存批次 Service 实现
 *
 * @author xinghua
 */
@AllArgsConstructor
@Service
public class OGoodsInventoryBatchServiceImpl
        extends ServiceImpl<OGoodsInventoryBatchMapper, OGoodsInventoryBatch>
        implements OGoodsInventoryBatchService {

    @Override
    public List<OGoodsInventoryBatch> listByInventoryId(Long inventoryId) {
        return this.list(new LambdaQueryWrapper<OGoodsInventoryBatch>()
                .eq(OGoodsInventoryBatch::getInventoryId, inventoryId));
    }

    @Override
    public boolean deductBatchStock(Long batchId, Integer quantity) {
        OGoodsInventoryBatch batch = this.getById(batchId);
        if (batch == null || batch.getCurrentQty() < quantity) return false;

        OGoodsInventoryBatch update = new OGoodsInventoryBatch();
        update.setId(batchId);
        update.setCurrentQty(batch.getCurrentQty() - quantity);
        return this.updateById(update);
    }

    @Override
    public List<OGoodsInventoryBatch> searchByKeyword(String keyword, Long warehouseId) {
        LambdaQueryWrapper<OGoodsInventoryBatch> wrapper = new LambdaQueryWrapper<OGoodsInventoryBatch>()
                .and(w -> w
                        .like(OGoodsInventoryBatch::getBarcode, keyword)
                        .or()
                        .like(OGoodsInventoryBatch::getSkuCode, keyword)
                        .or()
                        .like(OGoodsInventoryBatch::getBatchNum, keyword))
                .gt(OGoodsInventoryBatch::getCurrentQty, 0);
        if (warehouseId != null) {
            wrapper.eq(OGoodsInventoryBatch::getWarehouseId, warehouseId);
        }
        // 库存流水按时间倒序展示；id 兜底，保证同秒写入的记录顺序稳定
        wrapper.orderByDesc(OGoodsInventoryBatch::getCreateTime)
                .orderByDesc(OGoodsInventoryBatch::getId);
        return this.list(wrapper);
    }
}