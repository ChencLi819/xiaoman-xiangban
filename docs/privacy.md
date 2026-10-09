# 隐私设计

这个项目记录的是两个人最私密的内容，所以隐私不是附加说明，而是架构约束。改代码时请连同本文件一起更新。

## 数据在哪里

| 项目 | 情况 |
| --- | --- |
| 账号体系 | 无 |
| 自有服务器 / 后台 | 无 |
| 第三方 SDK、统计与埋点 | 无 |
| 崩溃上报 | 无（崩溃只落在本地 logcat） |
| 默认存储 | 设备本地 Room 数据库 + 应用私有目录里的图片文件 |
| 广告 / 推送 | 无 |

唯一的远程数据动作是你**主动配置**的 WebDAV 同步（见 [`sync.md`](sync.md)）。

## 权限

Manifest 声明三项：

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 每日提醒（Android 13+ 启动时才请求） |
| `RECEIVE_BOOT_COMPLETED` | **当前无 BOOT_COMPLETED receiver 使用它**（Manifest 里两个 receiver 都是桌面小部件；重启后的提醒由 WorkManager 自行恢复）。这是一处待清理的多余声明，见下「已知问题」 |
| `INTERNET` | 仅 sync flavor 的 WebDAV 同步会用到 |

> `INTERNET` 对两个 flavor 都会打进 APK，因为权限声明在共享的 `AndroidManifest.xml` 里；local flavor 不会调用任何网络代码（`BuildConfig.SYNC_ENABLED` 是编译期 `false`，release 构建下这段死代码被 R8 剔除）。如果你要把它做成 flavor 专属权限，需要引入 flavor 级 Manifest，并在改动后同步更新本表格与根 README。

不申请、也不需要：联系人、存储、位置、相机、麦克风、设备标识。相册与情书图片经**系统照片选择器**（`ActivityResultContracts.PickVisualMedia`）由用户主动挑取，压缩后存进应用私有目录，因此不需要 `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`。

## 端到端加密

同步内容在离开设备前就已加密，网盘服务商只看到密文：

- 密钥 = `SHA-256("xiaoman-sync|" + 配对码)`，AES-256-GCM，每次写入随机 12 字节 IV；
- 配对码由两台设备各自填写，**不经过任何中间方传输**；
- 因此能解密的人只有持有同一配对码的设备，以及能读到网盘文件的人（但后者只有密文）。

诚实说明其边界：配对码只有 6 位，强度依赖「同步目录只有你俩能访问」，属于家用级，不是对抗专业攻击者的设计。要改变这一点请同时更新 [`sync.md`](sync.md)。

## 「仅我看」

标记为 `privateOnly` 的备忘不参与同步：导出快照时被剔除，合并对端数据时再过滤一次（防御旧版本曾上传的残留），连删除墓碑都不上传。它只存在于本机的数据库里。

## 备份与清除

- 导出/导入是本地 JSON 文件，由用户自己决定去向；照片二进制不进备份。
- 「我们俩 → 清空全部数据」物理删除所有表与图片文件，不保留墓碑。
- **`allowBackup="true"`**：系统「备份到 Google One / adb backup」会把应用私有数据带出去。卸载后本机的私有目录数据会随包删除，但云端备份副本不受 App 控制。若你要求更强，可改为 `allowBackup="false"` 并用 App 内的 JSON 导出代替——这是一处待决策项，见下「已知问题」。

## 网络侧信道与传输安全

同步时你的网盘服务商会知道：这台设备访问过它、请求了 `host.json` / `guest.json` 的读写、传输量大小与频率。不知道：内容（加密）、配对码（只在本地参与派生密钥）。

`res/xml/network_security_config.xml` **显式放开了明文 HTTP**（`cleartextTrafficPermitted="true"`），因为家用 NAS / 自建 WebDAV 常见纯 HTTP 内网地址，默认策略会在「创建同步目录」一步直接抛 `CLEARTEXT communication not permitted`。这是一个有意的取舍：传输安全由应用层的配对码派生 AES-GCM 保证，而不是靠 TLS。

带来的实际风险：

- 走 HTTP 时，网盘**账号与应用密码以 Basic Auth 明文随请求发送**，同网段可被截获（内容本身仍是密文，但凭据泄露 = 网盘账户泄露）。
- 因此**公网网盘（坚果云 / Nextcloud）请一律填 HTTPS 地址**；`tools/mini_dav.py` 这类明文桩服务只用于本机测试。
- 另外提供「忽略自签名证书」开关，给内网自签 HTTPS 用，默认关闭。

## 已知问题（隐私相关）

以下是当前实现里已被记录、但尚未处理的点，改进时请连同本文件一起更新，并在 PR 里说明：

1. `RECEIVE_BOOT_COMPLETED` 声明了但没有对应的 BOOT receiver —— 多余权限。
2. `allowBackup="true"` 与「隐私优先」的定位存在张力。
3. 6 位配对码派生的密钥强度有限，依赖同步目录的访问控制；对抗性威胁模型不在当前设计范围内。
4. `trustAll`（忽略自签名证书）会同时关掉主机名校验，开启后存在中间人风险；UI 上已限定为「家用 NAS / 私有网络」语境。

发现问题请按 [`../SECURITY.md`](../SECURITY.md) 私下上报，不要公开提 Issue。

## 安全边界 / 已知问题

见 [`../SECURITY.md`](../SECURITY.md)。发现隐私层面的问题请勿公开提 Issue，走 SECURITY.md 的通道。
