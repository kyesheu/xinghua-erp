# -*- coding: utf-8 -*-
"""生成兴华 ERP 演示数据（OMS 渠道侧 + 发货备货 + 供应商产品 + 采购承运商）。

输出 `04-oms.sql`，依赖 `01-base.sql` / `02-txn.sql` / `03-fixup.sql` 已导入：
本脚本只补充此前为空、但前端页面已有入口的 6 组表——

* `erp_supplier_product` + `erp_supplier_product_item` + `erp_supplier_goods_price`（供应商产品）
* `oms_shop_goods` + `oms_shop_goods_sku`（店铺商品）
* `erp_purchase_logistics`（采购承运商）
* `oms_shop_order` + `oms_shop_order_item`（店铺订单）
* `oms_shop_refund`（店铺售后）
* `o_order_stocking` + `o_order_stocking_item`（发货记录 / 订单备货）

随机种子固定，重跑结果一致。所有主键（商户 / 店铺 / 供应商 / 商品 / SKU / 平台订单）均复用已有主数据。
"""
import os
import random
import subprocess
from datetime import date, datetime, timedelta

OUT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "04-oms.sql")

random.seed(20261113)

# ---------------------------------------------------------------- 基础设施
def query(sql):
    """在 Docker 里的 MySQL 上执行查询，返回按行切分的字符串列表。"""
    out = subprocess.run(["docker", "exec", "xinghua-erp-mysql-1", "mysql",
                          "-uroot", "-pxinghua-erp-dev-only", "-N",
                          "--default-character-set=utf8mb4", "-e", sql, "xinghua-erp"],
                         capture_output=True)
    if out.returncode != 0:
        raise RuntimeError("SQL 执行失败: %s\n%s" % (sql, out.stderr.decode("utf-8", "replace")))
    text = out.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in text.strip().split("\n") if line.strip()]


def q(v):
    """把 Python 值渲染成 SQL 字面量。"""
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def dt(d):
    """datetime -> SQL 字面量。"""
    return "'" + d.strftime("%Y-%m-%d %H:%M:%S") + "'"


out = []


def emit(s):
    out.append(s)


def insert(table, cols, rows):
    """把字典行渲染成一条批量 INSERT。"""
    rows = [r for r in rows if r]
    if not rows:
        emit("-- %s: 无数据，跳过" % table)
        return
    emit("INSERT INTO %s (%s) VALUES\n%s;" % (
        table, ",".join(cols),
        ",\n".join("(" + ",".join(q(r.get(c)) for c in cols) + ")" for r in rows)))


def money(yuan):
    """元 -> 分。"""
    return int(round(float(yuan) * 100))


# ---------------------------------------------------------------- 主数据
goods = {}
for r in query("SELECT id,name,goods_num,image,category_id,unit_name,retail_price,pur_price,"
               "merchant_id,weight,length,width,height FROM o_goods ORDER BY id"):
    goods[int(r[0])] = {"id": int(r[0]), "name": r[1], "num": r[2], "image": r[3],
                        "category": int(r[4]), "unit": r[5], "retail": float(r[6]),
                        "pur": float(r[7]), "merchant": int(r[8]), "weight": float(r[9] or 0),
                        "length": float(r[10] or 0), "width": float(r[11] or 0),
                        "height": float(r[12] or 0)}

skus_by_goods = {}
for r in query("SELECT id,goods_id,sku_code,sku_name,pur_price,retail_price,merchant_id,"
               "bar_code,color_value,size_value FROM o_goods_sku ORDER BY id"):
    s = {"id": int(r[0]), "goods_id": int(r[1]), "code": r[2], "sku_name": r[3],
         "pur": float(r[4]), "retail": float(r[5]), "merchant": int(r[6]), "bar": r[7],
         "color": r[8] or "", "size": r[9] or ""}
    skus_by_goods.setdefault(s["goods_id"], []).append(s)

shops = []
for r in query("SELECT id,merchant_id,type,name FROM o_shop ORDER BY id"):
    shops.append({"id": int(r[0]), "merchant": int(r[1]), "type": int(r[2]), "name": r[3]})

suppliers = []
for r in query("SELECT id,number,name,merchant_id,warehouse_id FROM erp_supplier ORDER BY id"):
    suppliers.append({"id": int(r[0]), "number": r[1], "name": r[2],
                      "merchant": int(r[3]), "wh": int(r[4])})

warehouses = {}
for r in query("SELECT id,warehouse_no,warehouse_name FROM erp_warehouse ORDER BY id"):
    warehouses[int(r[0])] = {"no": r[1], "name": r[2]}

# 商户 -> 发货仓库（erp_warehouse 的 merchant_id 列在 01-base 里被写错位，这里按业务固定映射）
MERCHANT_WH = {1: 1, 2: 2, 3: 3}

# 演练基准时间：所有生成的时间戳都会 clamp 到它之前，避免"未来订单/未来发货"在按时间倒序时浮到列表最前面。
# 取 min(固定基准, 真实当前时间-5min)：正常情况下固定基准生效，保证重跑 md5 一致；
# 若在基准时刻之前就重跑（时钟被人为调整），自动退化为"当前时间-5 分钟"，不会产出未来数据。
BASELINE = datetime(2026, 11, 13, 14, 30, 0)
NOW = min(BASELINE, datetime.now() - timedelta(minutes=5))
TODAY = NOW.date()


def cap(t, floor=None):
    """把时间戳压到 NOW 之前；floor 用于保证不早于业务前置时间。"""
    if t is None:
        return None
    if t > NOW:
        t = NOW - timedelta(minutes=random.randint(1, 20))
    if floor is not None and t < floor:
        t = floor
    return t

goods_by_merchant = {}
for g in goods.values():
    goods_by_merchant.setdefault(g["merchant"], []).append(g)

CITIES = [("浙江省", "杭州市", "余杭区", "文一西路"), ("浙江省", "杭州市", "西湖区", "文三路"),
          ("浙江省", "杭州市", "滨江区", "网商路"), ("广东省", "深圳市", "南山区", "科苑路"),
          ("广东省", "广州市", "天河区", "体育西路"), ("江苏省", "南京市", "鼓楼区", "中山北路"),
          ("上海市", "上海市", "浦东新区", "张江路"), ("四川省", "成都市", "武侯区", "人民南路"),
          ("北京市", "北京市", "朝阳区", "建国路"), ("浙江省", "金华市", "义乌市", "稠州北路"),
          ("湖北省", "武汉市", "洪山区", "珞喻路"), ("山东省", "青岛市", "市南区", "香港中路")]
SURNAME = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
GIVEN = ["伟", "芳", "娜", "敏", "静", "强", "磊", "洋", "勇", "艳", "杰", "娟",
         "涛", "明", "超", "霞", "平", "刚", "桂英", "建国", "秀兰", "海燕"]

