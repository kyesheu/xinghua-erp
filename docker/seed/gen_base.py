# -*- coding: utf-8 -*-
"""生成兴华 ERP 演示数据（基础主数据）。输出 seed_base.sql。"""
import random
from datetime import datetime, timedelta

random.seed(20260915)
PUB = "http://localhost:19000/xinghua-erp"
PWD = {
    "hq": "$2a$10$DHcPdTvhFQXE6pq0l40JuecIF4qiSP10tondvJAa46nzYvImh/RBu",
    "mer": "$2a$10$Zshn6gQkcv4LneZDksPcueTy7QXCWpTKBiLwI4xqYCVHPuvqW.yBK",
    "shop": "$2a$10$fNWiLQhipUtCjtNja6217OqAPDSg7fHGjNo.BqDlOx.iEhqYupYAy",
    "wh": "$2a$10$mWH87zwxe2B4JJi4N3K23.PpSJX.EXThDVbEaH3bp0UNV5htPRs9S",
    "buy": "$2a$10$NHiH0hbXZy3PfFhK.EzAEOf9OyZe1S7dsZQvlJf4ivJ0r.OGaUUty",
    "cs": "$2a$10$H8sdrpRy9rkjTw69Qad/4ecQfqCJmU3UY8lmZRLylNoY3IKYwpRae",
    "fin": "$2a$10$Fpj.w1DJoO4SyvhD9qwEbewBmWXn0cdq5taGVO4sUq21nKaelNVwq",
}

def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

def dt(d):
    return "'" + d.strftime("%Y-%m-%d %H:%M:%S") + "'"

out = []
def emit(sql):
    out.append(sql)

def insert(table, cols, rows):
    if not rows:
        return
    emit("INSERT INTO %s (%s) VALUES\n%s;" % (table, ",".join(cols),
         ",\n".join("(" + ",".join(q(v) for v in r) + ")" for r in rows)))

emit("SET NAMES utf8mb4;")
emit("SET FOREIGN_KEY_CHECKS=0;")
for t in ["erp_merchant", "o_shop", "erp_warehouse", "erp_supplier", "o_goods_category", "o_goods_brand",
          "o_goods", "o_goods_sku", "o_goods_inventory", "oms_shop_member", "sys_oss",
          "erp_business_user_binding"]:
    emit("DELETE FROM %s;" % t)
emit("DELETE FROM sys_user WHERE user_id > 1;")
emit("DELETE FROM sys_user_role WHERE user_id > 1;")
emit("DELETE FROM sys_role WHERE role_id > 3;")
emit("DELETE FROM sys_role_menu WHERE role_id > 3;")

# ---------- 商户 ----------
merchants = [
    (1, "xinghua_hz", "杭州兴华电子商务有限公司", "13805710001", "杭州兴华电子商务有限公司", "HZ-2024-001",
     "91330110MA2GXK7L01", "陈志远", "中国工商银行杭州余杭支行", "6212261202001234501", "陈志远",
     "浙江省杭州市余杭区文一西路969号3幢5层", "2026-03-12 09:12:00"),
    (2, "yiwu_yx", "义乌优选贸易有限公司", "13905790002", "义乌优选贸易有限公司", "YW-2024-002",
     "91330782MA2HXL8M02", "王丽娟", "中国银行义乌支行", "6217856200001234502", "王丽娟",
     "浙江省金华市义乌市国际商贸城二区18街", "2026-04-08 10:35:00"),
    (3, "sz_xl", "深圳星链供应链有限公司", "13600000003", "深圳星链供应链有限公司", "SZ-2024-003",
     "91440300MA5FXK9N03", "李国强", "招商银行深圳科技园支行", "6225887800001234503", "李国强",
     "广东省深圳市南山区科技园南区高新南七道12号", "2026-05-20 14:20:00"),
]
insert("erp_merchant",
       ["id", "login_name", "nick_name", "mobile", "password", "status", "del_flag", "create_by", "create_time",
        "update_by", "update_time", "name", "number", "usci", "faren", "bank", "link_man", "address", "remark"],
       [(m[0], m[1], m[2][:8], m[3], PWD["mer"], "0", "0", "admin", m[12], "admin", m[12],
         m[2], m[4], m[5], m[6], m[7], m[8], m[10], "演示数据") for m in merchants])

