# 自用分支说明（fkuxmtv）

基于 [op7418/guizang-tv](https://github.com/op7418/guizang-tv) 的个人自用改动，代码仍为 MIT 许可，上游署名与壁纸许可文件原样保留。

## 相对上游的改动

| 项目 | 上游 | 本分支 |
| --- | --- | --- |
| 应用包名 | `io.github.op7418.guizangtv` | `io.github.ariinyume.fkuxmtv` |
| 默认首项 | PlayStation 5 → HDMI 2 | **Switch 2 → HDMI 1** |
| 默认次项 | Apple TV → HDMI 1 | Apple TV → HDMI 2（避免与 Switch 2 抢端口） |
| 首项图标 | PlayStation 标志 | Nintendo Switch 标志（`mark_nintendo_switch.xml`，Simple Icons CC0） |
| 应用显示名 | 归藏电视 | **大米电视**（电视应用列表里与原版区分） |
| 版本 | `0.4.0-dev` / code 5 | `0.4.0-fkuxmtv.2` / code 7 |
| 壁纸 | 仅内置五张 Unsplash | 构建时打包 `F:\Desktop\壁纸` 的 15 张图，优先于内置照片 |
| 机型门禁 | 仅 `MiTV-MTSU0` | 识别小米电视家族（`MiTV*` / `L55M9*` 等），含目标机 **L55M9-S**；HDMI 直达不再硬性依赖机型名 |

改动文件：

- `app/build.gradle`：`namespace` / `applicationId` / `versionCode` / `versionName`
- `app/src/main/java/io/github/ariinyume/fkuxmtv/*`：源码目录与 `package` 声明整体迁移
- `HomeItems.java`：`DEFAULTS` 顺序与默认名称
- `HomeIcons.java`：`hdmi:1` → Switch 图标，`hdmi:2` → Apple TV 图标
- `res/values/styles.xml`：`app_name`
- `assets/icons/nintendoswitch.svg` + `SOURCES.json`、`res/drawable/mark_nintendo_switch.xml`：新增图标
- `assets/wallpapers/local/`：`scripts/prepare_wallpapers.py` 生成（Git 忽略，仅本地构建）

## 壁纸打包

按上游 README「使用自己的壁纸」执行，未改动脚本：

```sh
.venv/bin/python scripts/prepare_wallpapers.py "F:/Desktop/壁纸"
```

脚本把原图转成最长边 1920 的 JPEG（去 EXIF）+ 320×180 缩略图，写入
`app/src/main/assets/wallpapers/local/`，并生成 `local/catalog.json`。运行时
`FrameWallpapers` 先读 `local/catalog.json`，因此这 15 张会取代内置照片；
`bundled/` 仍随包保留作为回退，壁纸名称取文件名（01–15）。

## 构建

要求 JDK 17 / Gradle 8.9 / Android SDK Platform 34 + Build Tools 34.0.0。
本机工具链在 `../.tools/`，签名密钥在 `../.keys/`（都不在仓库内）。

```sh
../build-release.sh                 # 用现有壁纸构建
../build-release.sh "F:/Desktop/壁纸"  # 重新打包壁纸后构建
```

产物：`../dist/guizang-tv-fkuxmtv-<version>.apk`（已签名）。

## 安装

```sh
adb connect <电视IP>:5555
adb install -r dist/guizang-tv-fkuxmtv-0.4.0-fkuxmtv.2.apk
```

与原版包名不同，可与原版共存；两者设置互不迁移，卸载任一方不影响另一方。

## 已知限制

- HDMI 端口号（23 = HDMI 1、24 = HDMI 2）是在 `MiTV-MTSU0` 固件上实测得到的。L55M9-S 上沿用同一套小米 `com.xiaomi.mitv.tvplayer` 组件；若该固件不接受 `input` 参数，会退回信号源列表让你手选，不会闪退。
- 已在真机 `MiTV-MTSU0` 验证；L55M9-S 尚未实机验证（无设备），首次安装请先确认 HDMI 按钮行为。
- 本分支签名与上游官方 APK 不同，不能覆盖安装原版，也不能被原版覆盖。
- 壁纸来自本地目录，仅自用分发；公开分发需确认图片授权。