# 承运商：(名称, 编码, 备注里的联系人/电话/运费模板)
CARRIERS = [
    ("顺丰速运", "SF", "联系人：陈志强 13800138001；运费模板：顺丰标快-首重1kg 12元，续重2元/kg；月结账号 SF2026HZ01"),
    ("中通快递", "ZTO", "联系人：李红梅 13800138002；运费模板：江浙沪首重1kg 6元，续重1.5元/kg；月结账号 ZTO2026HZ02"),
    ("圆通速递", "YTO", "联系人：王建军 13800138003；运费模板：华东首重1kg 6.5元，续重1.8元/kg；月结账号 YTO2026HZ03"),
    ("韵达快递", "YD", "联系人：刘晓丽 13800138004；运费模板：全国首重1kg 7元，续重2元/kg；月结账号 YD2026HZ04"),
    ("申通快递", "STO", "联系人：张海峰 13800138005；运费模板：华东首重1kg 5.8元，续重1.6元/kg；月结账号 STO2026HZ05"),
    ("京东物流", "JD", "联系人：赵鹏 13800138006；运费模板：京东特惠-首重3kg 9元，续重1元/kg；月结账号 JD2026HZ06"),
    ("德邦物流", "DBL", "联系人：孙丽 13800138007；运费模板：大件快运-首重20kg 35元，续重1.2元/kg；月结账号 DBL2026HZ07"),
    ("中国邮政EMS", "EMS", "联系人：周国庆 13800138008；运费模板：EMS标准-首重0.5kg 20元，续重6元/0.5kg；月结账号 EMS2026HZ08"),
    ("极兔速递", "JT", "联系人：吴敏 13800138009；运费模板：全国首重1kg 5.5元，续重1.5元/kg；月结账号 JT2026HZ09"),
    ("百世快递", "HTKY", "联系人：郑伟 13800138010；运费模板：华东首重1kg 5元，续重1.4元/kg；月结账号 HTKY2026HZ10"),
    ("天天快递", "HHTT", "联系人：冯丹 13800138011；运费模板：江浙沪首重1kg 5.2元，续重1.3元/kg；月结账号 HHTT2026HZ11"),
    ("优速快递", "UC", "联系人：蒋文博 13800138012；运费模板：大件包裹-首重5kg 15元，续重1元/kg；月结账号 UC2026HZ12"),
    ("跨越速运", "KYSY", "联系人：韩雪 13800138013；运费模板：限时达-首重3kg 22元，续重3元/kg；月结账号 KYSY2026HZ13"),
    ("安能物流", "ANE", "联系人：杨帆 13800138014；运费模板：零担快运-首重30kg 45元，续重0.9元/kg；月结账号 ANE2026HZ14"),
]

emit("SET NAMES utf8mb4;")
emit("SET FOREIGN_KEY_CHECKS=0;")
for t in ["erp_supplier_goods_price", "erp_supplier_product_item", "erp_supplier_product",
          "oms_shop_goods_sku", "oms_shop_goods", "erp_purchase_logistics",
          "oms_shop_refund", "oms_shop_order_item", "oms_shop_order",
          "o_order_stocking_item", "o_order_stocking",
          "oms_shop_waybill_account"]:
    emit("DELETE FROM %s;" % t)

# ================================================================ 1. 供应商产品
sup_products = []
sup_items = []
sup_prices = []
sup_pid = 0
sup_iid = 0
sup_prid = 0
base_time = datetime(2026, 7, 1, 9, 0, 0)

for sp in suppliers:
    for k in range(8):
        g = goods[((sp["id"] - 1) * 5 + k * 3) % len(goods) + 1]
        sup_pid += 1
        status = 1
        if k == 7 and sp["id"] % 2 == 1:
            status = 2
        if sp["id"] == 5 and k == 6:
            status = 0
        moq = 20 + (sp["id"] * 7 + k * 13) % 180
        lead = 3 + (sp["id"] + k) % 12
        sup_products.append({
            "id": sup_pid,
            "supplier_id": sp["id"],
            "product_name": g["name"],
            "image_url": g["image"],
            "product_num": "%s%02d" % (sp["number"], k + 1),
            "category_id": g["category"],
            "brand_id": None,
            "unit_name": g["unit"],
            "length": g["length"], "width": g["width"], "height": g["height"],
            "weight": g["weight"],
            "status": status,
            "erp_goods_id": g["id"],
            "merchant_id": g["merchant"],
            "remark": "起订量%d件；供货周期%d天；结算方式：月结30天" % (moq, lead),
            "create_by": "sup%02d" % sp["id"],
            "create_time": base_time + timedelta(days=sp["id"] * 3 + k, hours=k * 2),
            "update_by": "admin",
            "update_time": base_time + timedelta(days=sp["id"] * 3 + k, hours=k * 2 + 1),
        })

        for sku in skus_by_goods.get(g["id"], []):
            sup_iid += 1
            # 供货价在商品进价上下浮动 ±4%，取两位小数
            factor = 1 + ((sup_iid * 7) % 9 - 4) / 100.0
            price = round(sku["pur"] * factor, 2)
            item_status = status if status != 0 else 0
            sup_items.append({
                "id": sup_iid,
                "supplier_product_id": sup_pid,
                "supplier_id": sp["id"],
                "sku_code": sku["code"],
                "product_name": g["name"],
                "bar_code": sku["bar"],
                "color_id": 0,
                "color_value": sku["color"],
                "color_image": g["image"],
                "size_id": 0,
                "size_value": sku["size"],
                "style_id": 0,
                "style_value": None,
                "standard": "%s/%s" % (sku["color"], sku["size"]),
                "brand_no": "PP%03d" % ((g["category"] % 6) + 1),
                "brand_name": ["兴华自营", "优品生活", "极简工坊", "星野", "蓝鲸", "山行户外"][g["category"] % 6],
                "price": price,
                "erp_goods_id": g["id"],
                "erp_goods_sku_id": sku["id"],
                "status": item_status,
                "remark": "含税供货价，按%s结算" % ("月结" if k % 2 == 0 else "货到付款"),
                "create_by": "admin",
                "create_time": base_time + timedelta(days=sp["id"] * 3 + k, hours=k * 2),
                "update_by": "admin",
                "update_time": base_time + timedelta(days=sp["id"] * 3 + k, hours=k * 2 + 1),
                "warehouse_goods_id": 0,
            })
            sup_prid += 1
            sup_prices.append({
                "id": sup_prid,
                "supplier_id": sp["id"],
                "supplier_product_id": sup_pid,
                "supplier_product_item_id": sup_iid,
                "sku_code": sku["code"],
                "price": price,
                "original_price": round(sku["retail"], 2),
                "valid_start_time": datetime(2026, 1, 1, 0, 0, 0),
                "valid_end_time": datetime(2026, 12, 31, 23, 59, 59),
                "merchant_id": g["merchant"],
                "status": 1,
                "remark": "2026 年度框架协议价",
                "create_by": "admin",
                "create_time": base_time + timedelta(days=sp["id"] * 3 + k, hours=k * 2),
                "update_by": "admin",
                "update_time": base_time + timedelta(days=sp["id"] * 3 + k, hours=k * 2 + 1),
            })

insert("erp_supplier_product",
       ["id", "supplier_id", "product_name", "image_url", "product_num", "category_id",
        "brand_id", "unit_name", "length", "width", "height", "weight", "status",
        "erp_goods_id", "merchant_id", "remark", "create_by", "create_time",
        "update_by", "update_time"], sup_products)
