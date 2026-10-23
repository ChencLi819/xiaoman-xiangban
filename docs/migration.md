# debug 版与正式版的数据兼容

小满的 `debug` 包和 `release` 包**共用同一个 `applicationId` 与同一把签名密钥**（默认用
`android/app/xiaoman-signing-legacy.keystore` 签名，密钥随项目走、任何机器构建同签名），所以两个版本在同一台手机上可以互相覆盖安装，升级安装时
**不会清除数据**，Room 会按递增的数据库版本逐级迁移（当前 v6，支持 1→2→…→6 全链路）。

但「能覆盖安装」不等于「任何方向都安全」。下面是完整的保留方案与边界。

---

## 一、推荐做法：直接覆盖安装（保留数据）

```bash
# 当前装的是 debug 版（版本号更高或相同）
./gradlew assembleLocalRelease
adb install -r app/build/outputs/apk/local/release/xiaoman-local-v1.5.0-release.apk
```

判据（三条同时成立，数据就原地保留）：

| 条件 | 说明 |
| --- | --- |
| 包名相同 | `local` 是 `com.xiaoman.memo`，`sync` 是 `com.xiaoman.memo.sync`；**跨 flavor 不是同一个 App**，数据不互通 |
| 签名相同 | release 与 debug 用同一把钥匙（`xiaoman.signing` 选定的那把，默认 legacy），见 [signing-keys.md](signing-keys.md) |
| 数据库版本不下降 | 新装包的 DB 版本 ≥ 手机上已有的 DB 版本 |

`-r` 表示覆盖安装；不要加 `-d` 之外的「先卸载」步骤——`adb uninstall` / 系统卸载都会删除
`/data/data/<包名>/` 下的数据库与图片。

> 反过来也成立：正式版换回 debug 版同样可以覆盖安装，只要 debug 的 DB 版本不低于正式版。

---

## 二、唯一不能做的是「降级」

数据库版本只升不降。装过 **v1.5.0 debug（DB v6）** 之后，再装 **v1.4.8 正式版（DB v5）**：

- Room 会拒绝打开数据库（不会静默清库）；
- 小满在 `XiaomanDb.get()` 里把异常翻译成中文提示：
  「本机的数据版本比当前 App 新……请装回同版本或更新版本的正式包，不要卸载」；
- 正确做法：把正式版也升到 v1.5.0（或更高）再覆盖安装。

`Database.kt` 里刻意没有加 `fallbackToDestructiveMigration*`——宁可崩溃并留下可查的日志，
也不允许任何情况下自动清库重建。

---

## 三、换签名 / 换手机：用「完整迁移包」

签名不同（例如换成你自己的正式 keystore）时系统会拒绝覆盖安装，只能卸载重装。
卸载会带走本机数据，所以要先把数据搬出来：

> **v1.5.0 → v1.5.1 不需要走这一遍**：v1.5.1 当初误判旧密钥丢失而换签，事后旧钥匙已找回
> （cert `794aca5a…`），并恢复为默认签名（`android/gradle.properties` 的 `xiaoman.signing=legacy`），
> v1.5.0 可直接覆盖安装。只有一种情况才要卸载重装：手机已经装过 **v1.5.1 事故包**
> （cert `552b01a2…`，`xiaoman.signing=xiaoman` 签的那个），此后要换回 legacy 就得走一遍迁移。
> 两把钥匙的指纹与切换方法见 [signing-keys.md](signing-keys.md)。

1. 旧包：`我们俩 → 数据导出` 一栏 → **「完整迁移包导出（含情书/相册图片）」**
   - 产物：`Android/data/<包名>/files/Documents/xiaoman_bundle_<时间戳>.zip`
   - 内含 `backup.json`（全部结构化数据）、`photos.json`（相册索引）、`files/photos/*`、`files/letters/*`
2. 新包安装完成后：同一栏 → **「从迁移包恢复（zip）」**，选择刚才的 zip
   - 先落图片，再按**文件名**把 JSON 里旧设备/旧包名的绝对路径改写为本机路径，最后追加导入；
   - 追加式：不清空新包已有数据；带 `uuid` 的行按 `updatedAt` 后写胜出，重复导入是幂等的。

其他可选路径：

- **JSON 备份**（不含图片）：`数据导出（JSON）` / `数据导入（选择备份 JSON）`，适合纯文本记录；
- **WebDAV 同步**（仅 sync flavor）：配好同一个同步文件夹后点「立即同步」，两台设备的数据会按
  `uuid` + `updatedAt` 合并；换机时在新机装上 sync 包、填同一套网盘与配对码即可。

---

## 四、版本演进时我们保证什么

- 每个数据库版本都有**手写 Migration**，且新增列一律带 `DEFAULT`，不做破坏性重建；
- 同步协议只在必要时升级（当前快照 v2），新增字段（如 `memos.tags`）对旧端是**忽略即兼容**：
  旧端只读 `tag`（主标签），新端优先读 `tags`；
- 内容扩充只做「追加」：互动与翻篇条目以**文本**为键（`pool_overrides` / `act_marks`），
  原有文本与顺序不变，因此历史收藏、已玩标记、自定义覆盖在升级后仍然有效。

---

## 五、一句话版本

> 同包名、同签名、版本不降 → 直接覆盖安装，数据都在；
> 换签名或换机 → 先导出「完整迁移包」，装好再恢复；
> 任何时候都别先卸载。
