# docs

## 当前文档

| 文件 | 内容 |
| --- | --- |
| [`technical.md`](technical.md) | 架构、关键实现取舍、依赖与版本约定 |
| [`sync.md`](sync.md) | WebDAV 端到端同步协议：配对、传输、加密、合并规则、触发时机 |
| [`privacy.md`](privacy.md) | 隐私设计：数据位置、权限、加密边界、备份与已知问题 |
| [`troubleshooting.md`](troubleshooting.md) | 故障排查：同步自查清单、错误提示对照、数据恢复、构建问题 |
| [`migration.md`](migration.md) | debug 版 ↔ 正式版的数据保留方案：覆盖安装、降级边界、完整迁移包 |
| [`signing-keys.md`](signing-keys.md) | 签名密钥清单：两把钥匙的指纹、切换开关、换机时怎么带 |
| [`widgets-integration.md`](widgets-integration.md) | 桌面小部件接入说明：8 张卡的清单、数据来源、刷新时机与广播回调 |
| [`widget-design-review.md`](widget-design-review.md) | 卡片排版/尺寸验收：三档格子离线渲染结果与逐条修复记录 |
| [`widget-issues-audit.md`](widget-issues-audit.md) | 卡片问题审计：逐卡问题清单与判定依据 |
| [`widget-visual-audit.md`](widget-visual-audit.md) | 卡片视觉规范审计：内距 / 字号阶梯 / 配色铁律的落实核对 |
| [`../CHANGELOG.md`](../CHANGELOG.md) | 版本变更 |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | 环境搭建、开发流程、提交与 PR 约定 |
| [`../SECURITY.md`](../SECURITY.md) | 漏洞上报通道与本项目的安全边界 |
| [`../tools/README.md`](../tools/README.md) | 辅助脚本（种子生成、同步调试、UI 探针、截图采集、卡片渲染验收） |

## 目录

```
docs/
├── prototype/   # 设计原型（HTML 交互稿）——同时是 tools/gen_seed.js 的种子数据来源
│                #   mood.html / widgets.html 是 v1.6 心晴与桌面卡片的原型
└── screenshots/ # README「界面」一节用的运行时截图
                 #   v1.6/ 是 v1.6 新功能的开发验证留档
```

原型是 Android 实现的起点，界面命名（此刻 / 说好的 / 点滴 / 往后 / 我们俩 / 雨过 / 说开了 / 抽一张 / 分寸 / 周期 / 云笺 / 心晴）与双色身份系统都源自它。
