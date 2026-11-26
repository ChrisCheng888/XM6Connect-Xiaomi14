# XM6 Connect（小米 14 + Sony WH-1000XM6 定制版）

> **专机定制说明**：本项目是为 **小米 14（`houji` / HyperOS / Android 16）+ Sony WH-1000XM6（`YY2984`，固件 `3.1.5`）** 深度实测与定制开发的原生 Android 伴侣应用（Kotlin + Jetpack Compose + Material 3 Expressive）。  
> 所有蓝牙 RFCOMM 协议指令、字节极性、状态回传与 Shizuku LDAC 990kbps 锁码率链路均已在上述真机组合上逐项抓包校准。

---

## 核心特性

- **原生零臃肿蓝牙直连（Sony MDR Protocol V2/V3）**
  - 直接通过 Classic Bluetooth RFCOMM（`956C7B26-D49A-4BA8-B03F-B17D393CB6E2`）连接耳机，实现 Table 1 (`0x0C`) + Table 2 (`0x0E`) 帧编解码与 ARQ 序列号确认。
- **降噪 / 环境声全参数无损切换**
  - 降噪 / 环境声 / 关闭三段滑动切换；支持 1–20 档通透强度、关注语音、自动环境声与低/中/高灵敏度。
  - 已校准 WH-1000XM6 `0x19` 协议提交位（`ValueChangeStatus = 0x01`），跨模式切换完整保留通透强度、关注语音与自动环境声状态，不再重置。
- **聆听模式单帧原子直切**
  - 首页独立三段弹簧滑动胶囊（`标准 | 背景音乐 | 电影`），背景音乐展开 `房间 / 客厅 / 咖啡馆` 空间距离。
  - 采用单帧原子切模（`E8 04 00` / `E8 09 00 <room>`），消除「背景音乐 ↔ 电影」切换中间 1 秒退回标准听感的断层，并附带状态防抖保护。
- **10 段图形均衡器（31Hz – 16kHz）**
  - 完整适配 XM6 原生 10 段 EQ（`-6 dB ~ +6 dB`），首页提供实时曲线预览与大尺寸预设网格；**全部预设（含初始预设）均支持修改覆盖、另存为新、删除与一键恢复默认**。
- **快捷功能与设备控制**
  - **DSEE Extreme**（含受空间音效限制时的「已暂停」实时状态同步）、**智能免摘**（含 `5s / 15s / 30s / 不关` 时长调节）、**触控面板**开关。
  - **多点连接**（读取已连接设备列表、一键切换播放设备/断开设备）、**佩戴暂停**、**自动关机**（适配 XM6 佩戴检测型 `摘下 / 关`）、**NC/AMB 按键循环模式**。
- **Shizuku 免 Root 锁定 LDAC 990kbps**
  - 通过 `ShizukuBinderWrapper` + `HiddenApiBypass` 直调系统 `IBluetoothA2dp`，一键或伴生连接自动锁定 `LDAC 990k / 660k / ABR`，实时回读当前系统音频编码与码率。
- **2×2 桌面小组件 + 自适应主题**
  - 支持「浅色 / 深色 / 随当地时间（07:00–19:00 浅色，19:00–07:00 深色）」主题切换；Jetpack Glance 2×2 桌面小组件实时跟随 App 主题色与耳机电量/编码/降噪状态，支持桌面直接切模式。
- **内置协议调试台**
  - 可在「音质 / 高阶 → 协议调试台」实时查看 TX/RX 十六进制帧日志、一键导出耳机 Table 1 / Table 2 能力表，并支持手动发送 Table 1 / Table 2 原始 Hex 帧以辅助其他机型逆向校准。

---

## 已验证运行环境

| 维度 | 实测环境 |
| --- | --- |
| 手机型号 | 小米 14（`houji`） |
| 手机系统 | HyperOS（Android 16 / API 36） |
| 耳机型号 | Sony WH-1000XM6（`YY2984`） |
| 耳机固件 | `3.1.5` |
| 编译工具链 | Android Gradle Plugin `9.3.1` / Kotlin `2.2.10` / JDK 17–21 / `compileSdk = 37` / `minSdk = 31` |

---

## 使用注意事项（必读）

1. **严禁与官方 Sony Sound Connect 同时运行**
   - WH-1000XM6 的 RFCOMM 控制通道不支持多 App 并发占用。使用本 App 前请**彻底退出并关闭官方 Sound Connect（包含后台常驻）**，否则会出现连接失败、指令无响应或状态互相覆盖。
