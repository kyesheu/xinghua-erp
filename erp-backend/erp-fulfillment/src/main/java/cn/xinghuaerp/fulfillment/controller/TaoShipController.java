package cn.xinghuaerp.fulfillment.controller;


import cn.xinghuaerp.common.AjaxResult;
import cn.xinghuaerp.order.bo.TaoOrderShipBo;
import cn.xinghuaerp.system.security.common.BaseController;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.web.bind.annotation.*;

@Log
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/tao/ship")
public class TaoShipController extends BaseController {

    @PostMapping("/order_ship")
    @ResponseBody
    public AjaxResult orderShip(@RequestBody TaoOrderShipBo bo) {
        log.info("接收到oms发货通知：开始推送到tao平台");
        String tid= bo.getTid();
        return success();
    }
}
