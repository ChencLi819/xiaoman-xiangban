# 签名密钥清单

两把钥匙并存，都在 `android/app/` 下，别名同为 `androiddebugkey`，口令见 `build.gradle.kts`。
密钥文件**不入库**（`.gitignore` 排除 `*.keystore`），只随项目文件夹走。

| 名称 | 文件 | 证书 SHA-256 | 证书 SHA-1 | 签过哪些包 |
| --- | --- | --- | --- | --- |
| **legacy**（默认） | `android/app/xiaoman-signing-legacy.keystore` | `79:4A:CA:5A:4A:50:1E:DF:30:E8:7B:53:66:AA:E5:86:3C:62:2F:B0:A8:D0:3F:73:D5:E6:10:B4:5F:94:DD:32` | `75:47:CD:FA:F2:7C:78:79:EE:7A:01:F3:58:49:AC:62:C6:8A:D0:79` | v1.4.8、v1.5.0 |
| xiaoman | `android/app/xiaoman-signing.keystore` | `55:2B:01:A2:9A:0C:6B:D0:28:B9:D8:97:8F:03:8A:FF:10:7A:13:4B:3F:17:84:2F:0F:83:6A:DA:19:2D:33:13` | `66:50:E2:BB:FF:9B:DB:BF:08:40:CD:05:71:A4:76:A8:8C:5B:48:CF` | v1.5.1（事故包） |

- 有效期均到 **2056-09-11**，算法 `SHA256withRSA`，签名方案 v1+v2+v3。
- 两把钥匙的 DN 都是 `C=US, O=Android, CN=Android Debug`，**只能靠指纹区分**，不要看 DN。

## 为什么要两把

2026-10-23 换机时旧钥匙随 `D:\AndroidDev` 环境目录脱离项目，被误判为「已丢失不可恢复」，
于是新建了 `xiaoman` 钥匙并让 v1.5.1 用它，结果手机上装着的 v1.5.0 无法覆盖安装。

旧钥匙后来在 `D:\AndroidDev\debug.keystore` 找到，指纹与原包一致，因此 v1.4.8 / v1.5.0
能继续无缝升级 —— **两台手机上现存的版本都是 legacy 签的，后续版本请继续用 legacy。**

## 怎么切换

`android/gradle.properties`：

```properties
xiaoman.signing=legacy   # 默认，能覆盖 v1.4.8 / v1.5.0
# xiaoman.signing=xiaoman # 仅当手机已装过 v1.5.1 事故包时
```

生效范围：`debug` 与 `release` 两个 buildType、`local` 与 `sync` 两个 flavor 全部跟随。
校验当前配置：

```bash
cd android && ./gradlew :app:signingReport | grep -E "Variant|Config|SHA-256"
```

## 怎么确认手机上是哪把钥匙

```bash
adb shell dumpsys package com.xiaoman.memo | grep -i signatures
```

输出里的 hex 与上表 SHA-1 去掉冒号后比对，例如 `7547cdfaf27c7879ee7a01f35849ac62c68ad079`
= legacy。`sync` flavor 把包名换成 `com.xiaoman.memo.sync`。

## 换机 / 重装系统时必须带走

`*.keystore` 在 `.gitignore` 里，**不会跟着 git 走**。换机器时手动拷贝
`android/app/` 下这两个 `.keystore` 文件，否则构建会用新生成的临时钥匙，签名再次对不上。

```bash
# 校验拷过来的文件是否还是那两把
keytool -list -v -keystore android/app/xiaoman-signing-legacy.keystore -storepass android | grep SHA256
```
