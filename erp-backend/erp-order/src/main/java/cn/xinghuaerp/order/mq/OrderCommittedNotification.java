package cn.xinghuaerp.order.mq;

import cn.xinghuaerp.enums.EnumShopType;

/** 本进程页面提示；可靠业务通知仍使用 Outbox。 */
public record OrderCommittedNotification(EnumShopType shopType, String orderNumber) {}