insert("erp_supplier_product_item",
       ["id", "supplier_product_id", "supplier_id", "sku_code", "product_name", "bar_code",
        "color_id", "color_value", "color_image", "size_id", "size_value", "style_id",
        "style_value", "standard", "brand_no", "brand_name", "price", "erp_goods_id",
        "erp_goods_sku_id", "status", "remark", "create_by", "create_time",
        "update_by", "update_time", "warehouse_goods_id"], sup_items)
insert("erp_supplier_goods_price",
       ["id", "supplier_id", "supplier_product_id", "supplier_product_item_id", "sku_code",
        "price", "original_price", "valid_start_time", "valid_end_time", "merchant_id",
        "status", "remark", "create_by", "create_time", "update_by", "update_time"],
       sup_prices)

# ================================================================ 2. 店铺商品 + SKU
# 每个店铺上架自己商户的商品；3~6 号店额外上架 2 个总部热销品做跨店分销
HEAD_HOT = [goods[i] for i in (1, 3, 5) if i in goods]
shop_goods_pool = {}
for sh in shops:
    pool = list(goods_by_merchant.get(sh["merchant"], []))
    if sh["id"] >= 3:
        # 3~6 号店额外分销 2 个总部爆款，覆盖“一店多品、一品多店”的真实场景
        own = {g["id"] for g in pool}
        extra = [g for g in HEAD_HOT if g["id"] not in own][:(2 if sh["id"] % 2 == 1 else 1)]
        pool = pool + extra
    seen = set()
    shop_goods_pool[sh["id"]] = [g for g in pool if not (g["id"] in seen or seen.add(g["id"]))]

shop_goods = []
shop_goods_sku = []
sg_id = 0
sgs_id = 0
sg_by_shop_goods = {}

for sh in shops:
    for idx, g in enumerate(shop_goods_pool[sh["id"]]):
        sg_id += 1
        g_skus = skus_by_goods.get(g["id"], [])
        # 部分多规格商品在渠道侧只上架前两个规格，控制 SKU 总数
        if len(g_skus) > 2 and (sg_id % 2 == 0):
            g_skus = g_skus[:2]
        if not g_skus:
            continue
        platform_spu = "%d%010d" % (sh["type"], 1000000 + sg_id * 137)
        min_price = min(money(s["retail"]) for s in g_skus)
        add_ts = datetime(2026, 7, 20, 10, 0, 0) + timedelta(days=(sg_id * 3) % 50, hours=idx)
        add_epoch = int(add_ts.timestamp())
        total_stock = 0
        for sku in g_skus:
            sgs_id += 1
            stock = 30 + (sgs_id * 17) % 470
            total_stock += stock
            price = money(sku["retail"]) + ((sgs_id % 5) - 2) * 100
            platform_sku = "%s%04d" % (platform_spu[-8:], sgs_id % 10000)
            row = {
                "id": sgs_id,
                "shop_goods_id": sg_id,
                "merchant_id": sh["merchant"],
                "shop_id": sh["id"],
                "shop_type": sh["type"],
                "product_id": platform_spu,
                "product_title": g["name"],
                "outer_product_id": g["num"],
                "sku_id": platform_sku,
                "outer_sku_id": sku["code"],
                "img": g["image"],
                "price": price,
                "stock_num": stock,
                "sku_code": sku["code"],
                "sku_name": sku["sku_name"],
                "status": 1 if (sgs_id % 17) else 0,
                "add_time": add_epoch,
                "sku_attrs": '[{"attr_key":"颜色","attr_value":"%s"},{"attr_key":"尺码","attr_value":"%s"}]'
                             % (sku["color"], sku["size"]),
                "stock": stock,
                "erp_goods_id": g["id"],
                "erp_goods_sku_id": sku["id"],
                "create_on": add_ts,
                "update_on": add_ts + timedelta(days=2),
                "bind_ship_sku": 1,
                "modify_time": add_epoch + 2 * 86400,
            }
            shop_goods_sku.append(row)
            sg_by_shop_goods.setdefault(sg_id, []).append(row)

        shop_goods.append({
            "id": sg_id,
            "shop_id": sh["id"],
            "shop_type": sh["type"],
            "merchant_id": sh["merchant"],
            "product_id": platform_spu,
            "outer_product_id": g["num"],
            "title": g["name"],
            "sub_title": "%s｜%s官方旗舰" % (g["name"], sh["name"].replace("店", "")),
            "imgs": '["%s"]' % g["image"],
            "img": g["image"],
            "desc_info": "<p>%s</p><p>品牌：%s；类目：%s；发货仓：%s</p>" % (
                g["name"], "兴华优选", g["category"], warehouses[MERCHANT_WH[sh["merchant"]]]["name"]),
            "attrs": '{"品牌":"兴华优选","产地":"中国大陆","保质期":"3年"}',
            "status": 1 if (sg_id % 13) else 0,
            "min_price": min_price,
            "market_price": money(g["retail"]) + 5000,
            "spu_code": g["num"],
            "add_time": add_epoch,
            "edit_time": add_epoch + 2 * 86400,
            "erp_goods_id": g["id"],
            "quantity": total_stock,
            "create_on": add_ts,
            "update_on": add_ts + timedelta(days=2),
            "deliver_method": 0,
            "bind_ship_sku": 1,
        })

insert("oms_shop_goods",
       ["id", "shop_id", "shop_type", "merchant_id", "product_id", "outer_product_id", "title",
        "sub_title", "imgs", "img", "desc_info", "attrs", "status", "min_price", "market_price",
        "spu_code", "add_time", "edit_time", "erp_goods_id", "quantity", "create_on",
        "update_on", "deliver_method", "bind_ship_sku"], shop_goods)
insert("oms_shop_goods_sku",
       ["id", "shop_goods_id", "merchant_id", "shop_id", "shop_type", "product_id",
        "product_title", "outer_product_id", "sku_id", "outer_sku_id", "img", "price",
        "stock_num", "sku_code", "sku_name", "status", "add_time", "sku_attrs", "stock",
        "erp_goods_id", "erp_goods_sku_id", "create_on", "update_on", "bind_ship_sku",
        "modify_time"], shop_goods_sku)

# ================================================================ 3. 采购承运商
carrier_rows = []
for i, (name, code, remark) in enumerate(CARRIERS):
    carrier_rows.append({
        "id": i + 1,
        "code": code,
        "name": name,
        "remark": remark,
        "status": 0 if i in (10, 13) else 1,
        "merchant_id": 0,
        "shop_id": 0,
    })
insert("erp_purchase_logistics", ["id", "code", "name", "remark", "status", "merchant_id",
                                  "shop_id"], carrier_rows)

# ================================================================ 4. 店铺订单 + 明细
START = date(2026, 9, 15)
DAYS = 60
days = [START + timedelta(days=i) for i in range(DAYS)]
day_weights = []
for d in days:
    w = 1.0
    if d.weekday() >= 5:
        w = 0.42                      # 周末下单少
    if d == date(2026, 11, 11):
        w = 9.0                       # 双十一峰值
    if d == date(2026, 11, 10):
        w = 3.0                       # 预热
    day_weights.append(w)

SHOP_WEIGHTS = {1: 30, 2: 22, 3: 18, 4: 14, 5: 10, 6: 6}
shop_ids = [s["id"] for s in shops]
shop_w = [SHOP_WEIGHTS[s["id"]] for s in shops]
shop_map = {s["id"]: s for s in shops}

