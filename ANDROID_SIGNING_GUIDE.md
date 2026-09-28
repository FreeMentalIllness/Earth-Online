# Android 正式签名配置指引（v1.0.1 及以后）

> 用途：把当前 **debug 签名 APK** 升级为可正式分发的 **release 签名 APK**。
> 读者：项目所有者（你本人）。本文档**只给模板，不代填任何密码**。

---

## 0. 为什么需要

当前 `build.gradle.kts` 的 release 签名逻辑是：仅当 `local.properties` 配齐四个签名变量时才创建
`signingConfigs.release`；否则 `assembleRelease` 自动回落到 **debug 密钥**。

因此现在产出的 `app-release.apk` 实际上是 **debug 签名**（可侧载 / USB 调试安装，但**不能上架应用商店**，
且安装设备需允许「未知来源」）。正式对外分发必须有你自己的 release 签名。

---

## 1. 红线（务必遵守）

- **AI 不会、也不能生成或猜测你的 keystore 密码 / 别名密码**——这些必须你自己设定并妥善保管。
- **绝不允许把 `.jks` / `local.properties` 提交到 Git 仓库**。
  仓库 `.gitignore` 已包含：`*.jks`、`*.keystore`、`local.properties`。
- keystore 文件一旦丢失，**已上架应用将无法继续更新**，请离线备份。

---

## 2. 第一步：生成 release keystore（由你在本机执行）

打开 PowerShell 或 CMD，执行（**密码请交互输入，不要写在命令里明文留存**）：

```powershell
keytool -genkeypair `
  -v `
  -keystore EarthOnline-release.jks `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -alias earthonline `
  -dname "CN=YourName, OU=EarthOnline, O=Personal, L=City, ST=Province, C=CN"
```

执行后会交互提示输入**密钥库口令（storepass）**与**密钥口令（keypass）**，请记住它们。

参数说明：
| 参数 | 含义 |
|------|------|
| `-keystore EarthOnline-release.jks` | 生成的密钥库文件名（**不要放进仓库**） |
| `-alias earthonline` | 密钥别名，后续配置记为 `EO_KEY_ALIAS` |
| `-validity 10000` | 有效期天数（约 27 年，建议大值） |
| `-keyalg RSA -keysize 2048` | 算法与密钥长度（标准配置） |
| `-dname` | 证书主体信息，按需填写，不影响签名有效性 |
| `-storepass` / `-keypass` | 上面交互输入的两种密码 |

生成后，把 `EarthOnline-release.jks` 移动到**项目根目录之外的安全路径**（如 `D:\Secrets\`），避免误提交。

---

## 3. 第二步：填写 local.properties（由你填写，勿提交）

在 `EarthOnline-Android/local.properties` **末尾追加**以下四个变量
（路径用正斜杠 `/` 或双反斜杠 `\\`，变量名必须与下方完全一致）：

```properties
EO_STORE_FILE=D:/Secrets/EarthOnline-release.jks
EO_STORE_PASSWORD=你的密钥库密码
EO_KEY_ALIAS=earthonline
EO_KEY_PASSWORD=你的密钥密码
```

| 变量 | 对应 keytool 参数 | 说明 |
|------|------------------|------|
| `EO_STORE_FILE` | `-keystore` 的路径 | `build.gradle.kts` 用 `file(storeFilePath)` 读取 |
| `EO_STORE_PASSWORD` | `-storepass` | 密钥库口令 |
| `EO_KEY_ALIAS` | `-alias` | 密钥别名 |
| `EO_KEY_PASSWORD` | `-keypass` | 密钥口令 |

> `build.gradle.kts` 已有解析逻辑：四个值齐全 → 创建 `signingConfigs.release`；
> 任一为空 → 回落 debug（即当前行为）。

---

## 4. 第三步：交给我升级打包

你确认 keystore 就位、`local.properties` 填好后，只需回复一句（例如：
「keystore 已就位，请用 release 签名打包」）。

我会将发布流程从 `assembleDebug` 升级为 `assembleRelease`，产出**正式签名 APK**，
并上传到 Android 仓库对应版本的 GitHub Release（不会新建 Release、不打 tag、不强制推送）。

---

## 5. 安全检查清单（发布前自查）

- [ ] `.jks` 文件不在仓库目录内，且已被 `.gitignore` 忽略
- [ ] `local.properties` 未被 `git add`（已被忽略）
- [ ] 密码仅存于本机，未出现在任何聊天 / 文档 / 截图
- [ ] keystore 已离线备份一份
