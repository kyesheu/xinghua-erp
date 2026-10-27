# -*- coding: utf-8 -*-
"""生成兴华 ERP 演示交易数据（采购 / 出入库 / 订单 / 发货 / 退款售后）。输出 seed_txn.sql。"""
import random
import subprocess
from datetime import datetime, timedelta

random.seed(1113)
START = datetime(2026, 9, 15)
DAYS = 60

def query(sql):
    out = subprocess.run(["docker", "exec", "xinghua-erp-mysql-1", "mysql",
                          "-uroot", "-pxinghua-erp-dev-only", "-N", "--default-character-set=utf8mb4",
                          "-e", sql, "xinghua-erp"], capture_output=True)
    text = out.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in text.strip().split("\n") if line.strip()]

def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"

out = []
def emit(s):
    out.append(s)

def insert(table, cols, rows):
    rows = [r for r in rows if r]
    if not rows:
        return
    emit("INSERT INTO %s (%s) VALUES\n%s;" % (
        table, ",".join(cols),
        ",\n".join("(" + ",".join(q(r.get(c)) for c in cols) + ")" for r in rows)))

emit("SET NAMES utf8mb4;")
emit("SET FOREIGN_KEY_CHECKS=0;")
for t in ["o_order", "o_order_item", "o_refund", "o_after_sale", "erp_sales_after", "o_shipment",
          "o_shipment_item", "erp_purchase_order", "erp_purchase_order_item", "erp_stock_in",
          "erp_stock_in_item", "erp_stock_out", "erp_stock_out_item", "o_refund_after_sale"]:
    emit("DELETE FROM %s;" % t)

# ---------- 读取主数据 ----------
skus = []
for r in query("SELECT s.id,s.goods_id,s.sku_code,s.sku_name,s.retail_price,s.pur_price,s.merchant_id,"
               "g.name,g.image,g.category_id,g.low_qty FROM o_goods_sku s JOIN o_goods g ON g.id=s.goods_id"):
    skus.append({"id": int(r[0]), "goods_id": int(r[1]), "code": r[2], "sku_name": r[3],
                 "price": float(r[4]), "cost": float(r[5]), "merchant": int(r[6]),
                 "goods_name": r[7], "image": r[8], "category": int(r[9]), "low": int(r[10])})
shops = [{"id": int(r[0]), "merchant": int(r[1]), "type": int(r[2]), "name": r[3]}
         for r in query("SELECT id,merchant_id,type,name FROM o_shop")]
sups = [{"id": int(r[0]), "merchant": int(r[1]), "wh": int(r[2])} for r in query(
    "SELECT id,merchant_id,warehouse_id FROM erp_supplier")]
whs = {int(r[0]): r[1] for r in query("SELECT id,warehouse_name FROM erp_warehouse")}

by_merchant = {}
for s in skus:
    by_merchant.setdefault(s["merchant"], []).append(s)

CITIES = [("浙江省", "杭州市", "余杭区", "文一西路"), ("浙江省", "杭州市", "西湖区", "文三路"),
          ("广东省", "深圳市", "南山区", "科苑路"), ("广东省", "广州市", "天河区", "体育西路"),
          ("江苏省", "南京市", "鼓楼区", "中山北路"), ("上海市", "上海市", "浦东新区", "张江路"),
          ("四川省", "成都市", "武侯区", "人民南路"), ("北京市", "北京市", "朝阳区", "建国路"),
          ("浙江省", "金华市", "义乌市", "稠州北路"), ("湖北省", "武汉市", "洪山区", "珞喻路")]
