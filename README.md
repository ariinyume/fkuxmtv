# 归藏电视 · GuiZang TV（个人自用分支）

一个给国内小米电视做的简洁桌面：大字、壁纸、常用应用，以及直接进入游戏机和 Apple TV 的入口。

> **本仓库是 `op7418/guizang-tv` 的个人自用分支**，改动：包名 `io.github.ariinyume.fkuxmtv`、首项为 Switch 2 直达 HDMI 1、壁纸按「使用自己的壁纸」流程在构建时打包。详见 [FORK.md](FORK.md)。

![归藏电视界面示意](docs/preview.svg)

**[下载 v0.3.0 APK](https://github.com/op7418/guizang-tv/releases/latest)** · [兼容性与验证](docs/COMPATIBILITY.md) · [代码 MIT 许可](LICENSE)

**v0.3.0：统一设置与全新 Dock。** 壁纸、布局、字体、焦点和首页编辑都在「设置」中；操作见 [设置指南](docs/SETTINGS.md)。横向 Dock 使用宽幅圆角毛玻璃底板和大图标，入口较多时随遥控器焦点平滑滚动。

项目背景见 **[v0.2.0 项目介绍与使用指南](docs/PROJECT-GUIDE.zh-CN.md)**：与 cleanYourTV、CleanFrame 的区别，实际改造与优势，安装、遥控器操作、壁纸设置和源码定制。

这是从个人电视上的定制版本整理出的公开版。目前重点适配小米电视 **MiTV-MTSU0**，不是面向所有 Android TV 的通用桌面替换工具。上图为布局示意；实际内置照片见 [壁纸预览与署名](docs/WALLPAPERS.md)。

**main 天气试用功能（尚未发布 APK）：** 时间右侧可显示天气，支持自动城市与手动选城。默认关闭，开启后联网；[试用说明与数据来源](docs/WEATHER.md)。正式下载仍为 v0.3.0。

## 能做什么

- 左侧大字列表：PlayStation 5、Apple TV、哔哩哔哩、小米投屏、相册、小米原桌面。
- 在已适配机型上，PlayStation 5 直达 HDMI 2，Apple TV 直达 HDMI 1。
- 操作区保留全部应用、其他信号源、设置（列表右下角，Dock 右上角）；壁纸统一进入「设置 → 背景」。
- 设置使用圆角选项与嵌入式首页预览；浏览壁纸时，预览和整页高斯模糊背景同步变化。
- 首页支持列表 / Dock、统一单色 SVG 图标 / 文字、字体风格与字号；毛玻璃焦点按内容宽度包裹，无长条描边。
- 首页 1–9 项，可添加、隐藏、排序和撤销编辑；默认六项不变。
- 壁纸支持固定选择、自动轮播，默认每 5 分钟切换；可选 1 / 5 / 15 / 30 分钟。
- 淡入淡出切换壁纸，离开桌面后暂停计时，返回后重新计时。
- 遥控器方向键操作，记住上次选中的入口和壁纸偏好。
- 不需要账号或无障碍服务；不下载或预装视频 App。main 的可选天气需要联网权限，默认关闭；v0.3.0 正式版仍不联网。本分支的「桌面守护」默认开启，依赖无障碍服务（只读窗口切换事件的包名，不读界面内容）；可选的「唤醒后进入桌面」默认关闭。见 [实机验证](#实机验证与已知限制)。
- 支持实验性开机自动进入：默认关闭，适配机型需先完成一次 ADB 配置，详见 [启用与恢复说明](docs/BOOT-START.md)。

公开版自带五张经过来源核对的 Unsplash 照片：雾中雪岭、雨林山路、热带绿叶、山间彩虹、流光花影。图片适用 [Unsplash License](https://unsplash.com/license)，代码适用 MIT；[完整来源与署名](docs/WALLPAPERS.md)随源码和 APK 保存。其余版权未确认的照片未纳入。也可以用自己的图片构建。

## 安装与使用

从 [Releases](https://github.com/op7418/guizang-tv/releases) 下载 APK，通过电视支持的本地安装方式安装。也可以在开启电视 ADB 调试、电脑连接同一网络后执行：

```sh
adb connect <电视IP>:5555
adb install -r guizang-tv-v0.3.0.apk
```

端口和调试方式取决于电视固件。首次连接请在电视上确认调试授权；使用结束后可以关闭调试。

安装后，在小米原桌面的应用列表中打开 **归藏电视**。

| 遥控器操作 | 行为 |
| --- | --- |
| 上 / 下 | 列表与其操作区切换项目；Dock 向上进入操作区，向下返回应用 |
| 右 | 列表进入操作区；Dock 向右选择应用 |
| 左 | 列表操作区返回上次应用；Dock 的应用和操作区均向左选择 |
| 确认 | 打开选中项目 |
| 返回 | 关闭面板；从操作区回到主列表；适配机型上从主列表回小米原桌面 |
| Home | 按系统原有设置工作，应用不拦截按键 |

**建议先作为普通应用试用。** 已测试的 HyperOS 固件会忽略第三方默认桌面选择，因此本项目不接管 Home 键。可在已适配机型上配置开机自动进入，需开启应用内开关并设置系统目标；这与默认桌面选择是两回事。无需禁用原桌面，小爱同学、手机投屏、直播及 HDMI 系统组件均保留。真实语音、投屏、直播播放仍需自行验证。

未配置开机自动进入时，卸载「归藏电视」即可结束试用。已经配置的用户先按 [关闭与恢复步骤](docs/BOOT-START.md) 停用并核对系统目标，避免缓存调用或降级旧版后行为变化。公开版包名为 `io.github.ariinyume.fkuxmtv`，不会覆盖上游 cleanYourTV，也不会覆盖原有私人试用版，设置不会自动迁移。

## 实机验证与已知限制

测试设备：**MiTV-MFTR0**（`Build.MODEL`）/ 固件 **OS2.0.23.0.RFTMATV** / Android 11。

| 能力 | 结果 |
| --- | --- |
| 桌面本体、壁纸、首页入口 | ✅ 安装即可用 |
| Switch 2 → HDMI 1 直达 | ✅ 点按可直达（与 MiTV-MTSU0 同为 23 端口） |
| 开机自动进入（`start_3rd_app`，走完整开机链） | ✅ 实测可用：真重启后前台即本桌面，进程在开机约 30 秒被拉起 |
| 开机自动进入（v0.5 系统 `BOOT_COMPLETED`，方案 A） | ❌ 未生效：广播送达晚于小米私有广播，当次进场由老路径完成 |
| 短按遥控器电源键（真待机） | ❌ `killAllThirdApp` 强杀进程并撤销无障碍授权，90 秒内无自启 |
| `KEYCODE_SLEEP` 熄屏后唤醒 | ✅ 进程存活、授权未被改写，唤醒回本桌面（2026-10-23 修正） |
| 桌面守护：回小米桌面即切回（v0.8） | ✅ 1.5 秒后接管；切 HDMI / 播放器不会被抢 |
| HOME 键接管（cleanyourtv 方案） | ❌ 澎湃 2 上四道锁破不了；强禁 tvhome 会让开机黑屏 |

**开机自启实测（2026-10-22，真重启）**：前台 `fkuxmtv/.FrameHomeActivity`，
`SYSTEM_ALERT_WINDOW` 熬过重启仍是 `allow`，长辈模式 `user_mode=2` 没丢。但归因要说清：标准
`BOOT_COMPLETED` 于 01:21:06 入队、01:21:07 开始分发，**41 个接收器串行跑了 18.5 秒**；小米私有广播
`mitv.action.STR_BOOT_COMPLETED` 在 01:21:22 就把 `BootGateActivity` 拉了起来——当次进场走的是老路径，
v0.5 的接收器没抢到先机（权限已放行，是顺序问题）。更早一次「失败」其实是 `adb reboot` 落空
（`error: device not found`、uptime 4061 秒），电视根本没重启：**验收前先看 uptime**。

HOME 键接管在这台电视上四道锁全破不了（框架层硬编码 `FallbackHome`、残缺的 `set-home-activity`、
打过补丁的解析器、受保护组件），强禁 `com.mitv.tvhome` 会让短按电源键开机时黑屏——完整证据链、
cleanyourtv 手法与两份研究文档见 `FORK.md` 的「开机自启实测」与「附：HOME 接管」。

**为什么原来判定息屏唤醒做不到（2026-10-23 修正）**：早前只测了遥控器电源键。按电源键待机时固件执行 `killAllThirdApp`，本应用进程被杀，无障碍服务被系统从设置里踢掉并打上 crashed 标记——这一条仍然成立：

```
电源键 → killAllThirdApp 屠杀（免杀名单形同虚设）
       → 进程死 → 无障碍服务被踢设置 + 打 crashed 标记
       → 90 秒内无自启 → 亮屏落回小米桌面
```

但用 `KEYCODE_SLEEP` 只熄屏时，进程和授权都还在：熄屏 30 秒、180 秒后进程仍存活、`enabled_accessibility_services` 未被改写，唤醒直接回到本桌面。所以「息屏唤醒 = 回小米桌面」不是固件硬结论，**真正的硬约束只有遥控器电源键这一条**。例外仍是**断电 / 关机再开机**（含 `adb reboot`）：走完整开机链，`start_3rd_app` 还在，开机自动进入照常生效。

**桌面守护（v0.8，默认开启）** 解决的是另一半：HOME 键被固件写死到 `com.mitv.tvhome`，`set-home-activity` 报 Success 却不生效，所以不去争「默认桌面」这个身份，改成「小米桌面一出现在前台就盖掉」。判定条件全在 `HomeGuardPolicy` 里：前台必须是 `com.mitv.tvhome`、屏幕亮着、自己不在前台、两次接管间隔 ≥ 4 秒、1 分钟最多 3 次；事件到达后先等 1.5 秒再确认一次才动手。主动去小米桌面（按返回键或点「小米原桌面」磁贴）会获得 5 分钟安静期，一离开小米桌面立即解除。

本机系统里**没有无障碍设置页**，应用内「无障碍设置」按钮只会提示“系统没有无障碍设置页”，开启只能靠 adb：

```bash
adb shell settings put secure enabled_accessibility_services "<原值>:io.github.ariinyume.fkuxmtv/io.github.ariinyume.fkuxmtv.WakeAccessibilityService"
adb shell settings put secure accessibility_enabled 1
```

## 从源码构建

要求：**JDK 17、Gradle 8.9、Android SDK Platform 34、Build Tools 34.0.0**。

仓库只包含源码；Gradle 请通过 [官方发行版](https://gradle.org/releases/) 安装。也可以用 Android Studio 打开项目，并选择本地 Gradle 8.9。

```sh
git clone https://github.com/op7418/guizang-tv.git
cd guizang-tv
export ANDROID_HOME=/path/to/Android/sdk
export JAVA_HOME=/path/to/jdk-17
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

生成文件：`app/build/outputs/apk/debug/app-debug.apk`。首次构建需要联网下载 Android Gradle Plugin 和测试依赖，应用运行本身不需要联网。

CI 会构建 Debug APK 并执行测试、lint；下载 Actions 产物需要登录 GitHub。Debug 包用于自行测试，不保证与 Releases 的正式签名兼容。

Release 构建：

```sh
gradle :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

未设置签名时输出未签名 APK。维护者可在本地配置 `GUIZANG_KEYSTORE`、`GUIZANG_STORE_PASSWORD`、`GUIZANG_KEY_ALIAS`（默认 `guizang-tv`）和 `GUIZANG_KEY_PASSWORD`（默认同 store password），再构建签名版本。密钥和密码不应提交到 Git。

## 使用自己的壁纸

目前支持**构建时打包**，电视端可以从已打包的图片中选择；尚未提供电视端文件导入功能。

准备 1–15 张 JPEG / PNG / WebP 图片，然后：

```sh
python3 -m venv .venv
.venv/bin/pip install Pillow
.venv/bin/python scripts/prepare_wallpapers.py /path/to/your/wallpapers
gradle :app:assembleDebug
```

脚本保留原图，为电视生成最长边 1920 像素的副本与缩略图，并去除 EXIF。生成内容放在 Git 忽略的 `app/src/main/assets/wallpapers/local/` 中。重新构建后，桌面会优先使用这组图片，内置照片仍随 APK 保留；文件名作为壁纸名称。请只分发有权公开使用的图片。

重复运行时，上一份生成结果会移到本地 `.wallpaper-backups/` 保留，避免旧照片混进新的安装包；备份目录也不进入 Git。

如需恢复默认的五张照片，将 `local/` 移到项目目录外备份，再重新构建。没有照片目录时会回退到五种渐变背景。

## 修改入口与适配电视

- 首页可在「设置 → 首页内容」编辑。默认名称、包名和顺序位于 [HomeItems.java](app/src/main/java/io/github/ariinyume/fkuxmtv/HomeItems.java)，应用启动由 FrameHomeActivity 处理。
- 小米接口集中在 [XiaomiControls.java](app/src/main/java/io/github/ariinyume/fkuxmtv/XiaomiControls.java)。HDMI 参数只在已验证机型上启用，不能仅添加机型名就视为完成适配。
- 分组设置位于 HomeSettings，首页及预览共用 HomeSurface；壁纸加载与轮播逻辑在 [FrameWallpapers.java](app/src/main/java/io/github/ariinyume/fkuxmtv/FrameWallpapers.java)。
- 当前 PS5 / Apple TV 是入口名称，不代表自动识别到设备。请按实际接线修改端口。
- 应用未安装时会显示提示；项目没有代装应用、账号登录或内容破解功能。

欢迎通过 Issue 提交电视型号、Android / 固件版本和复现步骤。请隐去局域网地址、序列号、账号及调试密钥。

## 来源与致谢

本项目起步于 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) 的本地定制，整理时提取了本次实现的原生列表桌面及小米适配。公开版不包含上游的一键清理脚本、旧 Dock 桌面、无障碍按键服务或系统应用禁用流程。

界面方向参考 [nthnclrk/CleanFrame](https://github.com/nthnclrk/CleanFrame)：照片背景、大字纵向列表、克制的焦点高亮，以及分组设置、预览和确认应用的交互。Android 界面使用原生 Java 实现，没有移植 Tizen 运行代码或附带其照片。

代码采用 MIT 许可，保留上游署名。详细来源见 [NOTICE.md](NOTICE.md)。