2. **Shizuku 锁定 LDAC 990kbps 前置条件**
   - 需先安装并启动 [Shizuku](https://shizuku.rikka.app/)（无线调试或 ADB 启动），并在本 App「音质」页完成授权。
   - **小米 HyperOS 特别提醒**：建议在「开发者选项」中开启「USB 调试（安全设置）」，并在应用信息中为 **Shizuku** 和 **XM6 Connect** 开启「自启动」与「无限制省电策略」，否则伴生连接自动锁 990k 可能被系统拦截。
3. **LE Audio（LC3）与 LDAC 互斥**
   - 若耳机或系统蓝牙已开启 LE Audio，蓝牙音频会工作在 LC3 而无法使用 LDAC。此时 App 首页顶部胶囊会提示 `LC3 · 切LDAC`，需先切回「LDAC 音质」再锁定 990k。
4. **LDAC 990k 与多点连接带宽限制**
   - 同时开启双设备多点连接 + LDAC 990kbps 在强干扰环境下可能出现偶发卡顿，属蓝牙射频物理带宽限制；如遇断续可在「音质」页切换为 `660k` 或 `ABR`。
5. **桌面小组件与主题同步**
   - 2×2 桌面小组件严格跟随 **App 内设定的主题**（浅色 / 深色 / 随当地时间），不单纯跟随系统深色开关。若 HyperOS 桌面长时间未刷新小组件，请确认已关闭省电限制。
6. **固件版本变更与调试台风险提示**
   - 本项目的协议载荷基于 WH-1000XM6 固件 `3.1.5` 校准。Sony 后续固件更新若改动子类型或字节布局，可能需要重新校准。
   - 使用「协议调试台」发送原始 Hex 时请谨慎操作，切勿向耳机盲发未知写指令（尤其是 OTA / 复位相关命令族）。

---

## WH-1000XM6 (`3.1.5`) 实测协议速查

> 以下指令均在实机抓包验证，可供二次开发或移植其他机型参考（核心实现见 [`Xm6ProtocolV2.kt`](app/src/main/java/com/xm6/app/protocol/Xm6ProtocolV2.kt)）：

| 功能 | 表 / 命令 | 载荷格式与已验证行为 |
| --- | --- | --- |
| 降噪 / 环境声 / 自动环境声 | T1 `66/67/68/69 19` | `68 19 [01=提交] [effect:0/1] [mode:0=ANC,1=ASM] [voice:0/1] [level:1..20] [autoAmb:0/1] [sens:0/1/2]`；第 3 字节为 `0x00` 时固件会忽略修改 |
| 10 段均衡器 | T1 `56/57/58/59 00` | `58 00 [presetId:00/A0/A1] 0A [10×(db+6), 0..12]` |
| 聆听模式 - 电影 | T1 `E6/E7/E8/E9 04` | `E8 04 00`=开，`E8 04 01`=关（`EnableDisable` 反向极性；开启时固件单帧自动关闭背景音乐） |
| 聆听模式 - 背景音乐 | T1 `E6/E7/E8/E9 09` | `E8 09 00 [room]`=开，`E8 09 01 [room]`=关（`room: 00=房间, 01=客厅, 02=咖啡馆`；开启时固件单帧自动关闭电影） |
| DSEE Extreme 与生效状态 | T1 `E6/E8 01` & `E2/E5 01` | 开关：`E8 01 [00=关, 01=自动]`；实时状态：`E5 01 [00=生效, 01=受空间音效暂停]` |
| 智能免摘 | T1 `F6/F8 0C` & `FA/FC 0C` | 开关：`F8 0C [00=开, 01=关] 01`（反向极性）；时长：`FC 0C 00 [00=5s, 01=15s, 02=30s, 03=不关]` |
| 触控面板 | T1 `F6/F8 0F` | `F8 0F [00=关, 01=开]` |
| 自动关机（佩戴检测型） | T1 `26/27/28/29 05` | 能力表含 `0x25` 无 `0x24`：`28 05 10 00`=摘下关机，`28 05 11 00`=不自动关机 |
| 多点连接与设备列表 | T2 `36/37/38/39 00..03` | `36 00` 读已连设备，`36/38 01` 多点总开关，`38 02/03` 切换/断开指定 MAC |

---

## 如何修改为其他型号的适配版本

本项目按**传输层 / 协议层 / 仓库状态层 / 系统能力层 / UI 层**清晰解耦，你可以按以下清单快速移植到其他 Sony 耳机或其他品牌 Android 手机。

### 一、适配其他 Sony 耳机型号（如 WH-1000XM5 / XM4 / WF-1000XM5 / LinkBuds）

1. **放宽蓝牙设备筛选与伴生过滤规则**
   - 修改 [`app/src/main/java/com/xm6/app/protocol/RfcommClient.kt`](app/src/main/java/com/xm6/app/protocol/RfcommClient.kt) 中的 `findBondedXm6Devices()`，将名称匹配关键字调整为目标型号（如 `WH-1000XM5`、`WF-1000XM5`、`LinkBuds`、`ULT WEAR`）。
   - 修改 [`app/src/main/java/com/xm6/app/system/Xm6CompanionService.kt`](app/src/main/java/com/xm6/app/system/Xm6CompanionService.kt) 中的 `BluetoothDeviceFilter` 正则 `".*(WH-1000XM6|WH-1000X|Sony).*"`，加入目标耳机名称模式。
   - 若适配较老的 V1 协议耳机（如早期 MDR 型号），`RfcommConnector` 已内置 `SONY_V1_UUID` 回退，但需确认指令集差异。

2. **第一步先用内置「协议调试台」导出目标耳机能力表**
   - 连接目标耳机后，进入 **音质 → 协议调试台 → 复制能力表**，查看耳机返回的 `Table 1 (0x0C)` 和 `Table 2 (0x0E)` Capability ID，以及初始握手日志中每一条查询帧的回包。

3. **适配降噪 / 环境声子类型（`NCASM`）**
   - 文件：[`app/src/main/java/com/xm6/app/protocol/Xm6ProtocolV2.kt`](app/src/main/java/com/xm6/app/protocol/Xm6ProtocolV2.kt)
   - XM6 使用 `NCASM_TYPE_XM6 = 0x19`（9 字节，含自动环境声与灵敏度）。
   - **WH-1000XM5 / WF-1000XM5** 通常使用 `0x17`（7 字节，无末尾 2 字节自动环境声）；更早型号（如 XM4）可能使用 `0x02` 或 `0x15`。
   - 修改 `buildGetNoiseControl()` 与 `buildSetNoiseControl()` 中的子类型与载荷长度，并核对 `decodeFrame()` 中 `CMD_NCASM_RET_PARAM` / `CMD_NCASM_NTFY_PARAM` 的字节偏移。

4. **适配均衡器段数（10 段 → 5 段 + Clear Bass）**
   - 文件：[`Xm6ProtocolV2.kt`](app/src/main/java/com/xm6/app/protocol/Xm6ProtocolV2.kt)、[`HeadphoneState.kt`](app/src/main/java/com/xm6/app/data/HeadphoneState.kt)、[`EqScreen.kt`](app/src/main/java/com/xm6/app/ui/eq/EqScreen.kt)、[`HomeScreen.kt`](app/src/main/java/com/xm6/app/ui/home/HomeScreen.kt)
   - WH-1000XM6 为 **10 段 EQ**（`31Hz–16kHz`，范围 `-6..+6`，偏移 `+6`）。
   - **WH-1000XM4 / WH-1000XM5 / WF-1000XM5** 为 **Clear Bass + 5 段 EQ**（共 6 个数值，范围 `-10..+10`，偏移 `+10`）。
   - 移植时需同步修改：
     - `Xm6ProtocolV2.buildSetEqualizer10Band()` 与 `CMD_EQ_RET_PARAM` 中的段数与偏移值（`+6` → `+10`）；
     - `Xm6Defaults.EQ_BAND_LABELS`、`BUILTIN_EQ_PRESETS` 与 `HeadphoneState.eqBands10` 长度；
     - `EqScreen.kt` 推子范围与 `HomeMiniEqCurve` 曲线绘制点数。

5. **适配自动关机、智能免摘、聆听模式与电池查询**
   - **自动关机**：[`HeadphoneState.isAutoPowerOffWearingOnly`](app/src/main/java/com/xm6/app/data/HeadphoneState.kt) 已根据能力表 `0x24`（定时型：`15m/30m/1h/3h/关`）与 `0x25`（佩戴检测型：`摘下/关`）自动区分；如目标机型同时支持两种或有 `5m` 档位，可在 `Xm6ProtocolV2.kt` 与 `DeviceSettingsScreen.kt` 放开对应选项。
   - **智能免摘**：XM6 使用 `SYS_TYPE_SPEAK_TO_CHAT_XM6 = 0x0C`（`F8 0C [00=开, 01=关] 01`）；XM4 / 部分老型号可能使用 `0x02` 或不同字节顺序，可通过调试台发 `F6 02` / `F6 0C` 对比官方 App 切换时的回包确认。
   - **聆听模式（背景音乐 / 电影）**：非 XM6 机型如不支持电影模式（`E8 04`）或背景音乐（`E8 09`），可在 `HomeScreen.kt` 按能力表隐藏 `ListeningModeSlidingSelector`。
   - **TWS 真无线耳机（如 WF-1000XM5）电量**：头戴式使用 `22 00`（单电池），TWS 耳机通常需要左耳/右耳/充电盒多电量查询子类型（如 `22 01` / `22 02`），需在 `Xm6ProtocolV2.kt` 与 `HeadphoneState.kt` 扩展左右耳及耳机盒电量字段。
   - **Handshake 型号判断**：检查 [`HeadphoneRepository.kt`](app/src/main/java/com/xm6/app/data/HeadphoneRepository.kt) 中 `runHandshakeAndSync()` 里的 `isKnownXm6` 与初始查询帧列表，去掉目标耳机不支持的查询帧以避免无效等待。

---

### 二、适配其他品牌 / 型号 Android 手机（Pixel / 三星 / 一加 / OPPO / vivo 等）

1. **Shizuku 反射调用 `IBluetoothA2dp` 兼容性**
   - 文件：[`app/src/main/java/com/xm6/app/ldac/LdacShizukuManager.kt`](app/src/main/java/com/xm6/app/ldac/LdacShizukuManager.kt)
   - 本项目已通过 `HiddenApiBypass` 兼容 AOSP Android 12–16 的 `getService` / `mService` / `mProfileConnector` 提取逻辑，以及 `setCodecConfigPreference` 的 2/3/4 参重载（含 `AttributionSource` 与 `SynchronousResultReceiver`）。
   - 若在某厂商深度定制 ROM（如 OneUI / ColorOS / OriginOS / HarmonyOS）上出现锁码率失败，请抓取 `adb logcat -s XM6Proto:D`：
     - 查看 `IBluetoothA2dp.setCodecConfigPreference overloads` 输出的实际方法签名；
     - 在 `invokeSetCodecConfigPreference()` 与 `invokeGetCodecStatus()` 中按实际参数表补齐分支即可。
2. **后台保活与伴生唤醒策略**
   - 不同 ROM 对 `CompanionDeviceService` 与 `GlanceAppWidget` 的后台限制各异，请按目标系统的电池优化/自启动策略进行白名单配置，并在 [`Xm6CompanionService.kt`](app/src/main/java/com/xm6/app/system/Xm6CompanionService.kt) 按需微调 A2DP 稳定等待时间（当前为 `delay(1500L)`）。
3. **小屏或折叠屏 UI 适配**
   - 首页与设置页基于 Jetpack Compose 自适应布局，如在极窄屏或折叠屏外屏使用，可在 [`HomeScreen.kt`](app/src/main/java/com/xm6/app/ui/home/HomeScreen.kt) 微调三列快捷磁贴（`QuickActionTile`）的字号与内边距。

---

## 本地构建与测试

```bash
# 运行单元测试（协议编解码、ARQ 帧解析、时间主题切换等）
./gradlew testDebugUnitTest

# 构建 Debug APK
./gradlew assembleDebug
```

输出安装包位于：`app/build/outputs/apk/debug/app-debug.apk`

---

## 目录结构

```text
app/src/main/java/com/xm6/app/
├── MainActivity.kt                  # 入口、权限申请、边缘沉浸栏与主题定时切换
├── Xm6Application.kt                # 全局单例（Repository / DataStore / Shizuku）
├── data/
│   ├── HeadphoneState.kt            # 耳机状态模型、默认 EQ 预设与能力判断
│   ├── HeadphoneRepository.kt       # RFCOMM 会话管理、状态同步与防抖控制
│   └── UserPreferencesStore.kt      # DataStore 持久化（绑定设备、LDAC、EQ 预设、主题）
├── protocol/
│   ├── RfcommClient.kt              # Classic Bluetooth RFCOMM 连接与 UUID 回退
│   ├── MdrFrameCodec.kt             # 0x3E/0x3C 帧封装、0x3D 转义与校验和
│   ├── ArqSession.kt                # 序列号 ACK 重传会话管理
│   └── Xm6ProtocolV2.kt             # WH-1000XM6 Table 1 & Table 2 编解码器
├── ldac/
│   └── LdacShizukuManager.kt        # ShizukuBinderWrapper 免 Root 锁定 LDAC 码率
├── system/
│   ├── Xm6CompanionService.kt       # Android 12+ 伴生设备唤醒服务
│   ├── Xm6GlanceWidget.kt           # 2×2 Jetpack Glance 桌面小组件
│   ├── AncTileService.kt            # 系统下拉快捷开关磁贴
│   └── ThemeScheduleReceiver.kt     # 07:00/19:00 主题边界闹钟广播
└── ui/
    ├── Xm6ViewModel.kt              # UI 状态与防抖调度
    ├── home/HomeScreen.kt           # 首页（状态卡、降噪、聆听胶囊、快捷行、EQ 预设）
    ├── eq/EqScreen.kt               # 10 段均衡器与预设管理
    ├── ldac/LdacAndDebugScreen.kt   # 音质/LDAC 锁定与协议调试台
    ├── device/DeviceSettingsScreen.kt # 多点连接、自动关机、外观主题与伴生绑定
    ├── components/ExpressiveComponents.kt # Material 3 Expressive 公共动效组件
    └── theme/                       # 动态配色与随时间主题解析器
```

---

## 免责声明

本项目为个人独立开发的第三方开源工具，与 Sony Corporation 及 Xiaomi Corporation 无任何官方隶属关系。所有商标归其各自所有者所有。
