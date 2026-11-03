# 公招透镜 · Recruit Lens

《明日方舟》公开招募的**只读**词条助手：识别当前页面的 5 个词条，告诉你哪些组合能保底几星、最可能出谁。

**不动游戏、不点屏幕、不发请求**——只是把「5 个词条能选出多少种组合、每种至少几星」这件事算清楚。

两种形态，同一套算法：

- **安卓 App**：悬浮球浮在游戏上，点一下截一帧识别，结果贴边显示（不用切出去）
- **网页版**：[`web/index.html`](web/index.html)，单文件、纯前端、离线可用，手机浏览器打开就能算

## 它解决什么问题

公招界面一次给 5 个词条，但你最多只能勾 3 个。要挑出「保底 4 星以上」的那一组，得在脑子里做 25 次交集运算——人算不过来，工具算得过来。

程序枚举全部 C(5,1)+C(5,2)+C(5,3) = 25 种组合，按保底星级排序，第一名就是该选的。

## 核心规则（对齐 MAA）

1. **保底 = 候选池里的最低星级**，不是最高。高星概率极低，池里最高星级只代表玄学上限。
   例如「辅助干员」的池子从 3★ 到 6★ 都有，实际几乎只出 3★——它的保底是 3★。
2. **最多枚举 3 个词条的组合**。游戏里写的是「职业需求（最多 3 项）」，勾 4、5 个在界面上做不到。
3. **不含「高级资深干员」时把 6★ 剔出池子**。公招的 6★ 只能靠高资标签出，不剔掉上限就是假的。
4. **按 9:00:00 招募计算**：1★/2★ 只在很短的时间下才会出，所以保底 <3★ 时把低星抬掉；
   整池都是低星时只有「支援机械」保留 1★（那正是刷机器人的玩法），其余纯低星组合直接不列。
5. 排序：保底 ↓ → 上限 ↓ → 平均星级 ↓ → 词条数 ↑。

规则细节与 MAA 的关系见 [THIRD_PARTY.md](THIRD_PARTY.md)。

## 界面

```
识别到 5 个词条
狙击干员  远程位  辅助干员  群攻  术师干员
─────────────────────────────
 值得上 · 保底 4★
 远程位 · 群攻
 最低可出：空爆、炎熔
 候选 6 人 · 运气好能出 5★
─────────────────────────────
4★ 群攻·术师干员   炎熔 · 候选 3 人 · 全是 4★
3★ 远程位          克洛丝、安比尔 · 候选 9 人
```

## 目录

```
android/                    安卓 App（Kotlin，无 Compose）
  app/src/main/java/com/arknights/recruitonly/
    MainActivity.kt             权限引导
    overlay/OverlayService.kt   前台服务：MediaProjection + 悬浮球/面板
    overlay/OverlayViews.kt     悬浮球与结果面板（纯代码构建）
    capture/ScreenCapturer.kt   复用型虚拟屏，按需抓帧
    ocr/OcrEngine.kt            ML Kit 中文 OCR
    analysis/TagExtractor.kt    OCR 文本 → 词条（词表 + 容错）
    analysis/MatchEngine.kt     组合匹配内核（纯 Kotlin，可单测）
  app/src/test/                 25+ 个纯 JVM 单测
web/
  template.html               带 __DATA__ 占位符的模板
  index.html                  生成好的单文件网页（直接打开）
tools/
  build_recruitment.py        重建数据 / 校验数据
  build_web.py                模板 + 数据 → web/index.html
data/
  recruitment.json            163 名公招池干员 / 31 个词条
  prts_ids.txt                PRTS 公招池的 charId 列表（数据来源凭据）
```

## 构建

**Android**（JDK 17 + Android SDK 36 + Gradle 8.13）：

```bash
cd android
gradle wrapper --gradle-version 8.13    # 仓库未带 wrapper jar，首次生成
./gradlew :app:testDebugUnitTest        # 纯逻辑单测，不需要设备
./gradlew :app:assembleDebug
```

⚠️ 工程路径不能含中文，否则 AGP 在 `app/build.gradle.kts` 第 1 行直接报
`Your project path contains non-ASCII characters`。把工程放到纯英文路径再编，或在
`gradle.properties` 加 `android.overridePathCheck=true`。

**网页**（只要有 Python 3）：

```bash
python tools/build_web.py    # 重新生成 web/index.html
```

没有构建步骤也能用——`web/index.html` 里数据是内嵌的，双击就能打开。

## 更新数据

公招池自 2021-09 起没扩充过，目前 163 人。真要更新：

```bash
# 1. 从 PRTS 拿最新的公招池 id 列表，覆盖 data/prts_ids.txt
#    https://prts.wiki/w/公招计算 用的是这条 Cargo 查询：
#    tables=chara,char_obtain  where=obtainMethod like "%公开招募%"
# 2. 下载角色数据表（约 15MB，不随仓库分发）
curl -L -o data/char_table.json \
  "https://cdn.jsdelivr.net/gh/Kengxxiao/ArknightsGameData@master/zh_CN/gamedata/excel/character_table.json"
# 3. 校验 + 重建
python tools/build_recruitment.py --check
python tools/build_recruitment.py --write
```

## 已知限制

- **只在 Android 16（一加 PJZ100）上真机跑过**，别的机型/系统版本没验证。
- **数据是 2021 年的公招池**：163 人、末位「琴柳」。这不是抓漏了——PRTS 的 `char_obtain`
  现在返回的也正好是这批人，公招池本身没变。
- **性别词条不参与计算**：数据源没有干员性别字段，遇到「女性干员 / 男性干员」时面板会明示
  「该词条未计入，结果可能偏乐观」，不会闷声给结论。
- **识别率取决于手机字体渲染**：真机上遇到 ML Kit 把「干」认成「千」，已加字符归一 + 同长度
  差一字的模糊匹配兜底；仍可能偶尔漏词条。
- 只在简中客户端验证，未适配国际服/日服/韩服（那些服词条名不同，需另做词表）。
- Release 未签名，仓库不提供预编译 APK，请自行构建。

## 许可证

源码以 **AGPL-3.0** 发布，全文见 [LICENSE](LICENSE)。选它是因为本项目复用了
[MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights)（同为 AGPL-3.0）
的算法规则与数据格式。引用清单与致谢见 [THIRD_PARTY.md](THIRD_PARTY.md)。

## 免责声明

非官方工具，与鹰角网络（Hypergryph）无关联。本仓库不包含任何游戏美术资源，
只对游戏界面做只读识别，不修改游戏数据、不代替玩家操作。请自行承担使用风险。

---

English: [README.en.md](README.en.md)
