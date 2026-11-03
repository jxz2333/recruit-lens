# Recruit Lens

A **read-only** helper for Arknights' Public Recruitment. It reads the five tags on screen and tells you which combinations guarantee which rarity — and who you will most likely get.

**It never touches the game**: no taps, no input injection, no network calls. It only answers "how many combinations do these five tags make, and what is each one's floor?"

Two builds, one engine:

- **Android app** — a floating ball over the game; tap it to capture one frame and show the result beside it
- **Web page** — [`web/index.html`](web/index.html), a single self-contained file, works offline, opens on a phone browser

## The problem

The recruit screen shows five tags, but the game only lets you pick **three**. Choosing the best trio means 25 set intersections in your head. That is what this tool does instead.

## Rules (aligned with MAA)

1. **Floor = the lowest rarity in the candidate pool**, not the highest. High rarities are extremely rare;
   the pool's maximum only means "if you're lucky". `辅助干员` spans 3★–6★ but yields 3★ almost always,
   so its floor is 3★.
2. **Only combinations of 1–3 tags are enumerated.** The game UI says "最多 3 项"; picking 4 or 5 is impossible.
3. **6★ operators are removed from any combination without `高级资深干员`.** In recruitment, 6★ is only
   reachable through that tag — keeping them makes the ceiling a lie.
4. **Assumes a 9:00:00 timer**: 1★/2★ require much shorter timers, so sub-3★ entries are lifted out.
   If the whole pool is low-star, only combos containing `支援机械` keep 1★ (that is the robot-farming case);
   every other all-low-star combo is dropped.
5. Sorting: floor ↓ → ceiling ↓ → average rarity ↓ → fewer tags.

## Screens

```
5 tags detected
Sniper  Ranged  Supporter  AoE  Caster
─────────────────────────────
 Worth it · floor 4★
 Ranged · AoE
 Lowest possible: Catapult, Lava
 6 candidates · lucky pull could be 5★
─────────────────────────────
4★ AoE·Caster      Lava · 3 candidates · all 4★
3★ Ranged          Kroos, Ambriel · 9 candidates
```

The panel is draggable anywhere on it (text included). Top-right buttons: `A-` / `A+` scale the
font size (0.8×–1.6×, the window width follows), `×` collapses the panel.

## Layout

```
android/                    Kotlin app (no Compose)
web/  template.html         template with a __DATA__ placeholder
      index.html            generated single-file page
tools/build_recruitment.py  rebuild / verify the dataset
      build_web.py          template + data → web/index.html
data/ recruitment.json      163 operators / 31 tags
      prts_ids.txt          charId list from PRTS (provenance)
```

## Build

**Android** — JDK 17, Android SDK 36, Gradle 8.13:

```bash
cd android
gradle wrapper --gradle-version 8.13
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

⚠️ The project path must be ASCII-only, otherwise AGP fails on line 1 of `app/build.gradle.kts`
with `Your project path contains non-ASCII characters`. Move it to an ASCII path, or add
`android.overridePathCheck=true` to `gradle.properties`.

**Web** — Python 3 only:

```bash
python tools/build_web.py
```

`web/index.html` is already generated and self-contained, so you can just double-click it.

## Data

The recruitment pool has not grown since 2021-09 — it is 163 operators. To refresh it:
pull the pool's charIds from PRTS's `char_obtain` Cargo query, download
`character_table.json` from [ArknightsGameData](https://github.com/Kengxxiao/ArknightsGameData),
then run `tools/build_recruitment.py --check` / `--write`.

## Known limitations

- Only tested on one device (Android 16, OnePlus). Other ROMs are unverified.
- Dataset is the 2021 recruitment pool; this is not a scraping gap — PRTS returns the same set today.
- Gender tags (`女性干员` / `男性干员`) are **not** counted: the data source has no gender field. The panel
  says so explicitly instead of silently over-reporting.
- OCR quality depends on device font rendering; a `干` → `千` misread was observed and is handled by
  character normalisation plus a same-length, one-character-diff fallback.
- Simplified Chinese client only.
- No prebuilt APK: release builds are unsigned here, build it yourself.

## Licence

**AGPL-3.0**, see [LICENSE](LICENSE). The project reuses the algorithm and data format of
[MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights) (also AGPL-3.0).
Full attribution: [THIRD_PARTY.md](THIRD_PARTY.md).

## Disclaimer

Unofficial. Not affiliated with Hypergryph. No game art assets are included. The app only performs
read-only screen recognition; it does not modify game data or play the game for you. Use at your own risk.