# ---------- 店铺 ----------
shops = [
    (1, "兴华天猫旗舰店", 100, 1, "浙江省", "杭州市", "余杭区", "文一西路969号", "陈志远", "13805710001", "TM-HZ-001"),
    (2, "兴华京东自营店", 200, 1, "浙江省", "杭州市", "滨江区", "网商路699号", "刘敏", "13805710011", "JD-HZ-002"),
    (3, "优选拼多多店", 300, 2, "浙江省", "金华市", "义乌市", "国际商贸城二区18街", "王丽娟", "13905790002", "PD-YW-003"),
    (4, "优选抖音小店", 400, 2, "浙江省", "金华市", "义乌市", "稠城街道稠州北路22号", "赵晓燕", "13905790012", "DY-YW-004"),
    (5, "星链快手小店", 600, 3, "广东省", "深圳市", "南山区", "科技园南区高新南七道12号", "李国强", "13600000003", "KS-SZ-005"),
    (6, "星链微店", 500, 3, "广东省", "深圳市", "福田区", "深南大道1006号", "周文静", "13600000013", "WS-SZ-006"),
]
insert("o_shop",
       ["id", "name", "type", "url", "sort", "status", "seller_id", "region_id", "merchant_id", "create_time",
        "create_by", "province", "city", "district", "address", "contact", "phone", "seller_num", "remark"],
       [(s[0], s[1], s[2], "https://shop.example.com/%d" % s[0], 9, 1, "seller%d" % s[0], 1, s[3],
         "2026-06-01 10:00:00", "admin", s[4], s[5], s[6], s[7], s[8], s[9], s[10], "演示店铺") for s in shops])

# ---------- 仓库 ----------
warehouses = [
    (1, "WH-HZ-001", "华东中心仓", 1, "浙江省", "杭州市", "余杭区", "仁和街道东风路8号", "孙立", "13712340001", 1),
    (2, "WH-YW-002", "义乌前置仓", 2, "浙江省", "金华市", "义乌市", "北苑街道春晗路66号", "吴海", "13712340002", 2),
    (3, "WH-SZ-003", "华南中心仓", 3, "广东省", "深圳市", "宝安区", "福永街道物流园B3栋", "郑强", "13712340003", 3),
    (4, "WH-TJ-004", "华北周转仓", 1, "天津市", "天津市", "武清区", "京津产业新城C区7号库", "冯磊", "13712340004", 1),
]
insert("erp_warehouse",
       ["id", "warehouse_type", "warehouse_no", "warehouse_name", "erp_warehouse_name", "type", "status",
        "province", "city", "county", "address", "contacts", "phone", "create_by", "create_time",
        "merchant_id", "warehouse_source", "jdl_api_type"],
       [(w[0], "1", w[1], w[2], w[2], "1", "0", w[3], w[4], w[5], w[6], w[7], w[8], "admin",
         "2026-06-05 09:30:00", w[9], 1, 0) for w in warehouses])

# ---------- 供应商 ----------
suppliers = [
    ("深圳市声科电子有限公司", "深圳", "深圳市", "宝安区", "深圳市宝安区新桥街道新和大道28号", "周海涛", "13902340001", "91440300MA5FTK1A11", "广东", 1, 1),
    ("东莞市优品家居制造有限公司", "东莞", "东莞市", "厚街镇", "东莞市厚街镇家具大道102号", "黄志坚", "13902340002", "91441900MA5FTK2B22", "广东", 2, 1),
    ("义乌市瑞丰日用品有限公司", "义乌", "金华市", "义乌市", "义乌市北苑街道春晗路88号", "陈美玲", "13902340003", "91330782MA5FTK3C33", "浙江", 3, 1),
    ("广州市美妍化妆品有限公司", "广州", "广州市", "白云区", "广州市白云区江高镇神山大道9号", "林静怡", "13902340004", "91440100MA5FTK4D44", "广东", 4, 1),
    ("泉州市行者户外用品有限公司", "泉州", "泉州市", "晋江市", "泉州市晋江市陈埭镇岸兜工业区6号", "许文博", "13902340005", "91350500MA5FTK5E55", "福建", 5, 0),
    ("宁波市简衣服饰有限公司", "宁波", "宁波市", "鄞州区", "宁波市鄞州区首南街道天童南路555号", "范晓丽", "13902340006", "91330200MA5FTK6F66", "浙江", 6, 0),
    ("苏州光芒照明科技有限公司", "苏州", "苏州市", "吴中区", "苏州市吴中区木渎镇金枫路198号", "顾建华", "13902340007", "91320500MA5FTK7G77", "江苏", 7, 0),
    ("佛山市健力运动器材有限公司", "佛山", "佛山市", "南海区", "佛山市南海区狮山镇科技路19号", "梁俊", "13902340008", "91440600MA5FTK8H88", "广东", 8, 1),
]
sup_rows = []
for i, s in enumerate(suppliers, start=1):
    m_id = [1, 1, 2, 2, 1, 2, 3, 3][i - 1]
    wh = [1, 1, 2, 2, 1, 2, 3, 3][i - 1]
    sup_rows.append((i, s[0], "GYS2026%04d" % i, "sup%02d" % i, PWD["mer"], 13.0,
                     s[1], s[4], s[5], s[6], s[8], s[2], s[3], s[7],
                     PUB + "/supplier/license/2026-11/license_%02d.jpg" % ((i - 1) % 5 + 1),
                     "2027-12-31", s[5], "中国工商银行" + s[2] + "支行", s[0][:20], "62122612020012345%02d" % (i + 10),
                     "采购专员%d" % i, m_id, 0, wh, 1 if s[10] else 0, "2026-06-10 09:00:00"))
