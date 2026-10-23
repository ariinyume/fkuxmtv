# 自用分支说明（fkuxmtv）

基于 [op7418/guizang-tv](https://github.com/op7418/guizang-tv) 的个人自用改动，代码仍为 MIT 许可，上游署名与壁纸许可文件原样保留。

## 测试设备

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
| 版本 | `0.4.0-dev` / code 5 | `0.4.0-fkuxmtv.10` / code 15 |
| 壁纸 | 仅内置五张 Unsplash | 构建时打包 `F:\Desktop\壁纸` 的 15 张图，优先于内置照片 |
| 机型门禁 | 仅 `MiTV-MTSU0` | 识别小米电视家族（`MiTV*` / `L\d{2}M\d` 等），含目标机 **MiTV-MFTR0**；HDMI 直达不再硬性依赖机型名 |
| 开机进入 | 无（依赖小米 `start_3rd_app`） | 吃系统 `BOOT_COMPLETED`，`SYSTEM_ALERT_WINDOW` 作后台启动豁免；`start_3rd_app` 保留兜底 |
| 唤醒进入 | 无 | 亮屏后 8 秒内若停在小米原桌面，拉起本桌面一次（无障碍服务驱动，默认关闭） |
| 桌面守护 | 无 | 前台一旦回到小米原桌面就切回本桌面（默认开启；返回键/磁贴主动过去可安静 5 分钟）。见 [桌面守护](#桌面守护v80-2026-10-23) |
| 被强杀后自动恢复 | 无 | 待机被 `killAllThirdApp` 强杀后，借「通知监听重绑」回到进程，再把无障碍授权写回系统设置。见 [自动恢复](#被强杀后自动恢复v09-2026-10-23) |

改动文件：

- `app/build.gradle`：`namespace` / `applicationId` / `versionCode` / `versionName`
- `app/src/main/java/io/github/ariinyume/fkuxmtv/*`：源码目录与 `package` 声明整体迁移
- `HomeItems.java`：`DEFAULTS` 顺序与默认名称
- `BootReceiver.java`：系统开机广播入口（新增）；`BootStart.java`：新增 `enterFromBoot()`、悬浮窗状态与授权入口
- `AndroidManifest.xml`：`RECEIVE_BOOT_COMPLETED` + `SYSTEM_ALERT_WINDOW`，注册 `BootReceiver`
- `WakeHome.java` / `WakePolicy.java` / `WakeAccessibilityService.java`：唤醒进入（新增）
- `HomeGuard.java` / `HomeGuardPolicy.java`：桌面守护（新增，v0.8）；`WakeAccessibilityService` 同一条无障碍通道里跑两套闸门
- `Revival.java` / `RevivalListener.java` / `A11yList.java` / `App.java`：被强杀后的自动恢复（v0.9 新增，v0.10 补上有界重试链）；`A11yList` 是纯逻辑，JUnit 覆盖
- `AndroidManifest.xml`：`WRITE_SECURE_SETTINGS`（需 adb 授予）、`RevivalListener` 通知监听服务、`android:name=".App"`
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
adb install -r dist/guizang-tv-fkuxmtv-0.4.0-fkuxmtv.10.apk
# 下面三项重装/换签名后都要重做一次（都是「系统权限」，装的时候不会自动给）
adb shell appops set io.github.ariinyume.fkuxmtv SYSTEM_ALERT_WINDOW allow      # 开机自动进入
adb shell pm grant io.github.ariinyume.fkuxmtv android.permission.WRITE_SECURE_SETTINGS  # 自动恢复：写回无障碍授权
adb shell cmd notification allow_listener \
  io.github.ariinyume.fkuxmtv/io.github.ariinyume.fkuxmtv.RevivalListener         # 自动恢复：被强杀后的回程票
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

> **MiTV-MFTR0 实测结论（2026-10-23 修正）：只有遥控器电源键那条链会杀进程。** `killAllThirdApp` 强杀 + 把无障碍授权从设置里踢掉（带 crashed 标记）只发生在按电源键待机时；用 `KEYCODE_SLEEP` 熄屏则进程与授权都活着，唤醒能直接回到本桌面，见「[息屏唤醒](#息屏唤醒按电源键这条链是死的熄屏可以2026-10-23-修正)」。另外本机系统没有无障碍设置页，入口只能用 adb 写 `settings put secure`。

设置 → 电视与启动 → 唤醒后进入桌面。默认**关闭**。

### 为什么是无障碍服务，而不是前台服务

v0.4.0-fkuxmtv.3 用前台服务守 `ACTION_SCREEN_ON`，在 MiTV-MFTR0 上从未启动过一次。实机日志：

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
| `Build.MODEL` | **MiTV-MFTR0** |
| 固件 | **OS2.0.23.0.RFTMATV** |
| Android | 11（API 30） |

| 能力 | 结果 | 说明 |
| --- | --- | --- |
| 桌面本体 / 壁纸 / 首页入口 | ✅ | 安装后直接可用 |
| Switch 2 → HDMI 1 直达 | ✅ | 实机点按可直达（`com.xiaomi.mitv.tvplayer` + `input=23`） |
| 开机自动进入（`start_3rd_app` 老路径） | ✅ | 真重启后前台即 `FrameHomeActivity`，进程在开机约 30 秒时被拉起 |
| 开机自动进入（`BOOT_COMPLETED`，v0.5 方案 A） | ❌ | 广播送达晚于小米私有广播，当次进场由老路径完成，见下 |
| 短按遥控器电源键（真待机） | ❌ | `killAllThirdApp` 强杀 + 无障碍授权被抹，90 秒内无自启；待阶段 2 复活机制，见下 |
| `KEYCODE_SLEEP` 熄屏后唤醒 | ✅ | 2026-10-23 实测：30s / 180s 后进程存活、授权未被改写，唤醒回本桌面 |
| 桌面守护（回小米桌面即切回） | ✅ | v0.8 实测，见 [桌面守护](#桌面守护v80-2026-10-23) |
| HOME 键接管（cleanyourtv 方案） | ❌ | 澎湃 2 上四道锁全破不了；强禁 tvhome 会黑屏，见附录 |

### 息屏唤醒：按电源键这条链是死的，熄屏可以（2026-10-23 修正）

早前的结论「本机息屏唤醒不可用」只对**遥控器电源键**成立。当时只测了电源键，把两条完全不同的链路当成了同一条。

**链路 A：遥控器电源键（`KEYCODE_POWER`）— 死**

```
电源键 → PowerManagerService killAllThirdApp forcestop
       → 进程死 + 无障碍授权被从 enabled_accessibility_services 删除（Crashed services）
       → 90 秒内无任何自启；亮屏落回小米桌面
```

实机日志（2026-10-23 10:49:44）：

```
ActivityManager: Killing 19488:io.github.ariinyume.fkuxmtv/u0a47 (adj 100): stop ...
MitvActivityManagerService: PowerManagerService killAllThirdApp forcestop packageName:io.github.ariinyume.fkuxmtv
```

**链路 B：`KEYCODE_SLEEP`(223) 只熄屏 — 通**

- 熄屏 30 秒 / 180 秒，进程都还在，`enabled_accessibility_services` 一字未改，`Crashed services` 为空；
- 之后唤醒，前台直接就是 `FrameHomeActivity`。

所以「息屏唤醒 = 回小米桌面」不是固件硬结论，真正的硬约束只有一条：**按电源键待机会强杀本进程**。

**短按遥控器电源键 ≠ `adb reboot`**：那是待机/唤醒（`killAllThirdApp` + 息屏），广播不会重发，本桌面也不会被拉起——按一下电源键回不到大米电视，只能从应用列表手动打开，或者真的断电冷启动。

另外两个「不算息屏」的例外：**直接断电 / 关机再开机**走完整开机链，`start_3rd_app` 还在，开机自动进入照常生效；以及未来固件改了 `killAllThirdApp` 策略。

## 桌面守护（v0.8 / 2026-10-23）

动机：HOME 键被固件写死到 `com.mitv.tvhome`，`MitvSettings.isThirdpartyHomeAllowed` 又是系统签名白名单（只有系统进程能写 `/data/system/mitv_features.xml`）。既然抢不到「默认桌面」这个身份，就改成「只要小米桌面出现在前台，就把它盖掉」。

实现：复用同一条无障碍通道，加第二套闸门 `HomeGuardPolicy`（纯逻辑，JUnit 覆盖）。默认**开启**，可在 设置 → 电视与启动 → 桌面守护 关闭。

闸门（全通过才接管）：

| 闸门 | 值 |
| --- | --- |
| 机型 / 开关 | 小米电视家族且守护开启 |
| 屏幕 | 亮着 |
| 前台包名 | 必须是 `com.mitv.tvhome`（HDMI、播放器、任何应用都不会被抢） |
| 自己是否已在前台 | 是则不动 |
| 安静期 | 用户主动按返回键 / 点「小米原桌面」磁贴后 5 分钟内不接管；一旦离开小米桌面立即解除 |
| 防抖 | 两次接管间隔 ≥ 4 秒 |
| 熔断 | 1 分钟内最多接管 3 次，超出后停手（防止固件把两边来回弹） |
| 确认延迟 | 事件到达后先等 1.5 秒，再确认一次前台仍是小米桌面才动手 |

实机验证（2026-10-23，`adb shell dumpsys activity io.github.ariinyume.fkuxmtv | grep Guard`）：

| 场景 | 结果 |
| --- | --- |
| 在 HDMI 画面按 Home | ✅ 1.5s 后接管，日志 `guard pull #1`，`note=守护接管成功` |
| 从本桌面按返回键去小米桌面 | ✅ 8 秒后仍停在小米桌面（`sinceUserExit` 生效） |
| 打开 Spotify 后再按 Home | ✅ 安静期解除，正常接管 |
| 切 HDMI 1 | ✅ 不被抢，前台保持 `ExternalSourceActivity` |
| `KEYCODE_SLEEP` 熄屏 → 唤醒 | ✅ 进程存活，回本桌面，接管计数在熄屏时归零 |

顺带改掉两处会丢焦点的属性（aerial-tv 踩过的坑）：`FrameHomeActivity` 去掉 `excludeFromRecents="true"` 与 `clearTaskOnLaunch="true"`——任务被 trim 后再拉回是新建 Activity，焦点会跳回第一个磁贴。

**已知边界**：按电源键待机后进程被杀，守护随进程一起消失，此时回小米桌面不会被接管——这一段的补法见下节「被强杀后自动恢复」。

## 被强杀后自动恢复（v0.9 / 2026-10-23）

上一节留的缺口是这条链：

```
电源键待机 → killAllThirdApp 强杀 → 进程死 + 无障碍授权被系统从设置里删掉
          → 唤醒后没人监听 → 小米桌面停在前面
```

Android 对「被强制停止」的应用有一条铁律：**收不到广播、跑不了 JobScheduler、闹钟全清**。所以常规的「开机广播 / 服务自启 / 定时器」在这里全部无效——这也解释了为什么 v0.5 的 `BOOT_COMPLETED` 方案对息屏唤醒毫无帮助（它只在完整开机时成立）。

唯一的例外是**通知监听**：系统不把它当作应用自启，而是自己的绑定对象；进程一死，`NotificationManagerService` 会在毫秒级把它重新绑上，并且这个授权在 force-stop 之后**仍然留在设置里**（无障碍授权则会被删）。于是它成了唯一可用的「回程票」。

### 三段式

1. **回程票**：`RevivalListener`（`NotificationListenerService`）什么都不读，只重写 `onListenerConnected`——被重新绑上就等于拿到了一个进程。
2. **写回授权**：`Revival.ensure()` 用 `WRITE_SECURE_SETTINGS` 把本服务追加回 `enabled_accessibility_services`，并置 `accessibility_enabled=1`。系统随即重新绑定无障碍服务，守护与唤醒一起复活。`A11yList` 负责这个列表的增删（保留小爱两个服务、短名 `pkg/.Cls` 与长名 `pkg/pkg.Cls` 等价、去重），纯 Java，8 个单测覆盖。
3. **兜底自检**：`App.onCreate` → 400ms 后跑同一套检查。任何进进程的路径（用户打开应用、开机广播、通知绑定）都会顺手把授权补回来。

**不抢画面**：恢复只修授权，是否接管仍由 `HomeGuardPolicy` / `WakePolicy` 判断（前台必须是小米桌面）。复活时如果没有小米桌面在前台，就安静待着——否则会和 HDMI-CEC、正在看的片源打架。

### 实机验证（MiTV-MFTR0 / Android 11，2026-10-23）

`am force-stop` 走的就是 `killAllThirdApp` 同一条路（日志确认过：`PowerManagerService killAllThirdApp forcestop packageName:...`），所以用它做等价复现：

```
11:19:55.585 Killing 24990:io.github.ariinyume.fkuxmtv (adj 0): stop
11:19:55.586 notification listener binding died: ComponentInfo{.../RevivalListener}
11:19:55.638 binding: Intent { act=...NotificationListenerService cmp=.../.RevivalListener }
11:19:55.696 Start proc 25252:io.github.ariinyume.fkuxmtv for service {...RevivalListener}
11:19:55.772 GuiZangRevive: notification listener bound: restarting from a killed process
11:19:55.784 GuiZangRevive: grant restored via 通知监听: ...karaoke...:...voicecontrol...:...fkuxmtv...
```

从被杀到授权写回 **199 毫秒**。随后：

| 检查项 | 结果 |
| --- | --- |
| 进程 | ✅ 复活（新 PID 25252），`dumpsys package` 里 `stopped=false`——「已停止」标记被清掉，下次完整重启照样收得到开机广播 |
| 无障碍 | ✅ `enabled_accessibility_services` 已包含本服务，服务重新绑定 |
| 守护 | ✅ 手动进小米原桌面后 1.5 秒被接管，`note=守护接管成功` |
| 小爱服务 | ✅ 两个原有条目原样保留 |

### 为什么没做 SyncAdapter

aerial-tv 还备了一条 `SyncAdapter` 复活路径，用来对付「Android 9 且 `ro.config.low_ram=true` 时系统拒绝所有通知监听」的机型（他们的 MiTV-MFTP0）。本机 `ro.config.low_ram=false`，通知监听 199ms 就回来了，再加一个账户 + 同步适配器只会多出一套系统账户和绑定服务，覆盖不到任何本机场景——所以不做，只在这里留个记录。

### 电源键真实待机实测（2026-10-23 11:31，v0.9 暴露的缺口）

上面那条 `am force-stop` 是自己按下去就完了，顺序正好：条目被删 → 进程死 → 系统重绑监听 → 我们立刻写回。**真实电源键不一样**：`killAllThirdApp` 是在一个循环里连续 force-stop 十几个包，我们自己那条的无障碍清除落在了「监听已经重绑、进程已经起来」**之后**。于是 v0.9 的 `ensure()` 读到的还是旧列表（`A11yList.has` 为真），以为不用写，4 秒后那次检查又因为「条目已被删」直接 return，**没人写回**，只能等系统自己重试：

```
11:31:44.691 Killing 25763:...fkuxmtv (adj 100): stop
11:31:44.694 notification listener binding died
11:31:44.704 MitvActivityManagerService: ... killAllThirdApp forcestop packageName:io.github.ariinyume.fkuxmtv
11:31:44.792 Start proc 26063 ... for service {...RevivalListener}
11:31:44.896 GuiZangRevive: notification listener bound      ← 此刻读到的还是旧列表，判断「无需写回」
11:32:07.500 GuiZangRevive: notification listener bound      ← 系统 22.6 秒后才第二次重绑
11:32:07.504 GuiZangRevive: grant restored via 通知监听
11:32:13.012 GuiZangRevive: asked Android to bind the service again
11:32:13.024 GuiZangWake: accessibility service connected     ← 守护到这一刻才真正回来
```

即 **21.6 秒**里守护是死的。这段时间如果用户按 Home，电视就停在小米桌面不动了。

**v0.10 的修法**：把「一次检查」改成**有界的三轮循环**（`Revival.round()`），每轮都先 `restoreEntry()` 再决定要不要强制重绑，「条目没了」由本轮自己写回，不再指望下一次系统重绑；`repairRunning` 保证一个进程只跑一条链，`MAX_ROUNDS = 3` 保证跑不通也不会空转。同样是 force-stop 复现，v0.10：

```
11:36:39.265 Killing 27157:...fkuxmtv (adj 0): stop
11:36:39.339 Start proc 27456 ... for service {...RevivalListener}
11:36:39.410 notification listener bound
11:36:39.419 grant restored via 通知监听
11:36:44.926 asked Android to bind the service again
11:36:44.940 accessibility service connected            ← 5.7 秒
```

**21.6 秒 → 5.7 秒**。剩下的 5.7 秒结构上是「4 秒宽限 + 1.5 秒删除/重加间隔」，是让系统自己绑与强制绑之间的取舍，再压就得牺牲「不跟系统 binder 抢」的稳定性。

### 一个仍存在的窗口

从「被杀」到「无障碍服务重新连上」的这 5～6 秒里，`HomeGuard` 和 `WakeHome` 都不在线：**这段时间按 Home 不会被接管**（待机唤醒回本桌面不受影响，因为系统恢复的是上次的任务栈）。如果用户在 5 秒内就按了 Home，会停在小米桌面，直到下一次回到小米桌面才会被守护拉回。这个窗口消不掉——除非常驻一个杀不掉的服务，而这台固件不给。

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

### cleanyourtv 的手法

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

## 参考与致谢

本分支能走到「待机唤醒也能回本桌面」，主要是站在别人的肩膀上：

| 项目 | 许可 | 借鉴了什么 |
| --- | --- | --- |
| [op7418/guizang-tv](https://github.com/op7418/guizang-tv) | MIT | 整个桌面本体，本分支的上游 |
| [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) | MIT | 原生列表桌面与小米适配的起点 |
| [nthnclrk/CleanFrame](https://github.com/nthnclrk/CleanFrame) | MIT | 界面方向：照片背景、大字纵向列表、克制的焦点高亮 |
| **[pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv)** | MIT | 桌面守护与强杀复活的整套思路 |


## 仓库与分发边界

- 本仓库**只存放代码**。自选壁纸（`assets/wallpapers/local/`）与构建产物（APK）**不进仓库、不发 GitHub Release**。
- APK 只在本地 `../dist/` 保留，用 adb 或 U 盘安装。
- 需要重新打包时把照片目录放在本机，按上面「壁纸打包」跑一遍脚本即可，`local/` 始终被 Git 忽略。

## 已知限制

- HDMI 端口号（23 = HDMI 1、24 = HDMI 2）是在原仓库 `MiTV-MTSU0` 固件上实测得到的。MiTV-MFTR0 上沿用同一套小米 `com.xiaomi.mitv.tvplayer` 组件；若该固件不接受 `input` 参数，会退回信号源列表让你手选，不会闪退。
- 已在真机 `MiTV-MFTR0` 验证，首次安装请先确认 HDMI 按钮行为。
- 本分支签名与上游官方 APK 不同，不能覆盖安装原版，也不能被原版覆盖。
- 壁纸来自本地目录，仅自用分发；公开分发需确认图片授权。
- **遥控器电源键待机会强杀进程**（`killAllThirdApp`，并把无障碍授权从系统设置里删掉）：v0.9 起可自愈——系统重新绑定通知监听时，用 `WRITE_SECURE_SETTINGS` 把授权写回去。v0.10 修掉了一个真实待机才会出现的顺序问题（授权清除落在监听重绑之后），恢复耗时从 **21.6 秒压到 5.7 秒**（实测记录见「[被强杀后自动恢复](#被强杀后自动恢复v09-2026-10-23)」）。代价是这两个权限只能用 adb 授予，重装后要重做（见「安装」）；并且这 5～6 秒内守护不在线，按 Home 不会被接管。
- **桌面守护依赖无障碍服务在线**：进程被杀后守护随之消失，v0.10 起约 5.7 秒回来；此外 1 分钟内接管上限 3 次，超出会停手（防弹跳），亮屏后自动归零。
- 开机自动进入有两条路：`BOOT_COMPLETED`（需 `SYSTEM_ALERT_WINDOW` app-op，否则被后台启动限制拦下）与 `start_3rd_app` 兜底。固件升级后两者都可能变化，升级后请重新确认。
- 方案 A（`BOOT_COMPLETED`）已在 MiTV-MFTR0 上实测：广播送达晚于小米私有广播，**未构成实际进场路径**（详见「开机自启实测」）；`start_3rd_app` 仍是唯一有效路径。
- 运行中按遥控器 Home 键仍会**先**回小米原桌面（`com.mitv.tvhome.permission.HOME_STATE` 由固件持有，无 root 改不了；`cmd package set-home-activity` 在该固件报 Success 但不生效），v0.8 起由「桌面守护」在 1.5 秒后切回本桌面。
- HDMI 端口映射（`input=23` / `24`）与 `MiTV-MTSU0` 一致；MiTV-MFTR0 上 **Switch 2 → HDMI 1 已实测直达**。`hdmi:2`（Apple TV）未单独验证，接线不同请自行改端口。
