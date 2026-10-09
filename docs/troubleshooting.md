# 故障排查

## 同步问题先自查

报「同步后数据不对」之前，请先按顺序确认这几项 —— 绝大多数情况出在配置而不是合并逻辑。

### 1. 两端配置是否一致

| 项 | 必须一致？ | 说明 |
| --- | --- | --- |
| 服务器地址 | ✅ | 含末尾路径；Nextcloud 形如 `https://域名/remote.php/dav/files/用户名/` |
| 同步文件夹 | ✅ | 两边填同一个目录，如 `/xiaoman-123456` |
| 配对码 | ✅ | 6 位，不一致的症状是解密失败 |
| 账号 / 应用密码 | ❌ 可以不同 | 两台设备共用一个网盘账号也行，各用各的（互相共享目录）也行 |
| 角色 | ❌ 必须不同 | 一个是「发起方」(host)，另一个是「加入方」(guest)；两边都填 host 会互相看不到对方 |

### 2. 两端版本是否一致

**协议 v2 拒绝合并版本 < 2 的快照**。旧版本 App 没有 `uuid` 和墓碑，混用会导致同一条记录在两台设备上各存一份（看起来像「重复」而不是「丢失」）。请双方都升级到最新版本再同步。

### 3. 数据是不是「没同步过来」而不是「丢了」

- 标记为「仅我看」（`privateOnly`）的备忘**永远不同步**，这是设计而非 Bug。
- 删除会以墓碑形式传播，墓碑在**30 天后物理清理**，之后对端不可能再把它恢复回来。
- 平手冲突（两端 `updatedAt` 相同但内容不同）时 **host 胜出**，guest 侧被覆盖的修改会记进日志的 `conflicts` 计数。这是已知取舍，见 [`sync.md`](sync.md#合并规则)。

### 4. 看同步的实际返回

「我们俩 → 立即同步」的结果文案会指出失败在哪一步：

| 提示 | 原因与处理 |
| --- | --- |
| `同步失败（在「连接服务器并创建同步目录」这一步）` | 地址/端口/路径问题，或服务器不支持 WebDAV |
| `账号或应用密码不对（…不是登录密码）` | 坚果云必须在网页版「账户信息 → 安全选项 → 添加应用」生成**应用密码** |
| `无法解析服务器地址` | 设备没网，或地址拼写错误 |
| `证书校验失败：自签名证书（家用 NAS）请在配置里打开「忽略自签名证书」` | 内网自签 HTTPS，按提示打开开关 |
| `解密失败：两台设备的配对码不一致` | 核对配对码 |
| `网盘服务器开小差（HTTP 5xx）` | 服务端问题，稍后重试 |
| `已同步（对方还没有上传过数据）` | 对端从未成功上传，检查对端配置与角色 |

提示末尾会附「原始错误：类名: 消息」，报 Issue 时请带上这一行（但**先删掉可能出现的账号、地址信息**）。

### 5. 报 Issue 时请附上

两端版本号与 flavor、网盘类型（不要贴地址）、角色分配、失败步骤提示全文、`adb logcat -d | grep -iE "xiaoman|SQLite|FATAL"` 的输出。

## 数据丢了怎么找回

1. **先停止同步**，避免错误的对端快照反复覆盖本地库。
2. 检查「我们俩 → 数据导出」是否有历史 JSON 备份，可用「数据导入」回灌（追加式，周期按开始日期覆盖）。
3. 合并前 App 会落一份本地快照文件兜底；若刚发生过覆盖，请**不要继续写入**，直接抓取数据库文件：
   ```bash
   adb exec-out run-as com.xiaoman.memo cat databases/xiaoman.db > xiaoman-local.db
   ```
   sync flavor 的包名是 `com.xiaoman.memo.sync`，对应改成包名。**这个文件是全部私密内容的明文数据库，请勿上传到任何公开位置**，报 Issue 时只描述现象、由维护者指导如何安全提供。
4. 若两端都还在，另一台设备往往还留着未覆盖的数据 —— 先在那台上导出，再谈恢复顺序。

## 构建问题

| 现象 | 处理 |
| --- | --- |
| `SDK location not found` | 创建 `android/local.properties`，填 `sdk.dir=<你的 SDK 路径>`（该文件不入库） |
| `./gradlew: bad interpreter` 或 `Permission denied` | `gradlew` 被转成了 CRLF。仓库已用 `.gitattributes` 固定为 LF；若是本地改过请恢复：`git checkout -- android/gradlew` |
| Windows 上 `gradlew` 无法执行 | 用 Git Bash 跑 `./gradlew`，或用 `gradlew.bat` |
| Room schema 校验失败 | 新增列没带 `defaultValue`，或与实体 `@ColumnInfo` 声明不一致；见 [`../CONTRIBUTING.md`](../CONTRIBUTING.md#数据库变更) |
| 只有某个 flavor 出问题 | 两个 flavor 共用同一套业务代码，差异只在 `BuildConfig.SYNC_ENABLED`、应用名与 `applicationIdSuffix`；请分别贴出两个 flavor 的报错 |

## 界面与提醒

- **每日提醒没来**：Android 13+ 需要授予通知权限；WorkManager 任务可能被厂商 ROM 的省电策略杀掉，请在系统设置里给 App 关闭电池优化。
- **小部件不刷新**：数据变更即刷新是设计行为；若系统把 App 后台进程杀掉，需等下一次 `APPWIDGET_UPDATE` 周期或手动打开 App。
- **深色模式下某些彩色区块对比度差**：周期四阶段与抽卡彩区在深色下需要单独降饱和，见 `android/app/src/main/res/values-night/`。这类问题请附截图并说明具体区块。

## 定位问题范围

改动或报障时说明涉及哪些文件，能显著加快处理：

| 症状 | 通常相关 |
| --- | --- |
| 合并、覆盖、重复记录 | `domain/Merge.kt`、`data/SyncEngine.kt` |
| 上传失败、目录建不出来、凭据错误 | `data/SyncTransport.kt` |
| 升级后字段丢失、崩溃 | `data/Database.kt`（Migration） |
| 周期日期/阶段算错 | `domain/CycleMath.kt`（有单测 `CycleMathTest`） |
| 备份导入导出少字段 | `data/Backup.kt` |
| 图片存不下、越权可读 | `util/ImageStore.kt` |
| 导航与深链 | `ui/nav/Nav.kt`、`res/xml/shortcuts.xml` |
| 内置文案（520 条） | 由 `tools/gen_seed.js` 从原型生成，改原型而不是改 `SeedData.kt` |