insert("erp_supplier",
       ["id", "name", "number", "login_name", "login_pwd", "tax_rate", "place", "address", "link_man", "contact",
        "province", "city", "county", "usci", "bl", "bl_period", "bl_faren", "bank", "bank_account_name",
        "bank_account", "purchaser_name", "merchant_id", "shop_id", "warehouse_id", "is_shipper", "create_time"],
       sup_rows)

# ---------- 商品类目 ----------
cats = [
    (1, "数码 3C", 0), (2, "家居生活", 0), (3, "服饰鞋包", 0), (4, "美妆个护", 0), (5, "户外运动", 0),
    (6, "手机配件", 1), (7, "智能穿戴", 1), (8, "影音设备", 1),
    (9, "厨房用品", 2), (10, "收纳整理", 2), (11, "灯具照明", 2),
    (12, "男装", 3), (13, "女装", 3), (14, "鞋靴箱包", 3),
    (15, "护肤", 4), (16, "彩妆", 4),
    (17, "健身器材", 5), (18, "户外装备", 5),
]
insert("o_goods_category",
       ["id", "number", "name", "remark", "parent_id", "path", "sort", "isDelete", "create_by", "create_time", "merchant_id"],
       [(c[0], "CT%04d" % c[0], c[1], c[1] + "类目", c[2], "0,%d," % c[0], c[0], 0, "admin",
         "2026-06-12 10:00:00", 0) for c in cats])

# ---------- 品牌 ----------
brands = [(1, "兴华自营"), (2, "优品生活"), (3, "极简工坊"), (4, "星野"), (5, "蓝鲸"), (6, "山行户外")]
insert("o_goods_brand", ["id", "name", "num", "status", "create_by", "create_time"],
       [(b[0], b[1], "PP%03d" % b[0], 0, "admin", "2026-06-12 11:00:00") for b in brands])