PLATFORM_STATUS = {
    100: ("WAIT_SELLER_SEND_GOODS", "等待卖家发货"),
    200: ("WAIT_SELLER_SEND_GOODS", "等待卖家发货"),
    300: ("WAIT_SELLER_SEND_GOODS", "待发货"),
    400: ("WAIT_SELLER_SEND_GOODS", "待发货"),
    500: ("WAIT_SELLER_SEND_GOODS", "待发货"),
    600: ("WAIT_SELLER_SEND_GOODS", "待发货"),
}
PAY_METHOD_BY_SHOP = {100: "ALIPAY", 200: "WEIXIN", 300: "WEIXIN", 400: "WEIXIN", 500: "WEIXIN", 600: "WEIXIN"}

orders = []
order_items = []
order_by_id = {}
oid = 0
iid = 0

for n in range(240):
    d = random.choices(days, weights=day_weights, k=1)[0]
    shop_id = random.choices(shop_ids, weights=shop_w, k=1)[0]
    sh = shop_map[shop_id]
    pool = [sg for sg in shop_goods if sg["shop_id"] == shop_id and sg["status"] == 1]
    if not pool:
        continue
    # 当天（NOW 所在日）只能落在已经过去的时段，避免生成"未来订单"
    if d == TODAY:
        hours = [9, 10, 11, 12, 13]
        hour_weights = [3, 5, 7, 6, 4]
    else:
        hours = [9, 10, 11, 12, 14, 15, 16, 19, 20, 21, 22, 23]
        hour_weights = [3, 5, 6, 5, 7, 7, 6, 11, 14, 13, 9, 4]
    hour = random.choices(hours, weights=hour_weights, k=1)[0]
    minute = random.randint(0, 59)
    second = random.randint(0, 59)
    order_dt = datetime(d.year, d.month, d.day, hour, minute, second)

    age = (TODAY - d).days
    if age <= 2:
        # 最近三天还没走完发货/完成流程
        status = random.choices([0, 1, 1, 11], weights=[3, 6, 4, 1], k=1)[0]
    elif age <= 6:
        status = random.choices([1, 2, 2, 3, 101, 11], weights=[12, 14, 10, 8, 4, 3], k=1)[0]
    else:
        status = random.choices([3, 3, 2, 2, 11, 12, 13], weights=[22, 18, 12, 10, 6, 4, 2], k=1)[0]

    n_items = 1 if random.random() < 0.45 else 2
    picked = random.sample(pool, min(n_items, len(pool)))
    if not picked:
        continue

    oid += 1
    order_no = "SO%s%05d" % (d.strftime("%Y%m%d"), oid)
    goods_amount = 0
    item_rows = []
    for sg in picked:
        sku_list = sg_by_shop_goods.get(sg["id"], [])
        if not sku_list:
            continue
        sku = random.choice(sku_list)
        qty = random.choices([1, 1, 1, 2, 2, 3], weights=[10, 8, 6, 5, 3, 2], k=1)[0]
        iid += 1
        sale_price = sku["price"]
        amount = sale_price * qty
        goods_amount += amount
        sub_no = "%s-%d" % (order_no, len(item_rows) + 1)
        item_rows.append({
            "id": iid,
            "merchant_id": sh["merchant"],
            "shop_id": shop_id,
            "shop_type": sh["type"],
            "shop_order_id": oid,
            "product_id": sg["product_id"],
            "sku_id": sku["sku_id"],
            "img": sg["img"],
            "quantity": qty,
            "sale_price": sale_price,
            "title": sg["title"],
            "on_aftersale_sku_cnt": 0,
            "finish_aftersale_sku_cnt": 0,
            "sku_code": sku["sku_code"],
            "market_price": sg["market_price"],
            "sku_name": sku["sku_name"],
            "real_price": amount,
            "outer_product_id": sg["outer_product_id"],
            "outer_sku_id": sku["outer_sku_id"],
            "is_discounted": "true" if status == 3 else "false",
            "item_amount": amount,
            "is_change_price": "false",
            "change_price": None,
            "out_warehouse_id": None,
            "discount_amount": 0,
            "erp_goods_id": sku["erp_goods_id"],
            "erp_goods_sku_id": sku["erp_goods_sku_id"],
            "order_id": order_no,
            "order_time": int(order_dt.timestamp()),
            "create_on": order_dt,
            "update_on": order_dt,
            "refund_status": 1,
            "refund_amount": None,
            "sub_order_id": sub_no,
            "order_status": status,
            "update_by": "sync",
            "ship_status": 2 if status in (2, 3) else (0 if status in (0, 1, 11, 21) else 1),
            "shipping_type": "快递配送",
            "logistics_company": None,
            "logistics_code": None,
            "payment": amount,
            "seller_discount": 0,
            "finder_discount": 0,
            "deduction_price": 0,
            "is_gift": 0,
            "barcode": sku["sku_code"],
        })

    if not item_rows:
        continue
    order_items.extend(item_rows)

    freight = 0 if goods_amount >= 9900 else 800
    platform_discount = 0
    seller_discount = 0
    if d == date(2026, 11, 11):
        platform_discount = min(3000, goods_amount // 10)
    elif random.random() < 0.35:
        seller_discount = min(1500, goods_amount // 20)
    order_amount = goods_amount + freight - platform_discount - seller_discount
    payment_amount = order_amount

    refund_status = 1
    if status == 12:
        refund_status = 3
    elif status == 3 and random.random() < 0.08:
        refund_status = 4

    pay_dt = cap(order_dt + timedelta(minutes=random.randint(1, 25)), floor=order_dt)
    confirm_dt = cap(pay_dt + timedelta(minutes=random.randint(2, 90)), floor=pay_dt)
    ship_dt = None
    if status in (2, 3):
        ship_dt = cap(confirm_dt + timedelta(hours=random.randint(4, 40)), floor=confirm_dt)
        for it in item_rows:
            it["logistics_company"] = random.choice(["顺丰速运", "中通快递", "圆通速递", "韵达快递", "京东物流"])
            it["logistics_code"] = "%s%d" % (random.choice(["SF", "ZTO", "YTO", "YD", "JD"]),
                                             random.randint(10 ** 11, 10 ** 12 - 1))

    pcode, ptext = PLATFORM_STATUS[sh["type"]]
    if status == 3:
        ptext = "交易完成"
    elif status == 2:
        ptext = "卖家已发货"
    elif status == 11:
        pcode, ptext = "TRADE_CLOSED_BY_TAOBAO", "交易关闭"
    elif status == 12:
        ptext = "退款中"

    city = random.choice(CITIES)
    rname = random.choice(SURNAME) + random.choice(GIVEN)
    phone = "1%d%09d" % (random.choice([3, 5, 7, 8, 9]), random.randint(0, 999999999))

    order_by_id[oid] = {
        "id": oid,
        "order_no": order_no,
        "shop_id": shop_id,
        "shop_type": sh["type"],
        "merchant": sh["merchant"],
        "amount": payment_amount,
        "goods_amount": goods_amount,
        "order_dt": order_dt,
        "status": status,
        "items": item_rows,
        "rname": rname,
        "phone": phone,
        "city": city,
        "refund_status": refund_status,
        "ship_dt": ship_dt,
    }

    orders.append({
        "id": oid,
        "merchant_id": sh["merchant"],
        "shop_id": shop_id,
        "shop_type": sh["type"],
        "order_id": order_no,
        "order_time": int(order_dt.timestamp()),
        "update_time": int(confirm_dt.timestamp()),
        "order_status": status,
        "refund_status": refund_status,
        "goods_amount": goods_amount,
        "order_amount": order_amount,
        "freight": freight,
        "change_price": 0,
        "discount_amount": platform_discount + seller_discount,
        "deduction_price": 0,
        "payment_amount": payment_amount,
        "payment_method": PAY_METHOD_BY_SHOP.get(sh["type"], "WEIXIN"),
        "pay_time": int(pay_dt.timestamp()),
        "seller_discount": seller_discount,
        "finder_discount": 0,
        "platform_discount": platform_discount,
        "merchant_receieve_price": payment_amount - platform_discount,
        "buyer_memo": random.choice(["", "", "", "请尽快发货，谢谢", "需要开发票", "包装结实一点", "工作日白天送货"]),
        "seller_memo": random.choice(["", "", "", "已安排仓库优先发货", "客户要求顺丰"]),
        "remark": "订单同步自%s" % sh["name"],
        "province": city[0],
        "city": city[1],
        "county": city[2],
        "town": None,
        "address": "%s%d号%d栋%d单元" % (city[3], random.randint(1, 999), random.randint(1, 30),
                                   random.randint(101, 2601)),
        "receiver_name": rname,
        "receiver_phone": phone,
        "virtual_order_tel_number": None,
        "ship_done_time": int(ship_dt.timestamp()) if ship_dt else None,
        "ewaybill_order_code": None,
        "confirm_status": 1 if status not in (0, 21) else 0,
        "confirm_time": confirm_dt if status not in (0, 21) else None,
        "erp_ship_status": 2 if status in (2, 3) else (1 if status == 101 else 0),
        "erp_ship_time": ship_dt,
        "create_on": order_dt,
        "update_on": confirm_dt,
        "shop_member_id": 0,
        "platform_user_id": "u%08d" % (n + 1001),
        "platform_account": "buyer%04d" % (n + 1),
        "platform_order_status": pcode,
        "platform_order_status_text": ptext,
        "deliver_method": 0,
        "order_time_text": order_dt.strftime("%Y-%m-%d %H:%M:%S"),
        "update_time_text": confirm_dt.strftime("%Y-%m-%d %H:%M:%S"),
        "order_mode": 0,
        "cancel_reason": "买家主动取消" if status == 11 else None,
        "erp_ship_company": item_rows[0]["logistics_company"],
        "erp_ship_code": item_rows[0]["logistics_code"],
        "order_type": 0,
        "order_source": None,
        "receiver_id": "r%08d" % (n + 1),
        "logistics_partner_code": None,
        "logistics_order_no": item_rows[0]["logistics_code"],
        "latest_delivery_time": int(cap(order_dt + timedelta(days=3)).timestamp()),
        "platform_seller_id": "seller%d" % shop_id,
        "platform_type": sh["name"][-2:],
        "finish_time": (int(cap(ship_dt + timedelta(days=7)).timestamp()) * 1000) if (ship_dt and status == 3) else None,
        "encrypt_post_receiver": rname,
        "encrypt_post_tel": phone,
        "encrypt_post_address": "%s%s%s%s" % (city[0], city[1], city[2], city[3]),
        "platform_seller_name": sh["name"],
        "order_scene": 0,
        "finder_id": None,
        "live_id": None,
        "open_address_id": "oa%08d" % (n + 1),
    })

insert("oms_shop_order",
       ["id", "merchant_id", "shop_id", "shop_type", "order_id", "order_time", "update_time",
        "order_status", "refund_status", "goods_amount", "order_amount", "freight",
        "change_price", "discount_amount", "deduction_price", "payment_amount",
        "payment_method", "pay_time", "seller_discount", "finder_discount",
        "platform_discount", "merchant_receieve_price", "buyer_memo", "seller_memo", "remark",
        "province", "city", "county", "town", "address", "receiver_name", "receiver_phone",
        "virtual_order_tel_number", "ship_done_time", "ewaybill_order_code", "confirm_status",
        "confirm_time", "erp_ship_status", "erp_ship_time", "create_on", "update_on",
        "shop_member_id", "platform_user_id", "platform_account", "platform_order_status",
        "platform_order_status_text", "deliver_method", "order_time_text", "update_time_text",
        "order_mode", "cancel_reason", "erp_ship_company", "erp_ship_code", "order_type",
        "order_source", "receiver_id", "logistics_partner_code", "logistics_order_no",
        "latest_delivery_time", "platform_seller_id", "platform_type", "finish_time",
        "encrypt_post_receiver", "encrypt_post_tel", "encrypt_post_address",
        "platform_seller_name", "order_scene", "finder_id", "live_id", "open_address_id"],
       orders)
insert("oms_shop_order_item",
       ["id", "merchant_id", "shop_id", "shop_type", "shop_order_id", "product_id", "sku_id",
        "img", "quantity", "sale_price", "title", "on_aftersale_sku_cnt",
        "finish_aftersale_sku_cnt", "sku_code", "market_price", "sku_name", "real_price",
        "outer_product_id", "outer_sku_id", "is_discounted", "item_amount", "is_change_price",
        "change_price", "out_warehouse_id", "discount_amount", "erp_goods_id",
        "erp_goods_sku_id", "order_id", "order_time", "create_on", "update_on",
        "refund_status", "refund_amount", "sub_order_id", "order_status", "update_by",
        "ship_status", "shipping_type", "logistics_company", "logistics_code", "payment",
        "seller_discount", "finder_discount", "deduction_price", "is_gift", "barcode"],
       order_items)

# ================================================================ 5. 店铺售后
REFUND_REASONS = ["商品与描述不符", "尺码不合适", "七天无理由退货", "商品破损", "少件/漏发",
                  "质量问题", "不喜欢/效果不好", "拍错/多拍"]
refund_rows = []
rid = 0
candidates = [o for o in order_by_id.values() if o["status"] in (2, 3, 12)]
random.shuffle(candidates)

for o in candidates:
    if rid >= 36:
        break
    it = random.choice(o["items"])
    rid += 1
    r_type = random.choices([11, 10, 10, 20, 1, 80], weights=[10, 14, 12, 5, 4, 3], k=1)[0]
    if o["status"] == 12:
        r_status = random.choices([2, 3, 4], weights=[6, 4, 3], k=1)[0]
    else:
        r_status = random.choices([0, 1, 2, 3, 4, 5, 6, 8], weights=[6, 4, 8, 5, 12, 3, 3, 2], k=1)[0]
    cnt = min(it["quantity"], random.choices([1, 1, 1, 2], weights=[10, 6, 4, 2], k=1)[0])
    # 退款金额不得超过该笔订单实付金额（折扣后），否则金额口径不自洽
    refund_amount = min(it["sale_price"] * cnt, o["amount"])
    phase = "ON_SALE" if o["status"] in (2, 12) else "AFTER_SALE"
    apply_dt = cap(o["order_dt"] + timedelta(days=random.randint(1, 9), hours=random.randint(1, 20)),
                   floor=o["order_dt"] + timedelta(hours=1))
    handle_dt = cap(apply_dt + timedelta(hours=random.randint(2, 72)), floor=apply_dt)
    reason = random.choice(REFUND_REASONS)
    order_status_text = "SHIPPED" if o["status"] == 2 else ("COMPLETE" if o["status"] == 3 else "PART_SHIPPED")
    need_return = r_type in (10, 20)
    refund_rows.append({
        "id": rid,
        "merchant_id": o["merchant"],
        "shop_id": o["shop_id"],
        "shop_type": o["shop_type"],
        "after_id": "RF%s%04d" % (apply_dt.strftime("%Y%m%d"), rid),
        "type": r_type,
        "status": r_status,
        "status_code": "REFUND_STATUS_%d" % r_status,
        "status_name": ["售后申请", "售后关闭", "售后处理中", "退款中", "售后成功",
                        "待用户处理", "待买家发货", "平台处理中"][r_status if r_status < 8 else 2],
        "order_id": o["order_no"],
        "sub_order_id": it["sub_order_id"],
        "order_amount": o["amount"],
        "product_id": it["product_id"],
        "goods_name": it["title"],
        "goods_image": it["img"],
        "goods_price": it["sale_price"],
        "sku_name": it["sku_name"],
        "sku_id": it["sku_id"],
        "outer_id": it["outer_sku_id"],
        "sell_count": it["quantity"],
        "count": cnt,
        "refund_reason": random.randint(1, 8),
        "refund_amount": refund_amount,
        "return_waybill_id": ("YT%d" % random.randint(10 ** 11, 10 ** 12 - 1)) if need_return else None,
        "return_delivery_id": "YTO" if need_return else None,
        "return_delivery_name": "圆通速递" if need_return else None,
        "reason": reason,
        "reason_text": "买家反馈：%s，已上传凭证，经客服核对同意按流程处理。" % reason,
        "confirm_status": 1 if r_status in (2, 3, 4) else 0,
        "confirm_time": handle_dt if r_status in (2, 3, 4) else None,
        "order_time": o["order_dt"].strftime("%Y-%m-%d %H:%M:%S"),
        "order_status": order_status_text,
        "order_ship_status": 1 if o["status"] in (2, 3, 12) else 0,
        "user_ship_status": 2 if need_return else 1,
        "goods_status": 1 if need_return else 0,
        "dispute_refund_status": 1 if r_status == 8 else 0,
        "refund_status": 3 if r_status == 4 else (2 if r_status == 3 else 1),
        "refund_phase": phase,
        "refund_success_time": handle_dt.strftime("%Y-%m-%d %H:%M:%S") if r_status == 4 else None,
        "remark": "售后单由%s平台同步" % o["shop_id"],
        "ref_bought_sku_id": it["sku_id"] if r_type == 20 else None,
        "exchange_goods_name": it["title"] if r_type == 20 else None,
        "exchange_goods_price": it["sale_price"] if r_type == 20 else None,
        "exchange_goods_num": cnt if r_type == 20 else None,
        "exchange_sku_id": it["sku_id"] if r_type == 20 else None,
        "platform_seller_id": "seller%d" % o["shop_id"],
        "platform_seller_name": "店铺%d" % o["shop_id"],
        "platform_type": "OMS",
        "create_time": int(apply_dt.timestamp()),
        "update_time": int(handle_dt.timestamp()),
        "update_on": handle_dt,
        "create_on": apply_dt,
        "shop_order_id": o["id"],
        "shop_order_item_id": it["id"],
    })

insert("oms_shop_refund",
       ["id", "merchant_id", "shop_id", "shop_type", "after_id", "type", "status",
        "status_code", "status_name", "order_id", "sub_order_id", "order_amount",
        "product_id", "goods_name", "goods_image", "goods_price", "sku_name", "sku_id",
        "outer_id", "sell_count", "count", "refund_reason", "refund_amount",
        "return_waybill_id", "return_delivery_id", "return_delivery_name", "reason",
        "reason_text", "confirm_status", "confirm_time", "order_time", "order_status",
        "order_ship_status", "user_ship_status", "goods_status", "dispute_refund_status",
        "refund_status", "refund_phase", "refund_success_time", "remark",
        "ref_bought_sku_id", "exchange_goods_name", "exchange_goods_price",
        "exchange_goods_num", "exchange_sku_id", "platform_seller_id",
        "platform_seller_name", "platform_type", "create_time", "update_time", "update_on",
        "create_on", "shop_order_id", "shop_order_item_id"], refund_rows)

# ================================================================ 6. 发货任务（o_order_stocking）
# 复用 02-txn 生成的平台订单（o_order）+ 订单明细（o_order_item）
# 只取 NOW 之前下单的订单，未来的订单不该已经分配发货任务
eo_orders = []
for r in query("SELECT id,order_num,merchant_id,shop_id,shop_type,order_status,order_time,"
               "receiver_name,receiver_mobile,province,city,town,address FROM o_order "
               "WHERE order_status IN (1,2,3) AND order_time <= '%s' ORDER BY id" % NOW.strftime("%Y-%m-%d %H:%M:%S")):
    eo_orders.append({"id": int(r[0]), "num": r[1], "merchant": int(r[2]), "shop_id": int(r[3]),
                      "shop_type": int(r[4]), "status": int(r[5]),
                      "order_time": r[6], "rname": r[7], "rphone": r[8],
                      "province": r[9], "city": r[10], "town": r[11], "address": r[12]})

eo_items = {}
for r in query("SELECT id,order_id,order_num,sub_order_num,sku_id,product_id,goods_id,goods_sku_id,"
               "goods_title,goods_img,goods_num,goods_spec,sku_num,goods_price,quantity "
               "FROM o_order_item ORDER BY id"):
    eo_items.setdefault(int(r[1]), []).append({
        "id": int(r[0]),
        "order_num": r[2], "sub": r[3], "sku_id": r[4] or "0", "product_id": r[5] or "0",
        "goods_id": int(r[6]), "goods_sku_id": int(r[7]), "title": r[8], "img": r[9],
        "goods_num": r[10], "spec": r[11], "sku_num": r[12], "price": float(r[13] or 0),
        "qty": int(r[14])})

random.shuffle(eo_orders)
usable = [o for o in eo_orders if eo_items.get(o["id"])]

LOGISTICS = [("顺丰速运", "SF"), ("中通快递", "ZTO"), ("圆通速递", "YTO"),
             ("韵达快递", "YD"), ("京东物流", "JD"), ("极兔速递", "JT")]

stocking = []
stocking_items = []
st_id = 0
sti_id = 0


def add_stocking(order, ship_type, send_status, stocking_status, day_offset):
    """构造一条发货任务及其明细。"""
    global st_id, sti_id
    st_id += 1
    base = datetime.strptime(order["order_time"], "%Y-%m-%d %H:%M:%S")
    create_dt = cap(base + timedelta(hours=2 + day_offset % 6), floor=base)
    ship_dt = None
    waybill = None
    carrier_name = None
    carrier_code = None
    if send_status >= 1:
        ship_dt = cap(create_dt + timedelta(hours=6 + day_offset % 30), floor=create_dt)
        carrier_name, carrier_code = LOGISTICS[st_id % len(LOGISTICS)]
        waybill = "%s%012d" % (carrier_code, 100000000000 + st_id * 7919)

    if ship_type == 300:
        sp = suppliers[st_id % len(suppliers)]
        shipper_id = sp["id"]
        shipper_no = sp["number"]
        wh_id = sp["wh"]
        wh_type = "SUPPLIER"
        supplier_id = sp["id"]
    else:
        wh_id = MERCHANT_WH.get(order["merchant"], 1)
        shipper_id = 0
        shipper_no = None
        wh_type = "LOCAL"
        supplier_id = 0
    wh = warehouses[wh_id]

    stocking.append({
        "id": st_id,
        "shipper_id": shipper_id,
        "o_order_id": order["id"],
        "order_num": order["num"],
        "order_time": base,
        "shop_type": order["shop_type"],
        "shop_id": order["shop_id"],
        "remark": "订单 %s 发货任务，来源%s" % (order["num"], "供应商代发" if ship_type == 300 else "本地仓发货"),
        "buyer_memo": random.choice(["", "", "尽快发货", "请用顺丰", "需要发票"]),
        "seller_memo": random.choice(["", "", "仓库优先处理", "已核对库存"]),
        "send_status": send_status,
        "shipping_time": ship_dt,
        "shipping_number": ("FH%s%04d" % (base.strftime("%Y%m%d"), st_id)) if send_status >= 2 else None,
        "shipping_company": carrier_name,
        "shipping_man": random.choice(["孙立", "吴海", "郑强", "冯磊", "周海涛"]),
        "shipping_cost": round(6 + (st_id % 13) * 1.5, 2),
        "create_time": create_dt,
        "create_by": "admin",
        "update_time": ship_dt or create_dt,
        "update_by": "admin",
        "province": order["province"],
        "city": order["city"],
        "town": order["town"],
        "waybill_code": waybill,
        "waybill_status": 3 if send_status >= 2 else (1 if send_status == 1 else 0),
        "waybill_company": carrier_name,
        "order_status": order["status"],
        "stocking_status": stocking_status,
        "merchant_id": order["merchant"],
        "warehouse_type": wh_type,
        "warehouse_id": wh_id,
        "warehouse_name": wh["name"],
        "erp_push_status": 1 if send_status >= 1 else 0,
        "erp_push_result": "推送成功" if send_status >= 1 else None,
        "erp_push_param1": None,
        "erp_push_param2": None,
        "erp_push_param3": None,
        "warehouse_no": wh["no"],
        "shipper_no": shipper_no,
        "shop_no": "SHOP%03d" % order["shop_id"],
        "platform_no": "PLAT%d" % order["shop_type"],
        "type": ship_type,
        "ship_mode": 1 if st_id % 3 == 0 else 0,
        "shipping_order_code": ("YCFH%06d" % st_id) if ship_type == 300 else None,
        "shipping_company_code": carrier_code,
        "receiver_name": order["rname"],
        "receiver_mobile": order["rphone"],
        "address": order["address"],
        "order_type": 0,
        "waybill_order_id": None,
        "shipping_erp_order_code": "ERPFH%06d" % st_id,
    })

    selected = eo_items[order["id"]]
    take = 1 if len(selected) == 1 else (2 if st_id % 5 else 1)
    for it in selected[:take]:
        sti_id += 1
        qty = it["qty"]
        unshipped = 0 if send_status >= 2 else qty
        stocking_items.append({
            "id": sti_id,
            "ship_order_id": st_id,
            "order_num": order["num"],
            "sub_order_num": it["sub"],
            "o_order_id": order["id"],
            "o_order_item_id": it["id"],
            "supplier_id": supplier_id,
            "product_id": it["product_id"],
            "sku_id": it["sku_id"],
            "goods_id": it["goods_id"],
            "goods_sku_id": it["goods_sku_id"],
            "goods_name": it["title"],
            "goods_img": it["img"],
            "goods_num": it["goods_num"],
            "sku_name": it["spec"],
            "sku_code": it["sku_num"],
            "quantity": qty,
            "remark": "按订单明细拣货",
            "send_status": send_status,
            "create_time": create_dt,
            "create_by": "admin",
            "update_time": ship_dt or create_dt,
            "update_by": "admin",
            "waybill_code": waybill,
            "waybill_status": 3 if send_status >= 2 else 0,
            "refund_status": 1,
            "order_time": base,
            "stocking_status": stocking_status,
            "merchant_id": order["merchant"],
            "warehouse_type": wh_type,
            "warehouse_id": wh_id,
            "warehouse_name": wh["name"],
            "unshipped_quantity": unshipped,
            "barcode": it["sku_num"],
            "warehouse_goods_id": 0,
        })


# 本地仓任务：62 条已发货（两个页面都能看到）+ 26 条待发货/部分发货（只出现在发货记录）
for i in range(88):
    if i >= len(usable):
        break
    if i < 62:
        send_status = 2
        stocking_status = random.choices([0, 0, 0, 1, 2], weights=[7, 6, 5, 2, 1], k=1)[0]
    elif i < 72:
        send_status = 0
        stocking_status = 0
    else:
        send_status = 1
        stocking_status = random.choices([0, 1], weights=[3, 1], k=1)[0]
    add_stocking(usable[i], 0, send_status, stocking_status, i)

# 供应商代发任务：22 条，覆盖待发货/部分发货/已发货
for j in range(22):
    idx = 88 + j
    if idx >= len(usable):
        break
    send_status = [0, 2, 2, 1, 2][j % 5]
    stocking_status = [0, 2, 1, 0, 2][j % 5]
    add_stocking(usable[idx], 300, send_status, stocking_status, idx)

insert("o_order_stocking",
       ["id", "shipper_id", "o_order_id", "order_num", "order_time", "shop_type", "shop_id",
        "remark", "buyer_memo", "seller_memo", "send_status", "shipping_time",
        "shipping_number", "shipping_company", "shipping_man", "shipping_cost",
        "create_time", "create_by", "update_time", "update_by", "province", "city", "town",
        "waybill_code", "waybill_status", "waybill_company", "order_status", "stocking_status",
        "merchant_id", "warehouse_type", "warehouse_id", "warehouse_name", "erp_push_status",
        "erp_push_result", "erp_push_param1", "erp_push_param2", "erp_push_param3",
        "warehouse_no", "shipper_no", "shop_no", "platform_no", "type", "ship_mode",
        "shipping_order_code", "shipping_company_code", "receiver_name", "receiver_mobile",
        "address", "order_type", "waybill_order_id", "shipping_erp_order_code"], stocking)
insert("o_order_stocking_item",
       ["id", "ship_order_id", "order_num", "sub_order_num", "o_order_id", "o_order_item_id",
        "supplier_id", "product_id", "sku_id", "goods_id", "goods_sku_id", "goods_name",
        "goods_img", "goods_num", "sku_name", "sku_code", "quantity", "remark", "send_status",
        "create_time", "create_by", "update_time", "update_by", "waybill_code",
        "waybill_status", "refund_status", "order_time", "stocking_status", "merchant_id",
        "warehouse_type", "warehouse_id", "warehouse_name", "unshipped_quantity", "barcode",
        "warehouse_goods_id"], stocking_items)

# ================================================================ 8. 电子面单账户
# 注意：实体 ShopWaybillAccount.templateId 是 Long，template_id 列虽是 varchar(55) 也必须存数字
waybill_accounts = [
    (1, 'CN-WB_TB-SH001-100', 'CN100', '菜鸟上海转运中心', 'WB_TB', 'CN', 'CAINIAO_STD', 1001, '菜鸟标准快件', 'DIRECT', 'XH-CN-2026001', 'src-cn-100', '上海市', '上海市', '青浦区华新镇嘉松中路 1688 号', '青浦区', 2600, '青浦区华新镇嘉松中路 1688 号菜鸟产业园', 310000, '上海市', 310118, '青浦区', 0, '全镇', 310000, '上海市', 0, 'CN-SH-001', '上海青浦网点', 1, 'CAINIAO', 9001, '菜鸟网络科技有限公司', 1, 0, '华新镇', '王雅琳', '13705710001', '021-56001001', 'https://oss.example.com/waybill/cn-std-v3.pdf', 1, 100, 1, 'PT', 'CN-LOG-100', 1, 310101),
    (2, 'JD-WB_JD_ALPHA-BJ001-200', 'JD200', '京东北京通州仓配中心', 'WB_JD_ALPHA', 'JD', 'JD_STD', 2001, '京东标准快递', 'DIRECT', 'XH-JD-2026002', 'src-jd-200', '北京市', '北京市', '通州区马驹桥镇物流基地兴贸三街 12 号', '通州区', 1800, '通州区马驹桥镇兴贸三街 12 号京东物流园', 110000, '北京市', 110112, '通州区', 0, '马驹桥镇', 110000, '北京市', 0, 'JD-BJ-001', '北京通州分拣中心', 1, 'JDWL', 9002, '京东物流', 1, 0, '马驹桥镇', '李成钢', '13800138002', '010-56002002', 'https://oss.example.com/waybill/jd-std-v2.pdf', 2, 200, 1, 'PT', 'JD-LOG-200', 1, 310102),
    (3, 'PDD-WB_PDD-ZS001-300', 'PD300', '拼多多浙江金华分拨中心', 'WB_PDD', 'PDD', 'PDD_STD', 3001, '拼多多标准件', 'JOIN', 'XH-PDD-2026003', 'src-pdd-300', '浙江省', '金华市', '义乌市北苑街道雪峰西路 968 号', '义乌市', 1500, '义乌市北苑街道雪峰西路 968 号电商分拨园', 330700, '金华市', 330782, '义乌市', 0, '北苑街道', 330000, '浙江省', 0, 'PDD-JH-001', '金华义乌分拨点', 1, 'PDDLOG', 9003, '拼多多电子面单服务', 1, 0, '北苑街道', '陈慧敏', '13905790003', '0579-56003003', 'https://oss.example.com/waybill/pdd-std-v1.pdf', 3, 300, 2, 'PT', 'PDD-LOG-300', 1, 310103),
    (4, 'DY-WB_DY-GZ001-400', 'DY400', '抖音广州白云云打印中心', 'WB_DY', 'DY', 'DY_STD', 4001, '抖音标准快递', 'DIRECT', 'XH-DY-2026004', 'src-dy-400', '广东省', '广州市', '白云区太和镇大源路 66 号', '白云区', 2000, '白云区太和镇大源路 66 号抖音电商仓', 440100, '广州市', 440111, '白云区', 0, '太和镇', 440000, '广东省', 0, 'DY-GZ-001', '广州白云云打印点', 1, 'DYEWB', 9004, '抖音电子面单', 1, 0, '太和镇', '林志强', '13609790004', '020-56004004', 'https://oss.example.com/waybill/dy-std-v1.pdf', 4, 400, 2, 'PT', 'DY-LOG-400', 1, 310104),
    (5, 'KS-WB_KS-SH002-600', 'KS600', '快手上海奉贤云打印中心', 'WB_KS', 'KS', 'KS_STD', 5001, '快手标准快递', 'JOIN', 'XH-KS-2026005', 'src-ks-600', '上海市', '上海市', '奉贤区金汇镇大叶公路 6999 号', '奉贤区', 1200, '奉贤区金汇镇大叶公路 6999 号快手仓', 310120, '奉贤区', 0, '奉贤区', 0, '金汇镇', 310000, '上海市', 0, 'KS-SH-002', '上海奉贤云打印点', 1, 'KSEWB', 9005, '快手电子面单', 1, 0, '金汇镇', '赵天宇', '13701890005', '021-56005005', 'https://oss.example.com/waybill/ks-std-v1.pdf', 5, 600, 3, 'PT', 'KS-LOG-600', 1, 310105),
    (6, 'WX-WB_WX_VS-SH003-500', 'WX500', '微信视频号云打印中心', 'WB_WX_VS', 'WX', 'WX_VS_STD', 5002, '微信小店标准快递', 'DIRECT', 'XH-WX-2026006', 'src-wx-500', '上海市', '上海市', '闵行区莘庄镇七莘路 1366 号', '闵行区', 900, '闵行区莘庄镇七莘路 1366 号万象城 B 座', 310112, '闵行区', 0, '莘庄镇', 0, '莘庄镇', 310000, '上海市', 0, 'WX-SH-003', '上海莘庄打印点', 1, 'WXEWB', 9006, '微信小店电子面单', 1, 0, '莘庄镇', '周雨桐', '13917890006', '021-56006006', 'https://oss.example.com/waybill/wx-vs-v1.pdf', 6, 500, 3, 'PT', 'WX-LOG-500', 1, 310106),
]
WAYBILL_COLS = ["id", "key1", "code", "name", "waybill_platform_type", "brand_code",
    "ref_logistics_code", "ref_logistics_id", "ref_logistics_name", "ref_logistics_type",
    "settle_account", "source_key", "province", "city", "detail", "district", "num",
    "address_address", "city_id", "city_name", "country_id", "country_name",
    "countryside_id", "countryside_name", "province_id", "province_name", "amount",
    "branch_code", "branch_name", "operation_type", "provider_code", "provider_id",
    "provider_name", "provider_type", "support_cod", "town", "deliver_name",
    "deliver_mobile", "deliver_phone", "template_url", "shop_id", "shop_type",
    "merchant_id", "type", "outer_logistics_id", "support_offline", "template_id"]
insert("oms_shop_waybill_account", WAYBILL_COLS,
       [dict(zip(WAYBILL_COLS, row)) for row in waybill_accounts])

# ---------------------------------------------------------------- 输出
with open(OUT_PATH, "w", encoding="utf-8") as f:
    f.write("\n".join(out))
    f.write("\n")

print("输出文件: %s" % OUT_PATH)
print("erp_supplier_product      : %d" % len(sup_products))
print("erp_supplier_product_item : %d" % len(sup_items))
print("erp_supplier_goods_price  : %d" % len(sup_prices))
print("oms_shop_goods            : %d" % len(shop_goods))
print("oms_shop_goods_sku        : %d" % len(shop_goods_sku))
print("erp_purchase_logistics    : %d" % len(carrier_rows))
print("oms_shop_order            : %d" % len(orders))
print("oms_shop_order_item       : %d" % len(order_items))
print("oms_shop_refund           : %d" % len(refund_rows))
print("o_order_stocking          : %d" % len(stocking))
print("o_order_stocking_item     : %d" % len(stocking_items))
print("oms_shop_waybill_account  : %d" % len(waybill_accounts))
