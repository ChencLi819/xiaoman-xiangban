# docs

## 当前文档

| 文件 | 内容 |
| --- | --- |
| [`technical.md`](technical.md) | 架构、关键实现取舍、依赖与版本约定 |
| [`sync.md`](sync.md) | WebDAV 端到端同步协议：配对、传输、加密、合并规则、触发时机 |
| [`privacy.md`](privacy.md) | 隐私设计：数据位置、权限、加密边界、备份与已知问题 |
| [`troubleshooting.md`](troubleshooting.md) | 故障排查：同步自查清单、错误提示对照、数据恢复、构建问题 |
| [`../CHANGELOG.md`](../CHANGELOG.md) | 版本变更 |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | 环境搭建、开发流程、提交与 PR 约定 |
| [`../SECURITY.md`](../SECURITY.md) | 漏洞上报通道与本项目的安全边界 |
| [`../tools/README.md`](../tools/README.md) | 辅助脚本（种子生成、同步调试、UI 探针、截图采集） |

## 目录

```
docs/
├── prototype/   # 设计原型（HTML 交互稿）——同时是 tools/gen_seed.js 的种子数据来源
└── screenshots/ # README「界面」一节用的运行时截图
```

原型是 Android 实现的起点，界面命名（此刻 / 说好的 / 点滴 / 往后 / 我们俩 / 雨过 / 说开了 / 抽一张 / 分寸 / 周期）与双色身份系统都源自它。