# ---------- 商品 ----------
GOODS = [
    ("兴华 无线蓝牙耳机 Pro 主动降噪", 6, 4, 1, 299.0, 199.0, "个"),
    ("兴华 头戴式降噪耳机 Studio", 8, 4, 1, 699.0, 459.0, "个"),
    ("星野 智能运动手环 5 代", 7, 4, 1, 259.0, 168.0, "只"),
    ("星野 智能手表 轻奢版", 7, 4, 1, 1299.0, 899.0, "只"),
    ("兴华 65W 氮化镓充电器", 6, 1, 1, 129.0, 79.0, "个"),
    ("兴华 磁吸无线充电宝 10000mAh", 6, 1, 1, 189.0, 118.0, "个"),
    ("优品 便携蓝牙音箱 Mini", 8, 2, 1, 239.0, 158.0, "个"),
    ("优品 桌面机械键盘 87 键", 6, 2, 1, 369.0, 245.0, "把"),
    ("极简 人体工学办公椅", 9, 3, 2, 1099.0, 720.0, "张"),
    ("极简 北欧实木餐桌 1.4 米", 9, 3, 2, 1899.0, 1280.0, "张"),
    ("优品 不粘炒锅 32cm", 9, 2, 2, 269.0, 168.0, "口"),
    ("优品 双层保温饭盒", 9, 2, 3, 129.0, 79.0, "个"),
    ("极简 折叠收纳箱 60L", 10, 3, 3, 89.0, 52.0, "个"),
    ("极简 布艺收纳挂袋", 10, 3, 3, 59.0, 32.0, "件"),
    ("光芒 LED 护眼台灯", 11, 3, 7, 259.0, 165.0, "台"),
    ("光芒 智能感应夜灯", 11, 3, 7, 79.0, 45.0, "个"),
    ("简衣 纯棉圆领短袖 T 恤", 12, 3, 6, 99.0, 55.0, "件"),
    ("简衣 商务免烫长袖衬衫", 12, 3, 6, 199.0, 118.0, "件"),
    ("简衣 女士针织开衫", 13, 3, 6, 229.0, 138.0, "件"),
    ("简衣 女士高腰阔腿裤", 13, 3, 6, 189.0, 112.0, "条"),
    ("山行 轻量徒步鞋", 14, 6, 5, 499.0, 320.0, "双"),
    ("山行 城市通勤双肩包", 14, 6, 5, 359.0, 226.0, "个"),
    ("山行 防泼水冲锋衣", 18, 6, 5, 799.0, 520.0, "件"),
    ("山行 户外露营帐篷 3 人", 18, 6, 5, 1299.0, 860.0, "顶"),
    ("蓝鲸 氨基酸洁面乳", 15, 5, 4, 89.0, 45.0, "支"),
    ("蓝鲸 保湿修护面霜 50g", 15, 5, 4, 259.0, 158.0, "瓶"),
    ("蓝鲸 烟酰胺精华液 30ml", 15, 5, 4, 199.0, 118.0, "瓶"),
    ("蓝鲸 丝绒雾面口红", 16, 5, 4, 129.0, 68.0, "支"),
    ("蓝鲸 轻透防晒乳 SPF50", 15, 5, 4, 149.0, 88.0, "瓶"),
    ("健力 可调节哑铃 20kg", 17, 2, 8, 599.0, 390.0, "对"),
    ("健力 加厚防滑瑜伽垫", 17, 2, 8, 159.0, 92.0, "张"),
    ("健力 弹力带套装", 17, 2, 8, 69.0, 36.0, "套"),
    ("兴华 Type-C 数据线 1.5m", 6, 1, 1, 39.0, 18.0, "条"),
    ("兴华 手机支架 铝合金", 6, 1, 1, 59.0, 28.0, "个"),
    ("优品 香薰蜡烛礼盒", 10, 2, 3, 139.0, 78.0, "盒"),
    ("极简 陶瓷马克杯 400ml", 9, 3, 3, 69.0, 35.0, "只"),
    ("光芒 落地阅读灯", 11, 3, 7, 399.0, 258.0, "台"),
    ("简衣 女士真丝睡裙", 13, 3, 6, 359.0, 226.0, "件"),
    ("山行 登山杖 碳纤维", 18, 6, 5, 229.0, 138.0, "对"),
    ("健力 折叠跑步机 家用", 17, 2, 8, 2199.0, 1580.0, "台"),
]
goods_rows, sku_rows, inv_rows = [], [], []
sku_id = 0
inv_id = 0
colors = ["黑色", "白色", "深蓝", "米白", "浅灰", "墨绿"]
sizes = ["S", "M", "L", "XL", "均码"]
for i, (name, cat, brand, sup, price, cost, unit) in enumerate(GOODS, start=1):
    m_id = 1 if i <= 20 else (2 if i <= 32 else 3)
    img = PUB + "/goods/2026-11/goods_%02d.jpg" % i
    low = random.choice([5, 8, 10, 15])
    goods_rows.append((i, name, img, "SP%05d" % i, unit, cat, "69%012d" % (300000000000 + i),
                       price, round(cost, 2), round(cost * 1.25, 2), round(cost * 1.1, 2),
                       low, low * 20, m_id, 1, 1, 0, "admin", "2026-07-%02d 10:%02d:00" % (i % 28 + 1, i % 60),
                       round(1.5 + i % 5 * 0.2, 2), round(10 + i % 7, 2), round(8 + i % 6, 2), round(6 + i % 4, 2),
                       random.choice(["个", "件", "只", "台"])))
    n_sku = random.choice([1, 2, 2, 3])
    for k in range(n_sku):
        sku_id += 1
        color = colors[(i + k) % len(colors)]
        size = sizes[(i + k) % len(sizes)]
        sku_rows.append((sku_id, i, name, "SP%05d" % i, "%s/%s" % (color, size), "SKU%05d%02d" % (i, k + 1),
                         color, size, "69%012d" % (300000000000 + i * 10 + k),
                         round(cost, 2), round(price, 2), round(cost * 1.05, 2), low, low * 20, m_id,
                         round(1.5 + i % 5 * 0.2, 2), round(10 + i % 7, 2), round(8 + i % 6, 2), round(6 + i % 4, 2)))
        for wh in random.sample([1, 2, 3, 4], 2):
            inv_id += 1
            qty = random.choice([0, 3, 8, 25, 60, 120, 260, 480])
            locked = min(qty, random.choice([0, 0, 2, 5, 10]))
            inv_rows.append((inv_id, i, "SP%05d" % i, sku_id, "SKU%05d%02d" % (i, k + 1), name, img,
                             "%s/%s" % (color, size), qty, locked, qty - locked, wh, m_id, 0))

