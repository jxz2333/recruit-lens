# 公招透镜 · RecruitOnly

《明日方舟》公开招募的**只读**词条识别悬浮窗。手机本地跑，不连电脑、不点屏幕、不改游戏。

- 打开 App → 授权悬浮窗 → 授权截屏 → 回到游戏
- 进到公招界面，点侧边悬浮球
- 面板列出这 5 个词条的所有组合，按保底星级排序，第一行就是推荐

## 目录

```
android/
  app/src/main/java/com/arknights/recruitonly/
    MainActivity.kt                权限引导 + 启动入口
    overlay/OverlayService.kt      前台服务：MediaProjection + 悬浮球/面板
    overlay/OverlayViews.kt        悬浮球与结果面板（纯代码构建，无 XML）
    capture/ScreenCapturer.kt      按需截一帧（临时 VirtualDisplay）
    ocr/OcrEngine.kt               ML Kit 中文 OCR（模型随包，离线）
    analysis/TagExtractor.kt       OCR 文本 → 词条（词表最长匹配）
    analysis/MatchEngine.kt        匹配内核（纯 Kotlin，可单测）
    data/RecruitRepository.kt      读 assets/recruitment.json
    model/Models.kt                数据模型
  app/src/main/assets/recruitment.json   MAA 的公招数据
  app/src/test/...                 纯 JVM 单测：匹配 + 切词
公招词条计算器.html                 同一套算法的网页版，用来对答案
data/recruitment_v2.json           原始数据（dev-v2 分支）
```

## 构建

需要 JDK 17 + Android SDK 36。

```bash
cd android
gradle wrapper --gradle-version 8.13   # 首次：生成 gradlew（仓库里没带 jar）
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest       # 匹配与切词的纯逻辑测试
```

装到手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`compileSdk` 是 36（Android 16）。如果你的 AGP/SDK 版本对不上，先把 `app/build.gradle.kts` 里的
`compileSdk` 降到已安装的版本——代码里没有用到 36 独有的 API。

### 两个踩过的坑

1. **路径不能有中文**。AGP 会在 `app/build.gradle.kts` 第 1 行直接失败：
   `Your project path contains non-ASCII characters`。两种解法：把工程复制到纯英文路径再编（推荐），
   或在 `gradle.properties` 加 `android.overridePathCheck=true`。
2. **APK 默认 53M**，因为 ML Kit 的中文 OCR 模型按 `arm64-v8a / armeabi-v7a / x86` 各打一份。
   已经用 `ndk.abiFilters = ["arm64-v8a"]` 砍到 24M。要再小就得换 PaddleOCR-ncnn 之类的轻量模型。

### 一个可用的命令行构建方式

没有 Android Studio 时，用命令行工具链即可（把路径换成你自己的）：

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk       # 需要 platforms;android-36 与 build-tools;36.0.0

cd /path/to/ascii-only/RecruitOnly              # 必须是纯 ASCII 路径
$ANDROID_HOME/../gradle-8.13/bin/gradle :app:assembleDebug --no-daemon
```

## Android 14+ / 16 的几个硬约束（代码已按此写）

1. **顺序不能反**：`startForeground(type=mediaProjection)` → `getMediaProjection()` →
   `registerCallback()` → `createVirtualDisplay()`。少一步就是 `SecurityException`。
2. **一次授权只能建一次虚拟屏**：Android 14 起 `createVirtualDisplay` 每个 token 只允许调一次，
   再建一次会直接让系统停掉整个投屏（日志：`Reusing token: invalid projection`）。
   所以做法是启动时建一次、之后复用同一个 ImageReader 取最新帧，而不是每次点都重建。
3. **单应用投屏**：Android 15 起授权弹窗可以只共享一个应用。选「整个屏幕」最稳；
   选「单个应用」时只截到那个应用，本功能也能用，但悬浮球不在截图里，别选错。
4. **自己的悬浮窗不能入镜**：截图前把悬浮球和面板设为 `INVISIBLE`，等 100ms 再抓帧，
   否则会把面板上的词条一起 OCR 进去，造成误判。
5. **虚拟屏用正方形（1600×1600）**：横屏游戏和竖屏桌面共用一个 VirtualDisplay，
   镜像内容等比缩放居中，留黑边而不是拉伸，转屏也不需要重建（重建会触犯第 2 条）。

## 匹配规则（对齐 MAA 的 `recruit_calc::get_all_combs`）

1. **最多只枚举 3 个词条的组合**——游戏里「职业需求（最多3项）」，勾 4、5 个在界面上做不到。
2. **不含「高级资深干员」时，把 6★ 从池里剔掉**——公招的 6★ 只能靠高资出，不剔会把上限算虚高。
3. **9:00:00 口径**：保底 <3★ 的池子抬到 ≥3★；整池都低星时只有「支援机械」保留 1★。
4. 排序：保底 ↓ → 上限 ↓ → 平均星级 ↓ → 词条数 ↑。

## 识别是怎么做的

没有做 ROI 标定，也不依赖分辨率：**整屏 OCR → 词表最长匹配**。

公招词条一共 31 种，都是 2–7 个汉字的固定词。把 OCR 出来的每一行按词表做最长匹配切分，
非词条文本（"开始招募""招聘合同"）自然被丢掉；再用这些关键词判断当前是不是招募界面。
好处是换手机、换分辨率、横竖屏都不用重新标定；代价是 OCR 必须认得准。

## 已知限制

- **公招池是冻结的，不是数据过期**：池里 163 人，末位「琴柳」是 2021-09。这不是抓漏了——PRTS 的
  `char_obtain`（公开招募）现在返回的也正好是这 163 个 charId，与游戏角色数据表重建的标签逐人比对 0 处差异。
  公招池自 2021 年后未再扩充，所以这份数据是完整的。
- **性别词条未参与计算**：数据源没有干员性别字段，`女性干员 / 男性干员` 两个词条会被跳过；
  识别到这两个词条时面板会显式提示"未计入，结果可能偏乐观"，不会闷声给结论。
- **保底 = 池中最低星级**：词条只决定候选池，出哪个星级是池内按权重随机、高星概率极低。
  所以"保底 4★"意思是"至少 4★"，而不是"大概率 4★"；池中最高星级只代表玄学上限。
- **按 9:00:00 招募计算**（与 MAA 一致）：1★/2★ 只在很短的时间下才会出，所以保底 <3★ 时
  把 1★/2★ 从池里抬掉；整池都是低星时只有「支援机械」保留 1★（那正是刷机器人的玩法），
  其余纯低星组合在 9 小时下什么都出不来，直接不列。
- 语音/多语言客户端未适配，只按简中词条做词表。

## 数据怎么更新

```bash
python tools/build_recruitment.py --check   # 校验现有数据与三源重建是否一致
python tools/build_recruitment.py --write   # 重建并写回 data/ 与 assets/
```

`data/prts_ids.txt` 是 PRTS 公招池的 charId 列表，取自该页面的 Cargo 查询：
`tables=chara,char_obtain&where=obtainMethod like "%公开招募%"`。池子哪天扩了，重新拉一次这个列表再跑脚本即可。

