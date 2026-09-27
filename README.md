# 地球Online · Android（Kotlin + Jetpack Compose）

将原有零依赖 HTML/CSS/JS 版「地球Online」重写为 **纯原生 Android 应用**（不依赖 WebView）。

> 阶段 1 已交付：工程骨架、主题、导航、首页、首次引导页、本地存储（Room + DataStore）。
> 任务 / 背包 / 成就 / 数据 / 地图 / 系统 / 设置 等页面目前为占位屏（阶段 2 / 3 落地）。

## 技术栈（固定）

| 类别 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository，ViewModel + StateFlow |
| 导航 | Navigation Compose |
| 本地存储 | DataStore（键值）/ Room（结构化） |
| 网络 | Retrofit + OkHttp + kotlinx.serialization（阶段 2 接入 AI 对话与 WebDAV 同步） |
| 图片 | Coil |
| 依赖注入 | Hilt |
| 异步 | Coroutines + Flow |
| 构建 | Gradle Kotlin DSL + `libs.versions.toml` |
| 地图 | 高德 Android SDK（阶段 3 接入；Manifest 已预留 key 占位） |
| 范围 | minSdk 24，compileSdk / targetSdk 35 |
| 打包 | APK + AAB |

## 目录结构

```
earth-online-android/
├── settings.gradle.kts                 # 插件仓库 + include(":app")
├── build.gradle.kts                    # 根：仅声明插件版本
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml              # 统一版本目录
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts                # 模块依赖 / 签名 / AAB
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml         # 权限 / 高德 meta-data / FileProvider
        ├── java/com/example/earthonline/
        │   ├── EarthOnlineApp.kt       # @HiltAndroidApp
        │   ├── MainActivity.kt         # Splash + setContent(Root)
        │   ├── data/
        │   │   ├── model/CustomField.kt
        │   │   ├── local/
        │   │   │   ├── entity/Entities.kt        # 8 张表实体
        │   │   │   ├── Converters.kt
        │   │   │   ├── dao/Daos.kt
        │   │   │   ├── AppDatabase.kt
        │   │   │   └── datastore/SettingsDataStore.kt
        │   │   └── repository/Repositories.kt
        │   ├── di/AppModules.kt        # Hilt 模块
        │   ├── ui/
        │   │   ├── theme/             # Color / Theme / Type（暖白 + 深色）
        │   │   ├── navigation/        # Screen / BottomNav / AppNavHost
        │   │   ├── Root/Root.kt       # 引导页 / 主框架分流 + 主题
        │   │   ├── MainScaffold.kt    # 底部导航 + 更多抽屉
        │   │   ├── components/PlaceholderScreen.kt
        │   │   ├── home/              # Home(VM+Screen) / MoreSheet
        │   │   ├── profile/           # Profile(VM+Screen)
        │   │   ├── map/               # Map(VM+Screen，高德足迹地图)
        │   │   └── onboarding/OnboardingScreen.kt
        │   └── util/DateUtils.kt      # uid / nowIso / ageFromBirthDate ...
        └── res/values, values-night, values-v31, values-night-v31, drawable, mipmap-*, xml
```

## 数据迁移映射（HTML → Android）

| HTML 侧 | Android 侧 |
|---|---|
| 单键 `earth_data`（整份 state JSON） | Room 按实体拆 8 表，归一化存储 |
| `earth_theme` | `SettingsDataStore` theme |
| `earth_wallpaper`+`wallpaper_blob` | `SettingsDataStore` 配置 + 沙盒图片文件 |
| `earth_notify` | `SettingsDataStore` notify |
| `earth_online_webdav_v1`（明文 localStorage） | `SettingsDataStore` webdavConfig（不再明文裸存） |
| `state.profile / birthDate` | `ProfileEntity`（单行 id=1） |
| `state.tasks / memos / items / achievements / collections / locations / activities` | 对应实体表 |

页面：home / profile / tasks / backpack / achievements / data / map / ai / settings + 首次引导页（Onboarding）。

## 在 Android Studio 打开与运行