SURNAME = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许"
GIVEN = ["伟", "芳", "娜", "敏", "静", "强", "磊", "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "霞"]
LOGISTICS = [("顺丰速运", "SF"), ("中通快递", "ZTO"), ("圆通速递", "YTO"), ("韵达快递", "YD"), ("京东物流", "JD")]
REASONS = ["尺码不合适", "商品与描述不符", "收到商品破损", "七天无理由退货", "发错货", "质量问题"]

def person(i):
    return SURNAME[i % len(SURNAME)] + GIVEN[i % len(GIVEN)]

# ---------- 订单 ----------
orders, items, shipments, ship_items = [], [], [], []
order_id = 0
item_id = 0
ship_id = 0
ship_item_id = 0
seq = 0
for day in range(DAYS):
    d = START + timedelta(days=day)
    weekend = d.weekday() >= 5
    base = 2 if weekend else 4
    if d.month == 11 and d.day == 11:
        base = 18
    elif d.month == 10 and d.day <= 3:
        base = 9
    elif d.month == 9 and d.day >= 28:
        base = 7
    count = max(1, base + random.randint(-1, 2))
    for _ in range(count):
        order_id += 1
        seq += 1
        shop = random.choice(shops)
        pool = by_merchant[shop["merchant"]]
        n_item = random.choice([1, 1, 2, 2, 3])
        picked = random.sample(pool, min(n_item, len(pool)))
        hour = random.choices([9, 10, 11, 12, 14, 15, 16, 17, 19, 20, 21, 22],
                              weights=[6, 8, 10, 8, 7, 8, 9, 8, 10, 12, 10, 5])[0]
        ot = d.replace(hour=hour, minute=random.randint(0, 59), second=random.randint(0, 59))
        order_num = "XH%s%04d" % (ot.strftime("%Y%m%d"), seq)
        status = random.choices([3, 2, 1, 31, 101, 11, 13, 21],
                                weights=[56, 15, 10, 4, 5, 3, 3, 4])[0]
        c = CITIES[order_id % len(CITIES)]
        receiver = person(order_id)
        goods_amount = 0.0
        subtotal = 0.0
        rows = []
        for k, sku in enumerate(picked):
            item_id += 1
            qty = random.choices([1, 1, 2, 2, 3], weights=[40, 20, 20, 12, 8])[0]
            price = round(sku["price"] * random.choice([0.85, 0.9, 0.95, 1.0]), 2)
            amount = round(price * qty, 2)
            subtotal += amount
            rows.append({"id": item_id, "order_id": order_id, "order_num": order_num,
                         "sub_order_num": order_num + "-%d" % (k + 1), "sku": sku, "qty": qty,
                         "price": price, "amount": amount, "order_time": ot, "shop": shop})
        post_fee = random.choice([0, 0, 0, 6, 8, 10, 12])
        goods_amount = round(subtotal, 2)
        discount = round(subtotal * random.choice([0, 0, 0, 0.05, 0.1]), 2)
        amount = round(goods_amount + post_fee, 2)
        paid = status not in (11, 13, 21)
        payment = round(amount - discount, 2) if paid else 0.0
        ships = status in (2, 3, 31, 101)
        company, code = random.choice(LOGISTICS)
        waybill = "%s%012d" % (code, random.randint(100000000000, 999999999999)) if ships else None
        orders.append({"id": order_id, "order_num": order_num, "shop": shop, "status": status,
                       "goods_amount": goods_amount, "post_fee": post_fee, "amount": amount,
                       "payment": payment, "receiver": receiver, "city": c, "order_time": ot,
                       "ships": ships, "waybill": waybill, "company": company if ships else None,
                       "merchant_amount": round(payment * 0.95, 2), "seq": order_id})
        for r in rows:
            r["status"] = status
            r["ships"] = ships
            r["waybill"] = waybill
            items.append(r)
        if ships:
            ship_id += 1
            ship_time = ot + timedelta(days=random.randint(1, 3), hours=random.randint(0, 8))
            if ship_time > datetime(2026, 11, 13, 20, 0):
                ship_time = ot + timedelta(hours=6)
            shipments.append({"id": ship_id, "shop_id": shop["id"], "order_num": order_num,
                              "sub": ",".join(r["sub_order_num"] for r in rows), "receiver": receiver,
                              "mobile": "1%02d%08d" % (30 + order_id % 60, order_id * 37 % 99999999),
                              "city": c, "company": company, "code": code, "waybill": waybill,
                              "fee": post_fee, "time": ship_time, "merchant": shop["merchant"],
                              "status": 1})
            for r in rows:
                ship_item_id += 1
                ship_items.append({"id": ship_item_id, "ship_id": ship_id, "order_id": order_id,
                                   "item_id": r["id"], "order_num": order_num,
                                   "sub": r["sub_order_num"]})

order_cols = ["id", "order_mode", "order_num", "shop_type", "shop_id", "merchant_id", "order_status",
              "goods_amount", "post_fee", "amount", "payment", "receiver_name", "receiver_mobile",
              "address", "province", "city", "town", "order_time", "erp_push_status", "erp_push_time",
              "create_time", "create_by", "update_time", "update_by", "platform_discount", "seller_discount",
              "dist_status", "ship_status", "waybill_code", "waybill_company", "waybill_status",
              "has_gift", "merchant_amount", "settlement_status", "buyer_memo", "remark", "tag"]
order_rows = []
for o in orders:
    o["mobile"] = "1%02d%08d" % (30 + o["id"] % 60, o["id"] * 37 % 99999999)
    o["address"] = "%s%s号%d栋%d单元" % (o["city"][3], random.randint(1, 999), random.randint(1, 20),
                                     random.randint(101, 2508))
    order_rows.append({"id": o["id"], "order_mode": 1, "order_num": o["order_num"],
                       "shop_type": o["shop"]["type"], "shop_id": o["shop"]["id"],
                       "merchant_id": o["shop"]["merchant"], "order_status": o["status"],
                       "goods_amount": o["goods_amount"], "post_fee": o["post_fee"], "amount": o["amount"],
                       "payment": o["payment"], "receiver_name": o["receiver"],
                       "receiver_mobile": o["mobile"], "address": o["address"],
                       "province": o["city"][0], "city": o["city"][1], "town": o["city"][2],
                       "order_time": o["order_time"], "erp_push_status": 1 if o["status"] in (1, 2, 3, 31, 101) else 0,
                       "erp_push_time": o["order_time"], "create_time": o["order_time"], "create_by": "sync",
                       "update_time": o["order_time"], "update_by": "sync",
                       "platform_discount": 0, "seller_discount": 0, "dist_status": 1 if o["ships"] else 0,
                       "ship_status": 1 if o["ships"] else 0, "waybill_code": o["waybill"],
                       "waybill_company": o["company"], "waybill_status": 1 if o["status"] == 3 else 0,
                       "has_gift": 0, "merchant_amount": o["merchant_amount"], "settlement_status": 1 if o["status"] == 3 else 0,
                       "buyer_memo": random.choice(["", "", "请尽快发货", "需要发票", "工作日送公司"]),
                       "remark": "演示订单", "tag": random.choice(["", "老客", "大促"])})
insert("o_order", order_cols, order_rows)

item_rows = []
for r in items:
    sku = r["sku"]
    item_rows.append({"id": r["id"], "order_id": r["order_id"], "order_num": r["order_num"],
                      "sub_order_num": r["sub_order_num"], "sku_id": str(sku["id"]),
                      "product_id": str(sku["goods_id"]), "goods_id": sku["goods_id"],
                      "goods_sku_id": sku["id"], "goods_title": sku["goods_name"],
                      "goods_img": sku["image"], "goods_num": sku["code"][:8],
                      "goods_spec": sku["sku_name"], "sku_num": sku["code"],
                      "goods_price": r["price"], "item_amount": r["amount"], "discount_amount": 0,
                      "payment": r["amount"], "quantity": r["qty"], "remark": "",
                      "refund_count": 0, "refund_status": 1, "has_push_erp": 1,
                      "create_time": r["order_time"], "create_by": "sync",
                      "update_time": r["order_time"], "update_by": "sync",
                      "ship_type": 1, "ship_status": 1 if r["ships"] else 0,
                      "waybill_code": r["waybill"], "waybill_company": "",
                      "shipper_id": 1, "order_time": r["order_time"],
                      "merchant_id": r["shop"]["merchant"], "shop_type": r["shop"]["type"],
                      "shop_id": r["shop"]["id"], "is_gift": 0, "shipper_name": "华东中心仓"})
insert("o_order_item",
       ["id", "order_id", "order_num", "sub_order_num", "sku_id", "product_id", "goods_id", "goods_sku_id",
        "goods_title", "goods_img", "goods_num", "goods_spec", "sku_num", "goods_price", "item_amount",
        "discount_amount", "payment", "quantity", "remark", "refund_count", "refund_status",
        "has_push_erp", "create_time", "create_by", "update_time", "update_by", "ship_type", "ship_status",
        "waybill_code", "waybill_company", "shipper_id", "order_time", "merchant_id", "shop_type",
        "shop_id", "is_gift", "shipper_name"], item_rows)

insert("o_shipment",
       ["id", "shop_id", "shipping_type", "order_nums", "sub_order_nums", "receiver_name", "receiver_mobile",
        "province", "city", "town", "address", "logistics_company", "logistics_company_code", "waybill_code",
        "shipping_fee", "shipping_time", "shipping_operator", "shipping_status", "package_weight",
        "package_length", "package_width", "package_height", "create_time", "create_by", "shipper_id",
        "merchant_id", "type", "ship_mode"],
       [{"id": s["id"], "shop_id": s["shop_id"], "shipping_type": 1, "order_nums": s["order_num"],
         "sub_order_nums": s["sub"], "receiver_name": s["receiver"], "receiver_mobile": s["mobile"],
         "province": s["city"][0], "city": s["city"][1], "town": s["city"][2],
         "address": s["city"][3] + "%d号" % (s["id"] * 7 % 900 + 1), "logistics_company": s["company"],
         "logistics_company_code": s["code"], "waybill_code": s["waybill"], "shipping_fee": s["fee"],
         "shipping_time": s["time"], "shipping_operator": "wh_keeper", "shipping_status": 1,
         "package_weight": round(0.5 + s["id"] % 5 * 0.3, 2), "package_length": 30, "package_width": 20,
         "package_height": 15, "create_time": s["time"], "create_by": "wh_keeper", "shipper_id": 1,
         "merchant_id": s["merchant"], "type": 0, "ship_mode": 0} for s in shipments])
insert("o_shipment_item", ["id", "shipping_id", "order_id", "order_item_id", "order_num", "sub_order_num"],
       [{"id": s["id"], "shipping_id": s["ship_id"], "order_id": s["order_id"],
         "order_item_id": s["item_id"], "order_num": s["order_num"], "sub_order_num": s["sub"]}
        for s in ship_items])

# ---------- 退款与售后 ----------
refunds, afters, sales_afters = [], [], []
refund_id = 0
after_id = 0
sales_after_id = 0
candidates = [o for o in orders if o["status"] in (2, 3, 31)]
random.shuffle(candidates)
done = candidates[:32]
processing = candidates[32:40]
for o in done + processing:
    refund_id += 1
    it = next(r for r in items if r["order_id"] == o["id"])
    sku = it["sku"]
    is_done = o in done
    rtype = random.choice([1, 10, 11])
    rtime = o["order_time"] + timedelta(days=random.randint(1, 6), hours=random.randint(1, 10))
    if rtime > datetime(2026, 11, 13, 21, 0):
        rtime = datetime(2026, 11, 13, 20, 0)
    fee = round(it["amount"] if rtype == 1 else it["amount"] * random.choice([0.5, 1.0]), 2)
    refund_num = "RF%s%04d" % (rtime.strftime("%Y%m%d"), refund_id)
    status = 4 if is_done else random.choice([2, 3])
    refunds.append({"id": refund_id, "num": refund_num, "type": rtype, "shop": o["shop"],
                    "order_amount": o["payment"], "fee": fee, "reason": random.choice(REASONS),
                    "order_num": o["order_num"], "sub": it["sub_order_num"], "sku": sku,
                    "qty": it["qty"], "status": status, "time": rtime, "item": it,
                    "returned": 1 if rtype == 11 else 0})
    after_id += 1
    afters.append({"id": after_id, "type": rtype, "shop": o["shop"], "refund_id": refund_id,
                   "refund_num": refund_num, "order_num": o["order_num"], "sub": it["sub_order_num"],
                   "order_id": o["id"], "item_id": it["id"], "sku": sku, "qty": it["qty"],
                   "status": status, "time": rtime, "returned": 1 if rtype == 11 else 0, "o": o})
    sales_after_id += 1
    sales_afters.append({"id": sales_after_id, "type": rtype, "shop": o["shop"], "refund_num": refund_num,
                         "order_id": o["id"], "item_id": it["id"], "order_num": o["order_num"],
                         "status": status, "sku": sku, "qty": it["qty"], "fee": fee,
                         "returned": 1 if rtype == 11 else 0, "time": rtime})

insert("o_refund",
       ["id", "refund_num", "refund_type", "shop_id", "shop_type", "order_amount", "refund_fee",
        "refund_reason", "order_num", "order_item_num", "sku_id", "goods_id", "goods_sku_id", "sku_num",
        "has_good_return", "goods_name", "goods_sku", "goods_image", "quantity",
        "return_logistics_company", "return_logistics_code", "status", "create_time", "create_by",
        "update_time", "update_by", "erp_push_status", "merchant_id", "erp_status", "has_processing",
        "process_type", "platform_status", "platform_status_text"],
       [{"id": r["id"], "refund_num": r["num"], "refund_type": r["type"], "shop_id": r["shop"]["id"],
         "shop_type": r["shop"]["type"], "order_amount": r["order_amount"], "refund_fee": r["fee"],
         "refund_reason": r["reason"], "order_num": r["order_num"], "order_item_num": r["sub"],
         "sku_id": str(r["sku"]["id"]), "goods_id": r["sku"]["goods_id"],
         "goods_sku_id": r["sku"]["id"], "sku_num": r["sku"]["code"],
         "has_good_return": r["returned"], "goods_name": r["sku"]["goods_name"],
         "goods_sku": r["sku"]["sku_name"], "goods_image": r["sku"]["image"], "quantity": r["qty"],
         "return_logistics_company": "顺丰速运" if r["returned"] else None,
         "return_logistics_code": "SF%012d" % random.randint(100000000000, 999999999999) if r["returned"] else None,
         "status": r["status"], "create_time": r["time"], "create_by": "as_cs",
         "update_time": r["time"], "update_by": "as_cs", "erp_push_status": 1,
         "merchant_id": r["shop"]["merchant"], "erp_status": r["status"], "has_processing": 0,
         "process_type": "退货退款" if r["type"] == 11 else "仅退款",
         "platform_status": str(r["status"]), "platform_status_text": "退款成功" if r["status"] == 4 else "处理中"}
        for r in refunds])

insert("o_after_sale",
       ["id", "type", "shop_id", "shop_type", "refund_id", "refund_num", "order_num", "sub_order_num",
        "o_order_id", "o_order_item_id", "sku_id", "quantity", "title", "img", "sku_info", "sku_code",
        "o_goods_id", "o_goods_sku_id", "has_goods_send", "return_info", "return_logistics_code",
        "return_logistics_company", "receiver_name", "receiver_tel", "receiver_province", "receiver_city",
        "receiver_town", "receiver_address", "status", "remark", "create_time", "create_by"],
       [{"id": a["id"], "type": a["type"], "shop_id": a["shop"]["id"], "shop_type": a["shop"]["type"],
         "refund_id": a["refund_id"], "refund_num": a["refund_num"], "order_num": a["order_num"],
         "sub_order_num": a["sub"], "o_order_id": str(a["order_id"]), "o_order_item_id": str(a["item_id"]),
         "sku_id": a["sku"]["id"], "quantity": a["qty"], "title": a["sku"]["goods_name"],
         "img": a["sku"]["image"], "sku_info": a["sku"]["sku_name"], "sku_code": a["sku"]["code"],
         "o_goods_id": a["sku"]["goods_id"], "o_goods_sku_id": a["sku"]["id"],
         "has_goods_send": a["returned"], "return_info": "客户已寄回" if a["returned"] else "无需退货",
         "return_logistics_code": "SF%012d" % random.randint(100000000000, 999999999999) if a["returned"] else None,
         "return_logistics_company": "顺丰速运" if a["returned"] else None,
         "receiver_name": a["o"]["receiver"], "receiver_tel": a["o"]["mobile"],
         "receiver_province": a["o"]["city"][0], "receiver_city": a["o"]["city"][1],
         "receiver_town": a["o"]["city"][2], "receiver_address": a["o"]["address"],
         "status": a["status"], "remark": "演示售后单", "create_time": a["time"], "create_by": "as_cs"}
        for a in afters])

insert("erp_sales_after",
       ["id", "type", "shop_id", "merchant_id", "refund_num", "order_id", "order_item_id", "order_num",
        "status", "remark", "create_time", "create_by", "sku_id", "goods_id", "goods_sku_id",
        "goods_title", "goods_img", "goods_num", "goods_spec", "sku_num", "item_amount", "refund_fee",
        "has_good_return", "refund_quantity", "return_logistics_company", "return_logistics_code"],
       [{"id": a["id"], "type": a["type"], "shop_id": a["shop"]["id"],
         "merchant_id": a["shop"]["merchant"], "refund_num": a["refund_num"],
         "order_id": str(a["order_id"]), "order_item_id": str(a["item_id"]), "order_num": a["order_num"],
         "status": a["status"], "remark": "演示售后", "create_time": a["time"], "create_by": "as_cs",
         "sku_id": str(a["sku"]["id"]), "goods_id": a["sku"]["goods_id"],
         "goods_sku_id": a["sku"]["id"], "goods_title": a["sku"]["goods_name"],
         "goods_img": a["sku"]["image"], "goods_num": a["sku"]["code"][:8],
         "goods_spec": a["sku"]["sku_name"], "sku_num": a["sku"]["code"],
         "item_amount": a["fee"], "refund_fee": a["fee"], "has_good_return": a["returned"],
         "refund_quantity": a["qty"],
         "return_logistics_company": "顺丰速运" if a["returned"] else None,
         "return_logistics_code": "SF%012d" % random.randint(100000000000, 999999999999) if a["returned"] else None}
        for a in sales_afters])

# ---------- 采购 ----------
pos, po_items, stock_ins, stock_in_items = [], [], [], []
po_id = 0
po_item_id = 0
si_id = 0
si_item_id = 0
for k in range(28):
    po_id += 1
    sup = random.choice(sups)
    pool = [s for s in skus if s["merchant"] == sup["merchant"]]
    if not pool:
        pool = skus
    d = START + timedelta(days=random.randint(0, DAYS - 4), hours=random.randint(9, 17),
                          minutes=random.randint(0, 59))
    po_num = "CG%s%03d" % (d.strftime("%Y%m%d"), po_id)
    n = random.choice([2, 2, 3, 3, 4])
    picked = random.sample(pool, min(n, len(pool)))
    total = 0.0
    rows = []
    for p in picked:
        po_item_id += 1
        qty = random.choice([20, 30, 50, 80, 100, 200])
        price = round(p["cost"] * random.choice([0.95, 1.0, 1.02]), 2)
        total += qty * price
        rows.append({"id": po_item_id, "order_id": po_id, "order_num": po_num, "date": d.date(),
                     "sku": p, "qty": qty, "price": price, "amount": round(qty * price, 2),
                     "merchant": sup["merchant"], "shop_id": 0})
    status = random.choices([2, 2, 1, 0, 3], weights=[45, 20, 15, 10, 10])[0]
    pos.append({"id": po_id, "sup": sup, "num": po_num, "date": d, "amount": round(total, 2),
                "status": status, "wh": sup["wh"], "rows": rows})
    po_items.extend(rows)
    if status in (1, 2):
        si_id += 1
        si_num = "RK%s%03d" % (d.strftime("%Y%m%d"), si_id)
        si_status = 2 if status == 2 else random.choice([0, 1])
        stock_ins.append({"id": si_id, "num": si_num, "type": 1, "source_no": po_num, "source_id": po_id,
                          "date": d + timedelta(days=1), "status": si_status, "merchant": sup["merchant"],
                          "wh": sup["wh"], "po": po_id})
        for r in rows:
            si_item_id += 1
            stock_in_items.append({"id": si_item_id, "si_id": si_id, "type": 1, "source_no": po_num,
                                   "source_id": po_id, "source_item_id": r["id"], "sku": r["sku"],
                                   "qty": r["qty"], "price": r["price"], "merchant": sup["merchant"],
                                   "wh": sup["wh"], "in_qty": r["qty"] if si_status == 2 else 0})

insert("erp_purchase_order",
       ["id", "supplier_id", "order_num", "order_date", "order_time", "order_amount", "ship_amount",
        "remark", "status", "audit_user", "audit_time", "supplier_confirm_time", "supplier_delivery_time",
        "received_time", "stock_in_time", "create_by", "create_time", "update_by", "update_time",
        "merchant_id", "warehouse_id", "warehouse_type", "warehouse_name", "shop_id", "purchase_type"],
       [{"id": p["id"], "supplier_id": p["sup"]["id"], "order_num": p["num"], "order_date": p["date"].date(),
         "order_time": int(p["date"].timestamp()), "order_amount": p["amount"], "ship_amount": 0,
         "remark": "演示采购单", "status": p["status"], "audit_user": "buyer01",
         "audit_time": int(p["date"].timestamp()) + 3600, "supplier_confirm_time": p["date"] + timedelta(hours=5),
         "supplier_delivery_time": p["date"] + timedelta(days=1),
         "received_time": p["date"] + timedelta(days=2) if p["status"] in (1, 2) else None,
         "stock_in_time": p["date"] + timedelta(days=2) if p["status"] == 2 else None,
         "create_by": "buyer01", "create_time": p["date"], "update_by": "buyer01", "update_time": p["date"],
         "merchant_id": p["sup"]["merchant"], "warehouse_id": p["wh"], "warehouse_type": "1",
         "warehouse_name": whs.get(p["wh"]), "shop_id": 0, "purchase_type": 1} for p in pos])

insert("erp_purchase_order_item",
       ["id", "order_id", "order_num", "trans_type", "amount", "order_date", "remark", "goods_id", "goods_num",
        "goods_name", "spec_id", "spec_num", "color_value", "color_image", "size_value", "price", "dis_amount",
        "dis_rate", "quantity", "inQty", "status", "merchant_id", "shop_id", "inventory_mode"],
       [{"id": r["id"], "order_id": r["order_id"], "order_num": r["order_num"], "trans_type": "1",
         "amount": r["amount"], "order_date": r["date"], "remark": "", "goods_id": r["sku"]["goods_id"],
         "goods_num": r["sku"]["code"][:8], "goods_name": r["sku"]["goods_name"],
         "spec_id": r["sku"]["id"], "spec_num": r["sku"]["code"],
         "color_value": r["sku"]["sku_name"].split("/")[0], "color_image": r["sku"]["image"],
         "size_value": r["sku"]["sku_name"].split("/")[-1], "price": r["price"], "dis_amount": 0,
         "dis_rate": 0, "quantity": r["qty"], "inQty": r["qty"], "status": 1,
         "merchant_id": r["merchant"], "shop_id": 0, "inventory_mode": 0} for r in po_items])

insert("erp_stock_in",
       ["id", "stock_in_num", "stock_in_type", "source_no", "source_id", "remark", "stock_in_operator_id",
        "stock_in_operator", "stock_in_time", "status", "create_by", "create_time", "update_by",
        "update_time", "merchant_id", "warehouse_id", "warehouse_type", "warehouse_name", "warehouse_no",
        "sender_name", "sender_mobile", "sender_address", "push_status", "shop_id"],
       [{"id": s["id"], "stock_in_num": s["num"], "stock_in_type": 1, "source_no": s["source_no"],
         "source_id": s["source_id"], "remark": "采购入库", "stock_in_operator_id": 5,
         "stock_in_operator": "wh_keeper", "stock_in_time": s["date"], "status": s["status"],
         "create_by": "wh_keeper", "create_time": s["date"], "update_by": "wh_keeper",
         "update_time": s["date"], "merchant_id": s["merchant"], "warehouse_id": s["wh"],
         "warehouse_type": "1", "warehouse_name": whs.get(s["wh"]), "warehouse_no": "WH-00%d" % s["wh"],
         "sender_name": "供应商仓库", "sender_mobile": "1390234000%d" % (s["id"] % 9),
         "sender_address": "供应商发货地址", "push_status": 1, "shop_id": 0} for s in stock_ins])

insert("erp_stock_in_item",
       ["id", "stock_in_id", "stock_in_type", "source_no", "source_id", "source_item_id", "goods_id",
        "goods_num", "goods_name", "goods_image", "sku_id", "sku_code", "sku_name", "quantity",
        "pur_price", "in_quantity", "remark", "status", "create_by", "create_time", "update_by",
        "update_time", "warehouse_id", "position_id", "merchant_id", "shop_id", "inventory_mode"],
       [{"id": r["id"], "stock_in_id": r["si_id"], "stock_in_type": 1, "source_no": r["source_no"],
         "source_id": r["source_id"], "source_item_id": r["source_item_id"],
         "goods_id": r["sku"]["goods_id"], "goods_num": r["sku"]["code"][:8],
         "goods_name": r["sku"]["goods_name"], "goods_image": r["sku"]["image"],
         "sku_id": r["sku"]["id"], "sku_code": r["sku"]["code"], "sku_name": r["sku"]["sku_name"],
         "quantity": r["qty"], "pur_price": r["price"], "in_quantity": r["in_qty"], "remark": "",
         "status": 1, "create_by": "wh_keeper", "create_time": datetime(2026, 11, 1, 10, 0),
         "warehouse_id": r["wh"], "position_id": 0, "merchant_id": r["merchant"], "shop_id": 0,
         "inventory_mode": 0} for r in stock_in_items])

# ---------- 出库 ----------
stock_outs, stock_out_items = [], []
so_id = 0
so_item_id = 0
ship_orders = [o for o in orders if o["ships"]][:26]
for o in ship_orders:
    so_id += 1
    d = o["order_time"] + timedelta(hours=random.randint(2, 20))
    so_num = "CK%s%03d" % (d.strftime("%Y%m%d"), so_id)
    status = random.choices([2, 2, 2, 1, 0], weights=[60, 20, 10, 6, 4])[0]
    wh = 1 if o["shop"]["merchant"] == 1 else (2 if o["shop"]["merchant"] == 2 else 3)
    rows = [r for r in items if r["order_id"] == o["id"]]
    stock_outs.append({"id": so_id, "num": so_num, "source": o["order_num"], "source_id": o["id"],
                       "shop": o["shop"], "date": d, "status": status, "wh": wh, "rows": rows})
    for r in rows:
        so_item_id += 1
        stock_out_items.append({"id": so_item_id, "so_id": so_id, "source_order_id": o["id"],
                                "source_item_id": r["id"], "source_num": o["order_num"],
                                "sku": r["sku"], "qty": r["qty"], "shop": o["shop"], "wh": wh,
                                "status": 2 if status == 2 else 0, "date": d})

insert("erp_stock_out",
       ["id", "out_num", "source_num", "source_id", "type", "shop_id", "goods_unit", "spec_unit",
        "spec_unit_total", "out_total", "remark", "status", "print_status", "print_time", "out_time",
        "complete_time", "operator_id", "operator_name", "create_time", "create_by", "update_time",
        "update_by", "merchant_id", "warehouse_id", "warehouse_name"],
       [{"id": s["id"], "out_num": s["num"], "source_num": s["source"], "source_id": s["source_id"],
         "type": 1, "shop_id": s["shop"]["id"], "goods_unit": sum(r["qty"] for r in s["rows"]),
         "spec_unit": sum(r["qty"] for r in s["rows"]), "spec_unit_total": sum(r["qty"] for r in s["rows"]),
         "out_total": sum(r["qty"] for r in s["rows"]), "remark": "销售出库", "status": s["status"],
         "print_status": 1, "print_time": s["date"], "out_time": s["date"],
         "complete_time": s["date"] if s["status"] == 2 else None, "operator_id": 5,
         "operator_name": "wh_keeper", "create_time": s["date"], "create_by": "wh_keeper",
         "update_time": s["date"], "update_by": "wh_keeper", "merchant_id": s["shop"]["merchant"],
         "warehouse_id": s["wh"], "warehouse_name": whs.get(s["wh"])} for s in stock_outs])

insert("erp_stock_out_item",
       ["id", "type", "entry_id", "source_order_id", "source_order_item_id", "source_order_num",
        "original_quantity", "out_quantity", "complete_time", "status", "warehouse_id", "position_id",
        "goods_id", "goods_num", "goods_name", "goods_image", "sku_id", "sku_code", "sku_name",
        "pur_price", "shop_id", "merchant_id", "inventory_mode"],
       [{"id": r["id"], "type": 1, "entry_id": r["so_id"], "source_order_id": r["source_order_id"],
         "source_order_item_id": r["source_item_id"], "source_order_num": r["source_num"],
         "original_quantity": r["qty"], "out_quantity": r["qty"] if r["status"] == 2 else 0,
         "complete_time": r["date"] if r["status"] == 2 else None, "status": r["status"],
         "warehouse_id": r["wh"], "position_id": 0, "goods_id": r["sku"]["goods_id"],
         "goods_num": r["sku"]["code"][:8], "goods_name": r["sku"]["goods_name"],
         "goods_image": r["sku"]["image"], "sku_id": r["sku"]["id"], "sku_code": r["sku"]["code"],
         "sku_name": r["sku"]["sku_name"], "pur_price": r["sku"]["cost"],
         "shop_id": r["shop"]["id"], "merchant_id": r["shop"]["merchant"], "inventory_mode": 0}
        for r in stock_out_items])

emit("SET FOREIGN_KEY_CHECKS=1;")
with open("seed_txn.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(out) + "\n")
print("txn rows: orders=%d items=%d shipments=%d refunds=%d afters=%d po=%d po_items=%d si=%d so=%d"
      % (len(order_rows), len(item_rows), len(shipments), len(refunds), len(afters), len(pos),
         len(po_items), len(stock_ins), len(stock_outs)))