insert("o_goods",
       ["id", "name", "image", "goods_num", "unit_name", "category_id", "bar_code", "retail_price", "pur_price",
        "whole_price", "unit_cost", "low_qty", "high_qty", "merchant_id", "ship_type", "status", "disable",
        "create_by", "create_time", "weight", "length", "width", "height", "period"],
       goods_rows)
insert("o_goods_sku",
       ["id", "goods_id", "goods_name", "goods_num", "sku_name", "sku_code", "color_value", "size_value",
        "bar_code", "pur_price", "retail_price", "unit_cost", "low_qty", "high_qty", "merchant_id",
        "weight", "length", "width", "height", "status", "ship_type", "shop_id"],
       [(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11], r[12], r[13], r[14],
         r[15], r[16], r[17], r[18], 1, 1, 0) for r in sku_rows])
insert("o_goods_inventory",
       ["id", "goods_id", "goods_num", "sku_id", "sku_code", "goods_name", "goods_img", "sku_name",
        "quantity", "locked_quantity", "available_quantity", "warehouse_id", "merchant_id", "shop_id",
        "stock_status", "is_delete", "create_time", "update_time"],
       [(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11], r[12], r[13],
         1, 0, "2026-08-01 09:00:00", "2026-11-12 18:00:00") for r in inv_rows])

# ---------- 会员 ----------
members = []
names = ["张伟", "王芳", "李娜", "刘洋", "陈晨", "杨帆", "赵磊", "黄婷", "周杰", "吴倩",
         "徐鹏", "孙丽", "马超", "朱琳", "胡斌", "郭敏", "何静", "高翔", "林峰", "罗雯"]
cities = [("浙江省", "杭州市", "余杭区"), ("广东省", "深圳市", "南山区"), ("江苏省", "南京市", "鼓楼区"),
          ("上海市", "上海市", "浦东新区"), ("四川省", "成都市", "武侯区"), ("北京市", "北京市", "朝阳区")]
for i in range(1, 101):
    shop = shops[(i - 1) % 6]
    c = cities[i % len(cities)]
    members.append((i, shop[3], shop[0], shop[2], "U%06d" % (100000 + i), "member%03d" % i,
                    "openid_%032d" % i, names[i % len(names)] + str(i % 10),
                    "1%03d%04d%03d" % (30 + i % 60, 1000 + i, 100 + i % 900), c[0], c[1], c[2],
                    "街道%d号" % (i * 3), "2026-08-%02d 12:00:00" % (i % 28 + 1)))
insert("oms_shop_member",
       ["id", "merchant_id", "shop_id", "shop_type", "platform_user_id", "platform_account", "platform_openid",
        "name", "phone", "province", "city", "county", "address", "create_on", "status"],
       [(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11], m[12], m[13], 0)
        for m in members])

