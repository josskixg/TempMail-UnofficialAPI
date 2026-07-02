<p align="center">
  <img src="./banner.svg" alt="TempMail Unofficial API Wrapper" width="800">
</p>

# 📬 TempMail Unofficial API — 多语言封装库

<p align="center">
  <strong>v1.1.0</strong> — 发布于 2026-07-02 &nbsp;|&nbsp; <a href="./RELEASE_NOTES.md">发布说明</a> &nbsp;|&nbsp; <a href="./CHANGELOG.md">更新日志</a> &nbsp;|&nbsp; <a href="./docs/plan/PLAN_v1.1.0.md">v1.1.0 计划</a>
</p>

[🇬🇧 English](./README.md) | [🇮🇩 Bahasa Indonesia](./README.id.md) | [🇨🇳 中文](./README.cn.md)

---

收集了用 **7 种编程语言**编写的各种临时邮箱服务的**非官方封装库**。一个仓库，一个目标：轻松地以编程方式创建和管理一次性邮箱。

## 🎯 支持的语言

| 语言 | 文件夹 | 包管理器 | 状态 |
|------|--------|----------|------|
| Go | [`/go`](./go) | `go get` | ✅ 完成 |
| Python | [`/python`](./python) | `pip` | ✅ 完成 |
| Java | [`/java`](./java) | `Maven` / `Gradle` | ✅ 完成 |
| PHP | [`/php`](./php) | `Composer` | ✅ 完成 |
| JavaScript | [`/javascript`](./javascript) | `npm` / `yarn` | ✅ 完成 |
| Rust | [`/rust`](./rust) | `cargo` | ✅ 完成 |
| C# | [`/csharp`](./csharp) | `NuGet` | ✅ 完成 |

## 🌐 支持的临时邮箱服务

本封装库目前支持 **16 个临时邮箱服务**：

