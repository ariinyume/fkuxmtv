# 自用分支说明（fkuxmtv）

基于 [op7418/guizang-tv](https://github.com/op7418/guizang-tv) 的个人自用改动，代码仍为 MIT 许可，上游署名与壁纸许可文件原样保留。

## 测试设备号

小米电视S55 2023 / L55M9-S / MiTV-MFTR0 / OS2.0.23.0.RFTMATV / Android 11

注意：MiTV-MFTR0 关联的设备不止一种。

## 相对上游的改动

| 项目 | 上游 | 本分支 |
| --- | --- | --- |
| 应用包名 | `io.github.op7418.guizangtv` | `io.github.ariinyume.fkuxmtv` |
| 默认首项 | PlayStation 5 → HDMI 2 | **Switch 2 → HDMI 1** |
| 默认次项 | Apple TV → HDMI 1 | Apple TV → HDMI 2（避免与 Switch 2 抢端口） |
| 首项图标 | PlayStation 标志 | Nintendo Switch 标志（`mark_nintendo_switch.xml`，Simple Icons CC0） |
| 应用显示名 | 归藏电视 | **大米电视**（电视应用列表里与原版区分） |
| 版本 | `0.4.0-dev` / code 5 | `0.4.0-fkuxmtv.5` / code 10 |
| 壁纸 | 仅内置五张 Unsplash | 构建时打包 `F:\Desktop\壁纸` 的 15 张图，优先于内置照片 |
| 机型门禁 | 仅 `MiTV-MTSU0` | 识别小米电视家族（`MiTV*` / `L\d{2}M\d` 等），含目标机 **L55M9-S / MiTV-MFTR0**；HDMI 直达不再硬性依赖机型名 |
| 开机进入 | 无（依赖小米 `start_3rd_app`） | 吃系统 `BOOT_COMPLETED`，`SYSTEM_ALERT_WINDOW` 作后台启动豁免；`start_3rd_app` 保留兜底 |
| 唤醒进入 | 无 | 亮屏后 8 秒内若停在小米原桌面，拉起本桌面一次（无障碍服务驱动，默认关闭；**本机实测不可用**，见下） |

改动文件：

- `app/build.gradle`：`namespace` / `applicationId` / `versionCode` / `versionName`
- `app/src/main/java/io/github/ariinyume/fkuxmtv/*`：源码目录与 `package` 声明整体迁移
- `HomeItems.java`：`DEFAULTS` 顺序与默认名称
- `BootReceiver.java`：系统开机广播入口（新增）；`BootStart.java`：新增 `enterFromBoot()`、悬浮窗状态与授权入口
- `AndroidManifest.xml`：`RECEIVE_BOOT_COMPLETED` + `SYSTEM_ALERT_WINDOW`，注册 `BootReceiver`
- `WakeHome.java` / `WakePolicy.java` / `WakeAccessibilityService.java`：唤醒进入（新增）
- `res/xml/accessibility_service_config.xml`：只接收窗口切换事件，不读窗口内容
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
adb install -r dist/guizang-tv-fkuxmtv-0.4.0-fkuxmtv.5.apk
# 开机自动进入需要的豁免（重装后要重做一次）
adb shell appops set io.github.ariinyume.fkuxmtv SYSTEM_ALERT_WINDOW allow
```

与原版包名不同，可与原版共存；两者设置互不迁移，卸载任一方不影响另一方。

## 开机自动进入（方案 A：系统开机广播）

v0.4.0-fkuxmtv.5 加了系统 `BOOT_COMPLETED` 这条路，设计上是让开机进入不再依赖小米的 `start_3rd_app`。

> **实测结论（MiTV-MFTR0）：标准广播抢不过小米私有广播，实际进场仍由 `start_3rd_app` 完成。**
> 这条路径当兜底保留，别当主力——证据见下文「[开机自启实测（2026-10-22）](#开机自启实测2026-10-22)」。

| 环节 | 实现 |
| --- | --- |
| 权限 | `RECEIVE_BOOT_COMPLETED` + `SYSTEM_ALERT_WINDOW`（后者是 Android 10+ 后台启动 Activity 的官方豁免） |
| 接收器 | `BootReceiver`，`exported="true"`，只听 `BOOT_COMPLETED` |
| 动作 | `startActivity(FrameHomeActivity)`，带 `NEW_TASK \| CLEAR_TOP`，并写入本次 `boot_count` 供 `BootStart.confirm()` 记录状态 |
| 闸门 | 复用 `BootStartPolicy`：开关开 + 机型受支持 + 本次开机未尝试过 + 开机 10 分钟内 |
| 重试 | 只有首次 `startActivity` 抛异常才在 2.5 秒后重试一次（`goAsync`）；成功即标记，不重复 |

开关在 设置 → 电视与启动 → 开机自动进入，**默认开启**（新装即生效）；关掉后两条路径都不再拉起。
`start_3rd_app` 那条老路保留作兜底，两条路共用 `attempted` 计数，同一次开机只会进一次。

授权（只需一次，重装后要重做）：

```sh
adb shell appops set io.github.ariinyume.fkuxmtv SYSTEM_ALERT_WINDOW allow
adb shell appops get io.github.ariinyume.fkuxmtv SYSTEM_ALERT_WINDOW
```

`pm grant ... SYSTEM_ALERT_WINDOW` 在部分固件上会报「不是可更改的权限类型」，`appops set` 才稳。
没有这个 app-op，开机广播照样收得到，但 `startActivity` 会被后台启动限制拦下——日志里会有
`GuiZangBoot: no SYSTEM_ALERT_WINDOW`。

验证：

```sh
adb shell dumpsys package io.github.ariinyume.fkuxmtv | grep -i BOOT_COMPLETED
adb shell dumpsys activity io.github.ariinyume.fkuxmtv | grep BootStart
adb reboot
```

`dumpsys` 里 `BootStart:` 行现在多一个 `overlay=true/false`（app-op 是否放行），`status` 会写
「已由系统开机广播进入桌面」或「后台启动被系统拒绝（…）」。手动 `am broadcast -n …/.BootReceiver -a android.intent.action.BOOT_COMPLETED`
只在该次开机还没进过桌面时有效，否则会被「本次开机已尝试」拦下——要复验就真重启。

## 唤醒后进入桌面（v0.4.0-fkuxmtv.4）

> **L55M9-S / MiTV-MFTR0 实测结论：本机不可用。** 电源键息屏会执行 `killAllThirdApp` 杀掉进程，并把无障碍授权从设置里踢掉（带 crashed 标记），服务根本没有机会监听亮屏。实现保留给未封杀的固件，本机不建议开启——本机系统连无障碍设置页都没有，应用内「无障碍设置」按钮只会提示“系统没有无障碍设置页”。完整链路见下文「[实机验证结果](#实机验证结果2026-10-21)」。

设置 → 电视与启动 → 唤醒后进入桌面。默认**关闭**。

### 为什么是无障碍服务，而不是前台服务

v0.4.0-fkuxmtv.3 用前台服务守 `ACTION_SCREEN_ON`，在 L55M9-S / MiTV-MFTR0 上从未启动过一次。实机日志：

```
21:52:24.990 AppsRelaunchController: Refused to start foreground service:
caller=io.github.ariinyume.fkuxmtv, isCallerSystem=false
```

被拒那一刻本应用**正在前台**（`TvMgr-Notifier` 报的 currentPkgName 就是本包），所以这不是"后台启动限制"，而是该固件对第三方前台服务的无差别封杀：判据只有 `isCallerSystem=false`。前台服务起不来 → `onCreate` 从未执行 → 动态广播从未注册 → "前台读不到"只是没触发，接口本身正常（同一时刻 `dumpsys usagestats` 里有秒级 `ACTIVITY_RESUMED` 事件，包名类名俱全）。

v0.4.0-fkuxmtv.4 把监听搬进 `WakeAccessibilityService`：由系统绑定并保活，亮屏广播和窗口切换事件都收得到，拉起 Activity 也走系统服务通道。`WakeService` / `WakeBootReceiver` 以及 `FOREGROUND_SERVICE*`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED` 权限已整体移除。

### 两路触发，同一道闸门

1. `TYPE_WINDOW_STATE_CHANGED` 里出现 `com.mitv.tvhome`（实时，主路径）；
2. SCREEN_ON 后 1.5 秒用 `queryEvents` 探一次前台（兜底：个别固件亮屏不重发窗口事件）；只有"读不到"才补一次 1.1 秒后的重试。

### 放行条件（全满足才拉一次，否则不动画面）

1. 开关开启；
2. 机型受支持（小米电视家族）；
3. 屏幕仍亮，且距本次 SCREEN_ON ≤ 8 秒；
4. 本次唤醒还没处理过（`SCREEN_OFF` 才重置）；
5. 能读到前台应用（读不到就一律不动，不猜）；
6. 前台包名严格等于 `com.mitv.tvhome`；
7. 本桌面不在前台；
8. 距上次拉起 > 4 秒（防抖）。

没有捕捉到 SCREEN_ON（服务晚于亮屏才绑定）时 `sinceWake = -1`，直接判否——所以亮屏 8 秒后你再按 Home 也不会被抢。

### 开启方式

系统 设置 → 无障碍 → 已下载的服务 → 「大米电视 · 唤醒进入」→ 开启 → 允许。

系统里没有这一页时用 adb：

```sh
adb shell settings put secure enabled_accessibility_services io.github.ariinyume.fkuxmtv/io.github.ariinyume.fkuxmtv.WakeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

使用情况访问（`PACKAGE_USAGE_STATS`）现在是**可选**，只影响第 2 路探测：

```sh
adb shell appops set io.github.ariinyume.fkuxmtv GET_USAGE_STATS allow
```

诊断：`adb shell dumpsys activity io.github.ariinyume.fkuxmtv | grep Wake:` 输出 `enabled / a11y / connected / usage / visible / result / note / foreground`；每次被跳过都写明原因。

## 实机验证结果（2026-10-21）

| 项 | 值 |
| --- | --- |
| 电视型号 | 小米电视 S 55 |
| `Build.MODEL` | **L55M9-S / MiTV-MFTR0** |
| 固件 | **OS2.0.23.0.RFTMATV** |
| Android | 11（API 30） |

| 能力 | 结果 | 说明 |
| --- | --- | --- |
| 桌面本体 / 壁纸 / 首页入口 | ✅ | 安装后直接可用 |
| Switch 2 → HDMI 1 直达 | ✅ | 实机点按可直达（`com.xiaomi.mitv.tvplayer` + `input=23`） |
| 开机自动进入（`start_3rd_app` 老路径） | ✅ | 真重启后前台即 `FrameHomeActivity`，进程在开机约 30 秒时被拉起 |
| 开机自动进入（`BOOT_COMPLETED`，v0.5 方案 A） | ❌ | 广播送达晚于小米私有广播，当次进场由老路径完成，见下 |
| 短按遥控器电源键唤醒 / 开关机 | ❌ | 与 `adb reboot` 不是一条链路，广播不重发，调不起本桌面 |
| 息屏唤醒进入桌面 | ❌ | 链路断死，见下 |
| HOME 键接管（cleanyourtv 方案） | ❌ | 澎湃 2 上四道锁全破不了；强禁 tvhome 会黑屏，见附录 |

### 息屏唤醒：本固件不可能，结论定死

```
电源键息屏 → killAllThirdApp 屠杀（免杀名单形同虚设）
          → 进程死 → a11y 服务被踢设置 + 打 crashed 标记
          → 亮屏时无人监听 SCREEN_ON / 窗口事件
          → 小米桌面接管，完事
```

实测覆盖了三条路：

- 应用在前台按电源键 —— 杀；
- 先退回小米桌面再按 —— 照样杀；
- 想绕开电源键用空闲息屏 —— 这台固件的 `sleep_timeout` 是摆设，电源键是唯一的息屏路径。

**短按遥控器电源键 ≠ `adb reboot`**：那是待机/唤醒（`killAllThirdApp` + 息屏），广播不会重发，本桌面也不会被拉起——按一下电源键回不到大米电视，只能从应用列表手动打开，或者真的断电冷启动。

还有一个更直接的死结：**本机系统里没有无障碍设置页**，应用内「无障碍设置」按钮只会提示“系统没有无障碍设置页”，所以连手动开启的入口都不存在（只能靠 adb 写 `settings put secure`，而写了也会被下次息屏撤销）。也就是说这个开关在本机型上是纯粹的摆设。

设计上是个死结：前台服务被固件封杀（`AppsRelaunchController: isCallerSystem=false`，见上节），`BOOT_COMPLETED` 只负责“武装”不负责“监听屏幕”，于是无障碍服务成了唯一入口——而入口每息屏一次就被拆一次。入口被拆，代码侧无解。

两个“不算息屏”的例外：

1. **直接断电 / 关机再开机**：走完整开机链，`start_3rd_app` 还在，开机自动进入照常生效——但这是重启，不是息屏唤醒。
2. **固件升级后 MIUI 改了 `killAllThirdApp` 策略**：未来的事，未发生——毕竟这台电视已经停在澎湃2.0.23.0（笑

结论：**这台电视上，息屏唤醒 = 回小米桌面，属于固件层面的硬约束。** 真要打破只有一条路——root 后 patch `MitvActivityManagerService`。为唤醒功能给电视 root 刷机，值不值自己掂量，本分支不做。

## 开机自启实测（2026-10-22）

真重启（不是重连）后的取证。此前有一次「看起来失败」，其实是 `adb reboot` 落空：stderr 是
`error: device not found`，`/proc/uptime` 显示 4061 秒——电视压根没重启。**验收前先看 uptime。**

| 时刻 | 事件 |
| --- | --- |
| 01:20:45 | `adb reboot` 发出；01:21:11 重新连上，uptime 22 秒 |
| 01:21:06 | 系统 `BOOT_COMPLETED` 入队 |
| 01:21:07 | 开始分发，**41 个接收器串行跑了 18.5 秒** |
| 01:21:22 | 小米私有广播 `mitv.action.STR_BOOT_COMPLETED` 拉起 `BootGateActivity` |
| 01:22 前后 | 前台 `fkuxmtv/.FrameHomeActivity`，进程存活 1 分 24 秒（开机约 30 秒时被拉起） |

要分两件事说：

- **开机自启本身 ✅ 可用**：真重启后前台就是本桌面；`appops SYSTEM_ALERT_WINDOW` 熬过重启仍是 `allow`；长辈模式 `user_mode=2` 没丢。
- **`BOOT_COMPLETED` 这条路 ❌ 没生效**：当次进场由老路径完成（`start_3rd_app` → `BootGateActivity`，`am start` 调用方 uid 1000，即 tvhome 以系统身份发起）。`Start proc` 日志显示进程是为 `BootGateActivity` 起的，不是为广播起的——**接收器没抢到先机**。原因不是权限（appops 已放行），是分发顺序：标准广播要串行跑完 41 个接收器，小米私有广播早约 16 秒先把桌面拉起来了。
- 两道保险按设计工作：老路径先写 `attempted`，接收器即便送达也判「本次开机已尝试」，没有二次抢画面。这不是 bug，是当初刻意加的闸门。

结论：本机**开机自启靠的是 `start_3rd_app`**。v0.5 的 `BootReceiver` 留着当兜底（换机器、换固件后顺序可能反过来），但别指望它在小米国行固件上抢先。

## 附：HOME 接管（cleanyourtv 研究 + 本机实测）

背景研究，供他人参考：

- 《cleanyourtv_HOME接管机制研究》 https://www.workbuddy.cn/space/d/jBCrx54mh7R3tYhnvGqUp7
- 《fkuxmtv_开机自启改造包（方案 A）》 https://www.workbuddy.cn/space/d/dhfFkqXYcO2xxDDtyO6gWa

### cleanyourtv 的手法（值得抄的部分）

没有黑科技，靠时序五步：识别原版桌面（白名单含 `com.mitv.tvhome`）→ 装新 launcher →
`am start -W` 先验证新桌面起得来 → `pm disable-user com.mitv.tvhome` →
`cmd package set-home-activity`（传完整组件名）→ `resolve-activity` 复核，失败就回滚。
脚本注释原话：*Android drops the preference whenever the set of HOME apps changes*——候选集合一变，
系统存的默认桌面记录就被丢弃。所以**先夺嫡（禁原版），再登基（设默认）**，顺序就是灵魂。

### 本机实测：四道锁，无 root 全破不了

| 手法 | 结果 |
| --- | --- |
| `pm disable-user --user 0 com.mitv.tvhome` | ✅ 成功 |
| `cmd package set-home-activity --user 0 …/.FrameHomeActivity` | ❌ 报 Success 但不生效，`resolve-activity` 仍返回 `com.xiaomi.mitv.settings/.entry.FallbackHome` |
| `pm disable` 掉 `com.xiaomi.mitv.settings/.entry.FallbackHome` | ❌ `SecurityException: Shell cannot change component state`（组件受保护） |
| `android.settings.HOME_SETTINGS` → 角色选择器里选「大米电视」 | ✅ 打开的是 `com.android.permissioncontroller/.role.ui.DefaultAppActivity`，操作后 `ROLE_HOME` 确实写成 `io.github.ariinyume.fkuxmtv` |
| 按 HOME 键 | ❌ 仍被 MIUI 硬编码到 `FallbackHome`，`ROLE_HOME` 形同虚设 |

几条硬证据：

- `Preferred Activities User 0` 里存着 `com.xiaomi.mitv.settings/.entry.FallbackHome`，`mAlways=true`（永久首选），`Selected from` 能看到本桌面曾是候选。
- `set-home-activity` 是个**半残的清障车**：它能抹掉那枚 `mAlways` 记录（执行后该段消失），但死活不写新记录——清完 `resolve` 依然返回 FallbackHome。
- 解析器也被打过补丁：本桌面 `priority=0`，FallbackHome `priority=-1000`，没有首选记录时系统**仍**选 FallbackHome。
- 遥控器 HOME 发的是标准 `KEYCODE_HOME`（不是研究文档里说的 `KEYCODE_ALL_APPS`）；30 秒捕获窗口内 7 次按压，每次都由 uid 0 直接 `am start` `FallbackHome`，`(has extras)` 说明是 MIUI 自己拼的 intent，绕过标准解析。
- 行为还怪：奇数次按压 FallbackHome 进程死亡 → 前台落回本桌面；偶数次新进程拉起迷你启动器 → 赖在屏幕上不走。

### ⚠️ 致命副作用：禁 tvhome 后短按电源键会黑屏

`pm disable-user com.mitv.tvhome` 确实成功，ROLE_HOME 也拿到了，但**开机解析桌面时小米自己的
FallbackHome 没接住，boot 流程断在半空**——短按电源键关机再开就是一块黑屏（屏幕停在设置页）。
恢复只有一条：`pm enable --user 0 com.mitv.tvhome`，系统随即自拉 `com.mitv.tvhome/.BootActivity`，
HOME 键回 tvhome，桌面恢复，`user_mode=2` 未丢。**没有物理恢复手段时不要禁 tvhome。**

### 结论

四道锁——框架层硬编码 FallbackHome、残缺的 `set-home-activity`、打过补丁的解析器、受保护的
FallbackHome / 遥控器服务组件——无 root 全部破不了。`ROLE_HOME` 能拿到（角色系统认本桌面），
但 MIUI 的按键路径根本不问它。**对普通用户，开机自启就是最优解**；真要接管 HOME 得 root 后改框架。

## 仓库与分发边界

- 本仓库**只存放代码**。自选壁纸（`assets/wallpapers/local/`）与构建产物（APK）**不进仓库、不发 GitHub Release**。
- APK 只在本地 `../dist/` 保留，用 adb 或 U 盘安装。
- 需要重新打包时把照片目录放在本机，按上面「壁纸打包」跑一遍脚本即可，`local/` 始终被 Git 忽略。

## 已知限制

- HDMI 端口号（23 = HDMI 1、24 = HDMI 2）是在原仓库 `MiTV-MTSU0` 固件上实测得到的。L55M9-S / MiTV-MFTR0 上沿用同一套小米 `com.xiaomi.mitv.tvplayer` 组件；若该固件不接受 `input` 参数，会退回信号源列表让你手选，不会闪退。
- 已在真机 `L55M9-S / MiTV-MFTR0` 验证，首次安装请先确认 HDMI 按钮行为。
- 本分支签名与上游官方 APK 不同，不能覆盖安装原版，也不能被原版覆盖。
- 壁纸来自本地目录，仅自用分发；公开分发需确认图片授权。
- **息屏唤醒在本机（L55M9-S / MiTV-MFTR0 / OS2.0.23.0.RFTMATV / Android 11）不可实现**：电源键息屏触发 `killAllThirdApp`，进程被杀且无障碍授权被系统撤销，亮屏后无人监听；仅能靠重启走开机自动进入。
- 开机自动进入有两条路：`BOOT_COMPLETED`（需 `SYSTEM_ALERT_WINDOW` app-op，否则被后台启动限制拦下）与 `start_3rd_app` 兜底。固件升级后两者都可能变化，升级后请重新确认。
- 方案 A（`BOOT_COMPLETED`）已在 L55M9-S / MiTV-MFTR0 上实测：广播送达晚于小米私有广播，**未构成实际进场路径**（详见「开机自启实测」）；`start_3rd_app` 仍是唯一有效路径。
- 运行中按遥控器 Home 键仍会回小米原桌面：`com.mitv.tvhome.permission.HOME_STATE` 由固件持有，无 root 改不了；`cmd package set-home-activity` 在该固件报 Success 但不生效。
- HDMI 端口映射（`input=23` / `24`）与 `MiTV-MTSU0` 一致；L55M9-S / MiTV-MFTR0 上 **Switch 2 → HDMI 1 已实测直达**。`hdmi:2`（Apple TV）未单独验证，接线不同请自行改端口。
