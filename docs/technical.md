# 技术说明

面向贡献者：架构、关键实现取舍、构建配置。功能介绍见根 [`README.md`](../README.md)，贡献流程见 [`CONTRIBUTING.md`](../CONTRIBUTING.md)。

工程本体在 [`../android/`](../android/)（Gradle 根目录）。

## 构建

```bash
cd android
./gradlew assembleLocalDebug   # 本地端：小满（com.xiaoman.memo）
./gradlew assembleSyncDebug    # 同步端：小满·相伴（com.xiaoman.memo.sync）
./gradlew test                 # 单元测试
```

要求 JDK 17、Android SDK（compileSdk 35）。首次构建前创建 `android/local.properties`（不入库）：

```properties
sdk.dir=<你的本机 Android SDK 路径>
```

产物命名：`android/app/build/outputs/apk/<flavor>/<buildType>/xiaoman-<flavor>-v<version>-<buildType>.apk`

## 模块分层

```
app/src/main/java/com/xiaoman/memo/
├── data/          # Room 实体 / DAO / Database、Migrations、SeedData（生成）、Backup、
│                  # SettingsStore（DataStore）、AppViewModel、SyncEngine、SyncTransport
├── domain/        # 纯函数业务逻辑：CycleMath、Merge、Quarrel、Tags、Pools
├── ui/
│   ├── theme/     # 双色身份色板（浅色 / 深色）
│   ├── components/# 卡片 / chips / 勾选框 / 翻卡动画等通用组件
│   ├── nav/       # 5 Tab + 全部子路由 + xiaoman:// 深链
│   └── screens/   # 各功能页
├── widget/        # 2×1 / 4×2 桌面小部件（RemoteViews）
└── remind/        # 通知 channel + 每日提醒 Worker
```

`domain/` 是可单测的纯 Kotlin 逻辑，不依赖 Android API；`data/` 与 `ui/` 分别承担持久化与呈现。新增算法优先落在 `domain/` 并补测试。

## 关键设计

### 单真相源，UI 只读本地

Room 是唯一真相源。写入只落本地库，`AppViewModel` 通过 DAO 的 Flow 驱动界面；同步是**后台的额外步骤**，不在读取路径上。因此没有网络也能完整使用。

### 跨设备身份用 `uuid`，不用自增主键

自增 id 是每设备本地的，不能作为跨设备合并键。v4 迁移给所有同步表补了 `uuid` 列（逐行 `randomblob` 回填），合并以 `uuid` 对齐同一行。

### 合并语义统一走 LWW，不按表特判

见 [`sync.md`](sync.md#合并规则)。所有同步表以 `uuid` 对齐、以 `updatedAt` 后写胜出、删除以墓碑传播；「标记翻篇」只是**一次带新 `updatedAt` 的普通修改**，靠 LWW 自然传播，没有额外的「已翻篇优先」规则——这样合并逻辑只有一套，可单测、可预测。

### 加密与配对码同源

密钥由配对码派生（`SHA-256("xiaoman-sync|" + 配对码)`），因此配对码既是身份也是密钥材料，网盘侧只见到密文。实现集中在 `SyncTransport`，AES-GCM、随机 IV 前置。细节见 [`sync.md`](sync.md)。

### 生成文件不要手改

`data/SeedData.kt`（内置 520 条翻篇方式与互动库）由 [`tools/gen_seed.js`](../tools/gen_seed.js) 从 [`docs/prototype/情侣备忘录-原型.html`](prototype/情侣备忘录-原型.html) 生成。要改种子内容应改原型后重新生成。

### 深色模式覆盖彩色区块

双色身份、周期四阶段、抽卡彩区在深色下需单独降饱和（不是简单反转），见 `ui/theme/` 与 `res/values-night/`。

### 双 flavor 的取舍

差异只在 `BuildConfig.SYNC_ENABLED`、应用名与 `applicationIdSuffix`。UI 以该标志决定是否显示同步入口，业务代码不分支。

## 依赖清单

全部依赖在 `app/build.gradle.kts`，无其它仓库来源（`settings.gradle.kts` 固定 `FAIL_ON_PROJECT_REPOS`，只允许 google() 与 mavenCentral()）：

| 用途 | 库 |
| --- | --- |
| UI | androidx.compose BOM 2024.10、material3、material-icons-extended |
| 导航 | androidx.navigation:navigation-compose |
| 数据 | androidx.room 2.6.1（KSP）、androidx.datastore:datastore-preferences |
| 后台 | androidx.work:work-runtime-ktx |
| 网络 | com.squareup.okhttp3:okhttp（仅 sync flavor 的 WebDAV 需要 MKCOL 等非标方法，Android 自带 `HttpURLConnection` 方法白名单会抛 `ProtocolException`） |
| 测试 | junit 4.13.2 |

## 版本与包结构约定

- `minSdk 26` / `targetSdk 35` / `compileSdk 35`，`jvmTarget 17`。
- `versionCode` 单调递增，**任何情况下不回退**；`versionName` 走语义化版本，变更记在 [`CHANGELOG.md`](../CHANGELOG.md)。
- `release` 用 debug 密钥签名（自用分发），仓库里不含任何发布密钥。
- 隐私说明与源码权限声明必须一致，改动见 [`privacy.md`](privacy.md)。

## 辅助脚本

调试用的截图采集、WebDAV 桩服务、无视觉 UI 探针等放在仓库根 [`tools/`](../tools/README.md)，不参与 Gradle 构建。
