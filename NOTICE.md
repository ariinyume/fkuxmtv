# 来源与许可

- 初始工程参考与衍生基础：Gathin23/cleanyourtv，MIT License。
  - https://github.com/Gathin23/cleanyourtv
  - 本地定制基线：`69c93b66a75c70a9e386adff327b6a51c12addc5`
  - 原始版权：Copyright (c) 2026 Gathin，保留于根目录 LICENSE。
- 设计与设置交互参考：nthnclrk/CleanFrame，Nathan Clark。
  - https://github.com/nthnclrk/CleanFrame
  - 参考版本：`fa537d1892b71d2de2bf94c24cbff3812e9f70d5`
  - 参考其分组、首页预览、焦点与已应用值分离、确认应用和逐层返回的交互。
  - 本项目没有打包其 Tizen 代码、字体或照片。
  - 图标采用下述开源 SVG，不打包 Android 应用的原始图标。
- 桌面守护与被强杀后自动恢复的机制参考：pengchujin/aerial-tv，MIT License，Copyright (c) 2026 pengchujin。
  - https://github.com/pengchujin/aerial-tv
  - 参考其「空实现通知监听作为 force-stop 后的复活入口，再用 WRITE_SECURE_SETTINGS 写回无障碍授权」的链路，
    以及「原桌面回到前台后延迟一拍、确认仍在前台再拉回」的守护判定与拉回时去掉 `excludeFromRecents` /
    `clearTaskOnLaunch` 的处理。
  - 本项目没有复制其 Kotlin 源码，相关部分为针对本机固件（MiTV-MFTR0 / Android 11）的 Java 独立实现，
    调用序列与判定条件按本机实测结果重写。
- 默认渐变、布局示意及本项目绘制代码采用 MIT 许可。
- 品牌 SVG：Simple Icons，CC0-1.0；工具 SVG：Phosphor Icons Fill（勾选与箭头为 Regular），MIT。原 SVG、精确提交来源与许可证位于 `app/src/main/assets/icons/`；Android VectorDrawable 是同一路径数据的格式转换。商标权不因图标的开源许可而转移。
- 使用 Android 系统字体，不打包 Outfit 字体。
- Android Gradle Plugin、Gradle 与 JUnit 是构建或测试依赖，各自适用其许可证；不代表被重新许可为 MIT。
- 各产品与商标归其权利人所有。本项目不隶属于小米、Sony、Apple 或哔哩哔哩。

v0.1.1 起，内置五张经原页面核对的 Unsplash 照片，适用 **Unsplash License**，不适用代码的 MIT 许可。逐张署名、原始链接和处理方式见 [壁纸说明](docs/WALLPAPERS.md)，许可通知也随 APK 打包。

用户自行加入的照片不因放入本工程而自动获得 MIT 许可。其他未授权公开的私人试用素材、实机诊断数据、签名密钥均不在公开源代码或官方 APK 中。

main 的可选天气使用 [Open-Meteo](https://open-meteo.com/) 数据（[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)），温度取整、天气码转换为中文及自绘矢量图标；城市搜索由 Open-Meteo / GeoNames 提供，IP 城市由 [IPWhois](https://ipwhois.io/) 提供。托管 API 的使用条款独立于本项目代码许可，详见[天气说明](docs/WEATHER.md)。
