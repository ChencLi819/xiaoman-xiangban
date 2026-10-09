# 参与贡献

欢迎 Issue 和 PR。开始前请先读[技术说明](docs/technical.md)与[同步协议](docs/sync.md)。

这是一个给两个人记录私密内容的应用，**数据不能丢** 是最高优先级——多数评审问题最终都会落到「某次改动会不会让一端的记录消失或凭空多出一条」。

## 环境

1. JDK 17
2. Android SDK（compileSdk 35，需要 platform 35 与 build-tools）
3. 创建 `android/local.properties`（不入库）：

   ```properties
   sdk.dir=<你的本机 Android SDK 路径>
   ```

4. 打开 `android/` 目录（**不是仓库根目录**，Gradle 根在 `android/`）

```bash
cd android
./gradlew assembleLocalDebug assembleSyncDebug   # 两个 flavor 都要能构建通过
./gradlew test                                   # 单元测试
```

`./gradlew` 必须是 LF 换行（`.gitattributes` 已固定）。Windows 上请用 Git Bash 执行；`.bat` / `.ps1` 脚本必须保持纯 ASCII 之外的内容用 UTF-8，命令行传参避免内联中文正则。

## 开发流程

1. 先开 Issue 说明要改什么。若 Issue 模板里有**安全与数据丢失**相关项，请如实回答。
2. 从 `main` 切分支，命名 `feat/<简述>` 或 `fix/<简述>`。
3. 实现 + 补测试。纯逻辑放 `domain/`（不依赖 Android API，可直接 JVM 单测）。
4. `./gradlew test` 与两个 flavor 的 `assembleDebug` 全绿。
5. 提 PR，描述里链接 Issue。

CI 会在 PR 上跑两个 flavor 的构建与单元测试，但**不会**跑真机验收——涉及同步合并的改动请按下文自测。

## 硬性约定

### 数据库变更

- **只走递增版本 + 手写 Migration，禁止破坏性重建**（不加 `fallbackToDestructiveMigration`；升降级失败宁可崩溃，也不静默清库）。
- 新增列必须带 `defaultValue`，且与实体的 `@ColumnInfo` 声明一致，否则 Room schema 校验不通过。
- 给**同步表**加字段要改**五处**，少一处就会出现「本机能写、对方看不到」：
  1. 实体字段（`data/Entities.kt`）
  2. 数据库版本 + 手写 Migration（`data/Database.kt`）
  3. 备份导出（`data/Backup.kt`）
  4. 备份导入（同上）
  5. 同步快照导出与合并读取（`data/SyncEngine.kt`）
- 新字段要能被旧版本 App 忽略（`optXxx` 带默认值），否则旧版解密后直接崩。
- 改合并语义（`domain/Merge.kt`）必须补 `MergeTest` 用例。

### 换行符与编码

- `gradlew`、`*.sh` 必须 LF；`*.bat` / `*.cmd` 必须 CRLF。
- 源文件 UTF-8；界面文案中文优先。

### 内容与命名

- **周期板块只做生理学叙述**，不引入任何生育视角内容（易孕区间 / 安全期 / 备孕 / 避孕）。
- 板块名沿用既有叫法（此刻 / 说好的 / 点滴 / 往后 / 我们俩 / 雨过 / 说开了 / 抽一张 / 分寸 / 周期），改动会带来认知成本，请先开 Issue 讨论。

### 隐私

- 新增权限、依赖、网络请求前，请先在 Issue 里说明；这三类改动会重新触发安全评审。
- 不要引入任何第三方 SDK、统计、埋点、崩溃上报。
- 改了权限或网络行为，请同步更新 [docs/privacy.md](docs/privacy.md) 的表格。

### 种子数据

`android/app/src/main/java/com/xiaoman/memo/data/SeedData.kt` 是生成文件，不要手改。改内容请修改 [`docs/prototype/情侣备忘录-原型.html`](docs/prototype/情侣备忘录-原型.html) 后运行 `node tools/gen_seed.js`。

## 自测同步改动

同步类改动最难在 CI 覆盖，推荐两条路径：

- **纯逻辑**：`domain/Merge.kt` 的决策可直接 JVM 单测，优先补这里。
- **端到端**：起本地桩 WebDAV（[`tools/mini_dav.py`](tools/README.md#mini_davpy)），用两个模拟器（`XM_ADB_SERIAL` 区分）当两台设备，配合 [`tools/ui.py`](tools/README.md#uipy) 做无视觉验收；用 [`tools/sync_snapshot.js`](tools/README.md#sync_snapshotjs) 构造「对端有墓碑 / 有冲突」这类难以手工触发的快照。

至少覆盖：两端各有对方没有的记录、同一记录两端都改过、一端删除、`privateOnly` 记录不出现、平手冲突时 guest 侧被覆盖并记 `conflicts`。

## 提交信息

Conventional Commits，`type` 用英文、描述用中文：

```
feat: 雨过新增「详细内容」可选项
fix: 同步时 privateOnly 备忘被误上传
docs: 更新 sync.md 的合并规则表
refactor: 把周期预测从 UI 抽到 domain/CycleMath
test: 补 Merge 平手冲突用例
chore: 升级 compose BOM 到 2024.10.01
```

一次提交只做一件事。`fix` 的正文里写清**症状 + 根因**，因为数据类问题的根因往往和现象隔了好几层。

## 版本与发布

- `versionCode` 单调递增，任何情况下不回退。
- `versionName` 语义化：MAJOR = 同步协议大版本（可能丢失旧记录）/ MINOR = 新功能 / PATCH = 修复与微调。
- **协议兼容性必须在 `docs/sync.md` 与 `CHANGELOG.md` 标注**，注明「需双方升级」。
- 破坏性数据库迁移必须同时给出迁移路径与「升级前本地导出备份 JSON」的提示文案。
- 仓库不含任何发布密钥。开源构建走 debug 自签，正式版签名由维护者本地完成。
- 版本号只在 `app/build.gradle.kts` 一处定义（`versionName` / `versionCode`），发版时不要同步改 README 文案。
- 打 tag：`v<versionName>`。
