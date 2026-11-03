# 引用与致谢 / Third-party notices

本项目站在别人肩膀上。下面逐项说明**用了什么、用在哪、按什么许可**。

---

## 1. MaaAssistantArknights（MAA）

- 仓库：https://github.com/MaaAssistantArknights/MaaAssistantArknights
- 许可：**AGPL-3.0**
- 用途：
  - **数据格式**：`android/app/src/main/assets/recruitment.json` 的 `{operators:[{id,name,rarity,tags}], tags:{}}`
    结构与其 `resource/recruitment.json` 一致；数据内容也与之逐条一致（见下条 PRTS 的交叉校验）。
  - **匹配规则**：`analysis/MatchEngine.kt` 的组合枚举方式与判定规则对齐其
    `src/MaaCore/Task/Miscellaneous/AutoRecruitTask.cpp` 中的 `recruit_calc::get_all_combs`：
    只枚举 1–3 个词条、不含 `高级资深干员` 时剔除 6★、按 9:00:00 口径抬升 1★/2★、排序优先级
    （最低星 → 最高星 → 平均星 → 词条数）。
  - **界面文本与锚点词**：参考其公招相关资源的命名思路。
- 说明：**没有复制其 C++ 源码**，Kotlin 实现是重写的；但由于规则与数据同源，本仓库整体以 AGPL-3.0 发布。

> 复核方式：`python tools/build_recruitment.py --check` 会重新构建数据并与仓库内文件逐字段比对。

---

## 2. PRTS Wiki

- 页面：https://prts.wiki/w/公招计算 （公开招募计算器）
- 用途：该页面通过 MediaWiki Cargo 查询取数据：

  ```
  tables=chara,char_obtain
  where=char_obtain.obtainMethod like "%公开招募%" AND chara.charIndex>0
  fields=chara.charId, chara.cn, chara.rarity, chara.profession, chara.position, chara.tag
  ```

  本仓库的 `data/prts_ids.txt`（163 个 charId）即由这条查询得到，是"哪些干员在公招池里"的权威依据。
- 许可：Wiki 文本内容按其站点声明的许可发布（通常为 CC BY-NC-SA），**具体以 prts.wiki 的版权声明为准**。
  本项目仅使用其查询出的**事实性数据**（谁在池里、谁的星级/职业是什么），不复制其正文内容。
- 声明：本项目与 PRTS 无隶属关系。

---

## 3. Kengxxiao/ArknightsGameData

- 仓库：https://github.com/Kengxxiao/ArknightsGameData
- 用途：其中 `zh_CN/gamedata/excel/character_table.json` 提供干员的中文名、星级（`rarity`）、
  职业（`profession`）、位置（`position`）、公招词缀（`tagList`）。
- 说明：该仓库是游戏资源的结构化整理，**原始数据版权归鹰角网络所有**。本项目只在构建期读取该文件，
  不随仓库分发（15MB，已被 `.gitignore` 排除），运行时也不需要网络。

---

## 4. Google ML Kit（Text Recognition v2，中文）

- 文档：https://developers.google.com/ml-kit/vision/text-recognition/v2
- 依赖：`com.google.mlkit:text-recognition-chinese:16.0.1`
- 用途：安卓端离线 OCR，识别屏幕上的词条文字。模型随 APK 打包，不联网、不上传任何图像。
- 许可：闭源 SDK，按 [Google APIs 服务条款](https://developers.google.com/terms) 使用；
  其随包的模型文件版权归 Google。

---

## 5. AndroidX / Kotlin / kotlinx.serialization / Gradle

| 组件 | 许可 |
|---|---|
| AndroidX（core-ktx、appcompat、lifecycle） | Apache-2.0 |
| Kotlin 标准库、kotlinx-coroutines、kotlinx-serialization | Apache-2.0 |
| Android Gradle Plugin、Gradle | Apache-2.0 |
| JUnit 4（仅测试） | EPL-1.0 |

---

## 6. 游戏与商标

《明日方舟》/ *Arknights* 及其角色、术语、界面设计的版权归**上海鹰角网络科技有限公司
（Hypergryph / Studio Montagne）**所有。

本项目是**非官方**的第三方工具，与鹰角网络无任何关联，未获其授权或背书：

- 不包含任何游戏美术资源、音频、文本数据文件
- 只对游戏界面做只读识别（截屏 → OCR），不修改游戏数据、不模拟操作、不代替玩家点击
- 干员名称、词条名称属于描述性使用

---

## 7. 本项目原创内容

- 全部 Kotlin / Python / HTML 代码
- 应用图标（深色底 + 放大镜 + 词条），以矢量方式绘制，无第三方素材
- 各文档

---

如果以上有任何署名遗漏或授权理解有误，欢迎提 Issue 指正，会尽快修正。
