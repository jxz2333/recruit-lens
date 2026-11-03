#!/usr/bin/env python3
"""从「PRTS 公招池 ID」+「游戏角色数据表」重建 recruitment.json，并提供校验。

数据链路：
    PRTS 公招计算页（https://prts.wiki/w/公招计算）
      → Cargo 查询  chara ⨝ char_obtain  where obtainMethod like "%公开招募%"
      → 公招池 charId 列表（data/prts_ids.txt）
    游戏角色数据表 character_table.json
      → 名字 / 星级 / 职业 / 位置 / 词条
    两者合并 → data/recruitment.json + android/app/src/main/assets/recruitment.json

角色数据表不随仓库分发（约 15MB），用时先下载到 data/char_table.json：

    curl -L -o data/char_table.json \\
      "https://cdn.jsdelivr.net/gh/Kengxxiao/ArknightsGameData@master/zh_CN/gamedata/excel/character_table.json"

用法：
    python tools/build_recruitment.py --check
    python tools/build_recruitment.py --write
    python tools/build_recruitment.py --check --table /path/to/character_table.json
"""
import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PROFESSION = {
    "PIONEER": "先锋干员", "WARRIOR": "近卫干员", "TANK": "重装干员",
    "SNIPER": "狙击干员", "CASTER": "术师干员", "MEDIC": "医疗干员",
    "SUPPORT": "辅助干员", "SPECIAL": "特种干员",
}
POSITION = {"MELEE": "近战位", "RANGED": "远程位"}

ALL_TAGS = [
    "高级资深干员", "资深干员", "新手",
    "近战位", "远程位",
    "先锋干员", "近卫干员", "重装干员", "狙击干员",
    "术师干员", "医疗干员", "辅助干员", "特种干员",
    "输出", "防护", "治疗", "支援", "群攻", "生存", "减速", "削弱",
    "控场", "位移", "召唤", "快速复活", "爆发", "费用回复", "支援机械", "元素",
    "女性干员", "男性干员",
]

TABLE_HINT = """找不到角色数据表 {path}

先下载一份（约 15MB）：

    curl -L -o data/char_table.json \\
      "https://cdn.jsdelivr.net/gh/Kengxxiao/ArknightsGameData@master/zh_CN/gamedata/excel/character_table.json"

或者用 --table 指定已有路径。"""


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build(ids, table):
    ops = []
    for cid in ids:
        entry = table.get(cid)
        if entry is None:
            raise SystemExit(f"角色表里没有 {cid}")
        rarity = int(entry["rarity"].split("_")[1])
        tags = list(entry.get("tagList") or [])
        tags.append(PROFESSION[entry["profession"]])
        tags.append(POSITION[entry["position"]])
        if rarity == 5:
            tags.append("资深干员")
        if rarity == 6:
            tags.append("高级资深干员")
        ops.append({
            "id": cid,
            "name": entry["name"],
            "rarity": rarity,
            "tags": sorted(set(tags)),
        })
    ops.sort(key=lambda o: (o["rarity"], o["id"]))
    return {"operators": ops, "tags": {t: t for t in ALL_TAGS}}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只校验")
    ap.add_argument("--write", action="store_true", help="重建并写回")
    ap.add_argument("--table", default=os.path.join(ROOT, "data", "char_table.json"),
                    help="character_table.json 路径")
    args = ap.parse_args()

    if not os.path.exists(args.table):
        raise SystemExit(TABLE_HINT.format(path=args.table))

    ids = [line.strip()
           for line in open(os.path.join(ROOT, "data/prts_ids.txt"), encoding="utf-8")
           if line.strip()]
    table = load(args.table)
    fresh = build(ids, table)

    targets = [
        os.path.join(ROOT, "data/recruitment.json"),
        os.path.join(ROOT, "android/app/src/main/assets/recruitment.json"),
    ]

    old = load(targets[1])
    old_by_id = {o["id"]: o for o in old["operators"]}
    new_by_id = {o["id"]: o for o in fresh["operators"]}

    only_old = sorted(set(old_by_id) - set(new_by_id))
    only_new = sorted(set(new_by_id) - set(old_by_id))
    diff = []
    for cid in set(old_by_id) & set(new_by_id):
        a, b = old_by_id[cid], new_by_id[cid]
        if set(a["tags"]) != set(b["tags"]) or a["rarity"] != b["rarity"]:
            diff.append((a["name"], a["rarity"], b["rarity"],
                         sorted(set(a["tags"]) - set(b["tags"])),
                         sorted(set(b["tags"]) - set(a["tags"]))))

    print(f"PRTS 公招池：{len(fresh['operators'])} 人")
    print(f"现有数据   ：{len(old['operators'])} 人")
    print(f"只在现有   ：{[old_by_id[i]['name'] for i in only_old] or '无'}")
    print(f"只在 PRTS  ：{[new_by_id[i]['name'] for i in only_new] or '无'}")
    print(f"标签/星级不一致：{len(diff)}")
    for row in diff:
        print("  ", row)

    if args.write:
        for path in targets:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as f:
                json.dump(fresh, f, ensure_ascii=False, indent=2)
            print("written:", path)

    same = not only_old and not only_new and not diff
    print("\n结果：", "一致" if same else "有差异")
    return 0 if same else 1


if __name__ == "__main__":
    sys.exit(main())
