#!/usr/bin/env python3
"""把 data/recruitment.json 注入 web/template.html，生成单文件网页 web/index.html。

网页是纯静态单文件（数据内嵌），双击即可离线打开，也可以直接丢到任意静态托管
（GitHub Pages / 对象存储 / 本地 nginx）上给别人用。

用法：
    python tools/build_web.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEMPLATE = os.path.join(ROOT, "web", "template.html")
OUTPUT = os.path.join(ROOT, "web", "index.html")
DATA = os.path.join(ROOT, "data", "recruitment.json")


def main():
    with open(DATA, encoding="utf-8") as f:
        data = json.load(f)
    payload = json.dumps(
        {"operators": data["operators"], "tags": data["tags"]},
        ensure_ascii=False,
        separators=(",", ":"),
    )

    with open(TEMPLATE, encoding="utf-8") as f:
        html = f.read()

    if "__DATA__" not in html:
        raise SystemExit("template.html 里找不到 __DATA__ 占位符")

    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write(html.replace("__DATA__", payload))

    print(f"written: {OUTPUT}")
    print(f"干员 {len(data['operators'])} 名 / 词条 {len(data['tags'])} 个")


if __name__ == "__main__":
    main()