1. **环境准备**
   - 安装 [Android Studio](https://developer.android.com/studio)（Hedgehog 2023.1.1+ / Iguana / Jellyfish 均可）。
   - JDK 17（Android Studio 自带；`build.gradle.kts` 已设 `sourceCompatibility = 17`）。

2. **打开工程**
   - `File → Open` → 选择 `earth-online-android/` 文件夹（含 `settings.gradle.kts` 的那一层）。
   - 首次打开会自动触发 Gradle Sync，下载 AGP / Kotlin / Compose BOM 等依赖（需联网）。

3. **关于 Gradle Wrapper**
   - 仓库未提交 `gradle/wrapper/gradle-wrapper.jar`（二进制）。两种处理方式任选其一：
     - **推荐**：直接用 Android Studio 打开，IDE 会用内置 Gradle 完成 Sync，无需 wrapper jar；
     - 命令行：本机已装 Gradle 8.9 时执行 `gradle wrapper` 生成 `gradle-wrapper.jar`，之后即可用 `./gradlew`。

4. **运行**
   - 连上 Android 设备（开启 USB 调试）或新建/启动一个 API 24+ 的模拟器。
   - 工具栏选择 `app` 配置，点击 ▶ Run（或 `Shift+F10`）。
   - 首次启动会进入**引导页**（填写角色名 / 性别 / 出生日期 / 区服 / 签名 / 自定义字段），完成后写库并进入主页。

## 签名与打包

### 方式一：Android Studio 图形界面（最省事）

1. `Build → Generate Signed Bundle / APK…`
2. 选择 **Android App Bundle（AAB）** 或 **APK**，Next。
3. 创建/选择 keystore：
   - `Create new…` → 填写 keystore 路径（建议 `app/keystore/earthonline.jks`）、密码、key alias、key 密码、有效期（≥25 年）、你的姓名/组织。
   - **务必备份 keystore 与密码**：丢失后无法更新上架应用。
4. 勾选 `release` build variant，Finish。
5. 产物：
   - AAB：`app/release/app-release.aab`（上传 Google Play 用）。
   - APK：`app/release/app-release.apk`（直接安装 / 分发用）。

### 方式二：Gradle 命令行（签名已内置，写 local.properties 即可）

`app/build.gradle.kts` 已内置签名逻辑：读取项目根目录 `local.properties` 的 `EO_STORE_FILE` 等字段；若该文件不存在或未配置，则 release 自动回退到默认签名（仍可正常打包，仅无法上架分发）。

**1. 生成 keystore（仅首次）**

```bash
keytool -genkeypair -v \
  -keystore earth-online-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias earthonline
# 按提示输入 keystore 密码、key 密码、姓名/组织等信息
# 生成的 earth-online-release.jks 请妥善备份，丢失后无法更新已上架应用
```

**2. 在项目根目录 `local.properties`（已 gitignore，勿提交）写入：**

```properties
EO_STORE_FILE=../earth-online-release.jks
EO_STORE_PASSWORD=你的store密码
EO_KEY_ALIAS=earthonline
EO_KEY_PASSWORD=你的key密码
```

**3. 打包**

```bash
# 打 AAB（上传应用市场用）
./gradlew :app:bundleRelease        # 产物 app/build/outputs/bundle/release/app-release.aab

# 打 APK（直接安装 / 分发用）
./gradlew :app:assembleRelease       # 产物 app/build/outputs/apk/release/app-release.apk
```

> 启用 R8 混淆（将 `build.gradle.kts` 中 `isMinifyEnabled` 改为 `true`）时，若运行期报 Hilt / Retrofit / kotlinx.serialization 相关崩溃，本仓库 `proguard-rules.pro` 已预置对应 keep 规则；若仍缺，按需补充。

## 后续阶段

- **阶段 2**：任务（树/状态/doneAt/进度）、背包+收藏（含 SAF 文件附件）、成就（自动引擎）、资料编辑+头像、数据看板（Compose 图表）+ 日历、设置（主题/壁纸/通知）、备份导入导出（JSON）、网络（AI 对话 + WebDAV 同步 Retrofit）。
- **阶段 3**：足迹地图（高德 Android SDK，替换 `AndroidManifest.xml` 中 `com.amap.api.v2.apikey`）、设置收尾、Compose 动画、自适应启动图标、签名与打包细化、本说明的命令行打包补全。

## 待你提供（阶段 3 前）

- **包名 / 应用名 / 签名 alias**（当前占位 `com.example.earthonline`）。
- **高德 Android Key**（与 Web JS Key 不同）。