# ---------- 角色 ----------
roles = [
    (4, "仓管员", "wms:keeper", 4),
    (5, "采购员", "purchase:buyer", 5),
    (6, "售后客服", "after:sale", 6),
    (7, "财务", "finance:viewer", 7),
]
insert("sys_role", ["role_id", "role_name", "role_key", "role_sort", "data_scope", "status", "del_flag", "create_by", "create_time"],
       [(r[0], r[1], r[2], r[3], "1", "0", "0", "admin", "2026-06-20 10:00:00") for r in roles])

menu_map = {4: [9, 8], 5: [7, 4], 6: [1, 2], 7: [1, 2]}
menu_rows = []
import subprocess
def child_menus(pid):
    return []
# 菜单按一级目录整棵授权，ID 在导入前由 SQL 子查询完成
for rid, pids in menu_map.items():
    for p in pids:
        menu_rows.append((rid, p))

# ---------- 账号 ----------
users = [
    (2, "hq_ops", "总部运营", "00", "13800000011", PWD["hq"], "总部运营账号"),
    (3, "m1_admin", "兴华商户管理员", "20", "13800000012", PWD["mer"], "商户管理员账号"),
    (4, "shop1_ops", "天猫店运营", "40", "13800000013", PWD["shop"], "店铺运营账号"),
    (5, "wh_keeper", "华东仓仓管", "10", "13800000014", PWD["wh"], "仓库管理员账号"),
    (6, "buyer01", "采购专员", "00", "13800000015", PWD["buy"], "采购账号"),
    (7, "as_cs", "售后客服", "40", "13800000016", PWD["cs"], "售后客服账号"),
    (8, "fin01", "财务专员", "00", "13800000017", PWD["fin"], "财务账号"),
]
insert("sys_user",
       ["user_id", "user_name", "nick_name", "user_type", "email", "phonenumber", "sex", "password", "status",
        "del_flag", "create_by", "create_time", "remark"],
       [(u[0], u[1], u[2], u[3], u[1] + "@xinghua-erp.com", u[4], "0", u[5], "0", "0", "admin",
         "2026-06-20 10:30:00", u[6]) for u in users])

insert("sys_user_role", ["user_id", "role_id"],
       [(2, 1), (3, 2), (4, 3), (5, 4), (6, 5), (7, 6), (8, 7)])

insert("erp_business_user_binding",
       ["user_id", "subject_type", "subject_id", "enabled", "create_by", "create_time", "update_by", "update_time"],
       [(3, "MERCHANT", 1, 1, "admin", "2026-06-20 10:40:00", "admin", "2026-06-20 10:40:00"),
        (4, "SHOP", 1, 1, "admin", "2026-06-20 10:41:00", "admin", "2026-06-20 10:41:00"),
        (5, "WAREHOUSE", 1, 1, "admin", "2026-06-20 10:42:00", "admin", "2026-06-20 10:42:00"),
        (7, "SHOP", 4, 1, "admin", "2026-06-20 10:43:00", "admin", "2026-06-20 10:43:00")])

# ---------- sys_oss 台账 ----------
oss_rows = []
for i in range(1, 41):
    oss_rows.append((i, "goods_%02d.jpg" % i, "goods_%02d.jpg" % i, "jpg",
                     PUB + "/goods/2026-11/goods_%02d.jpg" % i, "goods/2026-11/goods_%02d.jpg" % i,
                     "xinghua-erp", "goods", 0, "0", "0", "admin", "2026-11-01 09:00:00"))
for i in range(1, 6):
    oss_rows.append((40 + i, "license_%02d.jpg" % i, "营业执照%d.jpg" % i, "jpg",
                     PUB + "/supplier/license/2026-11/license_%02d.jpg" % i,
                     "supplier/license/2026-11/license_%02d.jpg" % i,
                     "xinghua-erp", "supplier-license", 0, "0", "0", "admin", "2026-11-01 09:10:00"))
insert("sys_oss",
       ["oss_id", "file_name", "original_name", "file_suffix", "url", "object_name", "bucket", "category",
        "file_size", "status", "del_flag", "create_by", "create_time"],
       [(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], 0, r[9], r[10], r[11], r[12]) for r in oss_rows])

emit("SET FOREIGN_KEY_CHECKS=1;")
with open("seed_base.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")
print("base rows: merchants=%d shops=%d warehouses=%d suppliers=%d goods=%d sku=%d inv=%d members=%d users=%d"
      % (len(merchants), len(shops), len(warehouses), len(sup_rows), len(goods_rows), len(sku_rows),
         len(inv_rows), len(members), len(users)))
