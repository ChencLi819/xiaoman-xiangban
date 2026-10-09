# 同步协议

适用于 sync flavor（小满·相伴）。local flavor 里 `BuildConfig.SYNC_ENABLED` 是编译期常量 `false`，所有同步入口（[`XiaomanApp`](../android/app/src/main/java/com/xiaoman/memo/XiaomanApp.kt)、[`SyncScheduler`](../android/app/src/main/java/com/xiaoman/memo/data/SyncEngine.kt)、UI）都以该标志为前置判断，因此本地版运行时不会发起任何网络请求（release 构建下 R8 会把这段死代码整块剔除）。

实现位置：[`android/app/src/main/java/com/xiaoman/memo/data/SyncTransport.kt`](../android/app/src/main/java/com/xiaoman/memo/data/SyncTransport.kt)（WebDAV + 加解密）、[`SyncEngine.kt`](../android/app/src/main/java/com/xiaoman/memo/data/SyncEngine.kt)（快照与合并）、[`../android/app/src/main/java/com/xiaoman/memo/domain/Merge.kt`](../android/app/src/main/java/com/xiaoman/memo/domain/Merge.kt)（纯函数合并决策）。

## 拓扑：两台设备 + 你自己的网盘

没有服务器、没有账号。任何支持 WebDAV 的存储都行（坚果云 / Nextcloud / 群晖等 NAS / 自建），它只充当**两个密文文件的存放处**，服务器看不到明文。

```
设备 A（host）  --PUT host.json-->   WebDAV 目录 /xiaoman-<配对码>   <--GET guest.json--  设备 B（guest）
```

## 配对

1. 一方「发起配对」，App 随机生成 **6 位配对码**。
2. 在该网盘里准备 `/xiaoman-<配对码>` 目录（可由发起方新建后共享，或双方直接共用同一个网盘账号，角色一个是发起方、一个是加入方）。
3. 两台设备分别填：服务器地址、账号、应用密码、同步文件夹路径、配对码、角色。

坚果云要用网页版「账户信息 → 安全选项 → 添加应用」生成的**应用密码**，不是登录密码；填错时 App 会把 HTTP 401 直接翻译成这句提示。Nextcloud 地址形如 `https://域名/remote.php/dav/files/用户名/`。

## 传输协议

- **一角色一文件**：host 只写 `host.json`，guest 只写 `guest.json`。写入互不冲突，因此不需要锁、不需要版本号协商、不需要 CRDT。
- **全量快照**：每次上传当前库的全部行，**包含墓碑行**（已软删的记录）。这样删除才能跨设备传播。
- **逐级 MKCOL**：多数 WebDAV 服务器不支持一次创建多级目录，因此按路径段逐段创建，`405/301/302` 视为已存在；`PUT` 遇 `409`（父目录缺失）自动补建后重试一次。
- 必须用 OkHttp：Android 自带 `HttpURLConnection` 的方法白名单不含 `MKCOL`，`setRequestMethod("MKCOL")` 会直接抛 `ProtocolException`。
- 「忽略自签名证书」开关只对家用 NAS 的这条配置生效，用于 `https://NAS` 这类自签场景。

## 加密

```
key      = SHA-256("xiaoman-sync|" + 配对码)          → AES-256
文件内容 = Base64(iv) + "." + Base64(ciphertext || tag)
iv       = 12 字节，每次写入用 SecureRandom 重新生成
mode     = AES/GCM/NoPadding，tag 128 bit
```

配对码既是身份也是密钥材料，所以配对码填错的症状是解密失败，App 会提示「两台设备的配对码不一致」。

命令行工具 [`tools/sync_snapshot.js`](../tools/sync_snapshot.js) 用同一套算法，可解密查看或伪造对端快照，用于测试。

## 合并规则

对端快照与本地库逐表比对，键与优先级如下：

| 情形 | 结果 |
| --- | --- |
| 对端有、本地无该 `uuid` | 插入 |
| 对端 `updatedAt` > 本地 | 以对端为准更新 |
| 对端 `updatedAt` < 本地 | 保留本地 |
| `updatedAt` 相同但内容不同（平手冲突） | **host 胜出**；guest 侧被覆盖的修改计入 `conflicts` 并写日志，不静默丢弃 |
| 对端是更新的墓碑行 | 本地跟着删除 |
| 对端是更新的正常行（本地是墓碑） | 记录复活（撤销误删） |

删除本身被当作「一种内容」参与 LWW，因此不存在「删除永远赢」的问题。

合并整体包在一个 Room 事务里，任何一步失败全部回滚；合并前还会落一份本地快照文件兜底。

> 「标记翻篇」没有专门的合并规则——它只是一次带新 `updatedAt` 的普通修改，靠 LWW 自然传播。保持一套合并语义是可测试性的前提。

## 「仅我看」条目

标记为 `privateOnly` 的备忘**完全不参与同步**：导出快照时被剔除，合并对端数据时也会再过滤一遍（防御旧版本 App 上传过的残留）。连删除标记都不上传。

## 触发时机

只有三个，没有后台轮询：

1. 打开 App；
2. 数据变更后 15 秒防抖（[`SyncScheduler`](../android/app/src/main/java/com/xiaoman/memo/data/SyncEngine.kt)，连续变更只触发一次）；
3. 手动点「立即同步」。

## 兼容与升级

快照带版本号。**版本 < 2 的快照拒绝合并**——v2 才引入 `uuid` 与墓碑，混用会导致同一记录在两台设备上各存一份。协议变更必须同时递增快照版本，并在 [`CHANGELOG.md`](../CHANGELOG.md) 标注「需双方升级」。

## 已知取舍

- **全量快照**而非增量：数据量是两人日常记录，代价可接受，换来的是无状态、可重放、无需差量协议。
- **平手时 host 固定胜**：不是对称方案。真实同时编辑概率极低，但会有一端被覆盖的可能，故记录 `conflicts` 而非静默。
- 配对码即密钥，长度只有 6 位：安全性依赖「网盘目录只有你俩能访问」这一前提，属于家用方案而非对抗性加密。改动这一点请同时更新 [`privacy.md`](privacy.md)。