| # | 服务 | 网站 | API 类型 | 认证方式 | 难度 | 状态 |
|---|------|------|----------|----------|------|------|
| 1 | Mail.tm | [mail.tm](https://mail.tm) | REST+JSON | Bearer Token | ✅ 简单 | 活跃 |
| 2 | GuerrillaMail | [guerrillamail.com](https://www.guerrillamail.com) | REST | Session Token | ⚡ 中等 | 活跃 |
| 3 | YOPmail | [yopmail.com](https://yopmail.com) | HTML 抓取 | 无 | ⚡ 中等 | 活跃 |
| 4 | Dropmail | [dropmail.me](https://dropmail.me) | GraphQL | Token (自动) | ✅ 简单 | 活跃 |
| 5 | 1secemail | [1secemail.com](https://www.1secemail.com) | REST | 无 | ✅ 简单 | 活跃 |
| 6 | Ncaori Mail+ | [nca.my.id](https://www.nca.my.id) | REST+JSON | 无 | ✅ 简单 | 活跃 |
| 7 | Zoromail | [zoromail.com](https://zoromail.com) | REST+JSON | 无 | ✅ 简单 | 活跃 |
| 8 | Tempmail.lol | [tempmail.lol](https://tempmail.lol) | REST+JSON | Token | ✅ 简单 | 活跃 |
| 9 | Tempmailc | [tempmailc.com](https://tempmailc.com) | REST+JSON | 无 | ✅ 简单 | 活跃 |
| 10 | Temp-mail.io | [temp-mail.io](https://temp-mail.io) | REST+JSON | Bearer Token | ⚡ 中等 | 活跃 |
| 11 | Tempmail.plus | [tempmail.plus](https://tempmail.plus) | REST+JSON | 无 | ✅ 简单 | 活跃 |
| 12 | Emailfake | [emailfake.com](https://emailfake.com) | HTML 抓取 | Cookie | ⚡ 中等 | 活跃 |
| 13 | Generator.email | [generator.email](https://generator.email) | HTML 抓取 | Cookie | ⚡ 中等 | 活跃 |
| 14 | Mailnesia | [mailnesia.com](https://mailnesia.com) | HTML 抓取 | 无 | ⚡ 中等 | 活跃 |
| 15 | 10minutemail | [10minutemail.net](https://10minutemail.net) | HTML 抓取 | Cookie Session | ⚡ 中等 | 活跃 (通过 10minutemail.net) |
| 16 | Email-temp | [email-temp.com](https://email-temp.com) | HTML 抓取 | Cookie | ⚡ 中等 | 活跃 |

## 📁 项目结构

```
TempMail-UnofficialAPI/
├── go/                   # Go 封装
├── python/               # Python 封装
├── java/                 # Java 封装
├── php/                  # PHP 封装
├── javascript/           # Node.js / JavaScript 封装
├── rust/                 # Rust 封装
├── csharp/               # C# / .NET 封装
├── docs/
│   └── plan/
│       └── PLAN_v1.1.0.md  # v1.1.0 实现计划
├── README.md             # English (default)
├── README.id.md          # Bahasa Indonesia
├── README.cn.md          # 中文
├── RELEASE_NOTES.md      # 各版本发布说明
├── CHANGELOG.md          # 完整更新记录
├── LICENSE               # Apache 2.0
└── NOTICE                # 归属声明与免责声明
```

## 🚀 快速开始

每种语言都有自己的 README。点击上方文件夹查看安装详情和使用示例。

### 通用示例（伪代码）

```
// 1. 生成临时邮箱
email = tempmail.generate()
// → "random123@mail.tm"

// 2. 查看收件箱
messages = tempmail.get_inbox(email)

// 3. 读取邮件
if messages.length > 0:
    content = tempmail.read_message(messages[0].id)

// 4. 删除（可选，也支持自动过期）
tempmail.delete(email)
```

## ⚡ API 接口（所有语言）

所有封装实现一致的接口：

| 方法 | 描述 | 返回值 |
|------|------|--------|
| `generate_email()` | 生成临时邮箱地址 | `string` (邮箱地址) |
| `get_inbox(email)` | 获取邮件列表 | `[]Message` |
| `read_message(id)` | 读取邮件内容 | `MessageDetail` |
| `delete_email(email)` | 删除邮箱（清理） | `bool` |
| `wait_for_email(email, timeout)` | 轮询等待新邮件 | `Message` 或 `null` |

## 📦 数据模型

### Message (邮件)
- `id` — 唯一消息标识符
- `from` — 发件人地址
- `subject` — 邮件主题
- `date` — 接收时间戳

### MessageDetail (邮件详情，继承 Message)
- `body_text` — 纯文本邮件正文
- `body_html` — HTML 邮件正文（如有）
- `attachments` — 附件元数据列表

## 🛡️ 免责声明

> **⚠️ 重要提示**
>
> - 本项目为**非官方**项目 — 与任何临时邮箱服务无关。
> - API 端点可能随时更改，恕不另行通知。
> - 仅用于**测试、开发或个人自动化**。
> - 请勿用于垃圾邮件、欺诈或任何非法活动。
> - 部分服务有速率限制 — 请负责任地使用。

## 🤝 贡献

想添加语言？修复 Bug？欢迎：

1. Fork 本仓库：[github.com/josskixg/TempMail-UnofficialAPI](https://github.com/josskixg/TempMail-UnofficialAPI/fork)
2. 创建分支：`feat/add-kotlin-wrapper`
3. 提交并推送
4. 发起 Pull Request

### 贡献指南

- 遵循现有接口结构
- 在各语言 README 中添加使用示例
- 切勿硬编码 API 密钥（使用环境变量）
- 提交 PR 前请先测试

## 🗺️ 路线图

v1.1.0 已发布 16 个服务、7 种语言。未来版本计划：

- **增强爬虫稳定性与 YOPmail** — 改善反爬虫绕过机制与无头浏览器支持
- **WebSocket 支持** — 为 Dropmail.me 和 Mail.tm 提供实时收件箱订阅
- **更多语言** — 新增 Kotlin、Swift 和 Ruby 封装库
- **CLI 工具** — 统一的命令行工具，可直接从终端管理所有服务商的临时邮箱

欢迎贡献 — 详见 [CONTRIBUTING.md](./CONTRIBUTING.md)，或开 [PR](https://github.com/josskixg/TempMail-UnofficialAPI/pulls) / [Issue](https://github.com/josskixg/TempMail-UnofficialAPI/issues)。

## 📄 许可证

Apache License 2.0 — 详见 [LICENSE](./LICENSE) 和 [NOTICE](./NOTICE)。

---

<p align="center">
  <strong>🌟 如果本项目对你有帮助，请点个 Star！</strong><br>
  由社区用 🫠 打造，为社区服务。
</p>
