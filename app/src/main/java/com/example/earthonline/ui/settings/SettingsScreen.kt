package com.example.earthonline.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earthonline.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.reminder.ReminderScheduler
import com.example.earthonline.util.IMAGE_TYPES_HINT
import com.example.earthonline.util.ImageStore
import com.example.earthonline.util.openUrlInBrowser
import com.example.earthonline.util.rememberImagePicker
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlin.text.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsRoute(moreActions: MoreMenuActions, vm: SettingsViewModel = hiltViewModel()) =
    SettingsScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, moreActions: MoreMenuActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val theme by vm.theme.collectAsStateWithLifecycle(initialValue = "system")
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle(initialValue = "")
    val wallpaperAlpha by vm.wallpaperAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
    val notify by vm.notify.collectAsStateWithLifecycle(initialValue = false)
    val aiConfigJson by vm.aiConfigJson.collectAsStateWithLifecycle(initialValue = "")
    val webdavConfigJson by vm.webdavConfigJson.collectAsStateWithLifecycle(initialValue = "")
    val autoSync by vm.autoSync.collectAsStateWithLifecycle(initialValue = true)
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle(initialValue = "")
    val fontScale by vm.fontScale.collectAsStateWithLifecycle(initialValue = "std")
    val achSound by vm.achSound.collectAsStateWithLifecycle(initialValue = true)

    // v1.2.1：隐私政策 + 应用内检查更新
    var showPrivacy by remember { mutableStateOf(false) }
    // v1.2.2：更新说明
    var showChangelog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf<UpdateCheck?>(null) }

    // v1.0.1：检查更新 → 下载并安装（系统下载器 + 未知来源权限）。
    //   有 APK 直链：DownloadManager 下载到应用私有下载目录，下载完成广播触发安装意图；
    //   Android 8.0+ 需「安装未知应用」权限，未授予时先跳系统设置页授权，回来再下。
    //   无直链（只有 Release 页）：退化为打开浏览器，沿用 UrlOpener 的容错。
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val lastDownloadId = remember { mutableStateOf(-1L) }
    var pendingApkUrl by remember { mutableStateOf<String?>(null) }

    fun startApkDownload(url: String) {
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("地球Online 更新")
            setDescription("正在下载安装包…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "earth-online-update.apk")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        lastDownloadId.value = downloadManager.enqueue(req)
        scope.launch { snackbar.showSnackbar("开始下载更新，完成后自动提示安装") }
    }

    fun installDownloadedApk(id: Long) {
        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val uriStr = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (status == DownloadManager.STATUS_SUCCESSFUL && !uriStr.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uriStr), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { scope.launch { snackbar.showSnackbar("无法启动安装，请手动前往 Release 页面") } }
            } else {
                scope.launch { snackbar.showSnackbar("下载失败，请手动前往 Release 页面") }
            }
        }
    }

    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        val url = pendingApkUrl
        pendingApkUrl = null
        if (canInstall && url != null) startApkDownload(url)
        else scope.launch { snackbar.showSnackbar("未授权「安装未知应用」，已改为打开 Release 页面") }
    }

    fun beginInstallFlow(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingApkUrl = apkUrl
            installPermLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } else {
            startApkDownload(apkUrl)
        }
    }

    // 下载完成的系统广播 → 触发安装（仅匹配我们这次 enqueue 的 id）
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == lastDownloadId.value) installDownloadedApk(id)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    var showAiConfig by remember { mutableStateOf(false) }
    var showDavConfig by remember { mutableStateOf(false) }
    var showSponsor by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    // v1.2.1：本地自动备份快照列表（进入页面读一次，操作后再刷新）
    var autoSnapshots by remember {
        mutableStateOf(emptyList<com.example.earthonline.data.backup.AutoBackupManager.Snapshot>())
    }
    LaunchedEffect(Unit) { autoSnapshots = vm.autoSnapshots() }

    var davTesting by remember { mutableStateOf(false) }
    var davSyncing by remember { mutableStateOf(false) }
    var davPulling by remember { mutableStateOf(false) }

    /* v1.0.0：壁纸导入 —— **原图完整保存**。
       与头像共用同一套「选图 + 原样复制」管道（util/ImageStore）：
         · 存原图：4K 也按字节原封不动复制进 filesDir/wallpaper，画质一点不损失。
           复制过程不解码，所以没有全尺寸位图的内存尖峰（这才是 OOM 的真凶，不是文件大）；
         · 显示按需采样：MainScaffold 里已用 Coil 的 .size(屏幕像素) 加载，
           Coil 会按目标尺寸解码，壁纸再大也只分配一屏所需的像素。
       顺序上先把新文件落盘、成功后再清旧文件 —— 万一复制失败，旧壁纸还在，不会「两头空」。 */
    val pickWallpaper = rememberImagePicker(onPicked = { uri ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val dir = ImageStore.wallpaperDir(context)
                ImageStore.importOriginal(context, uri, dir, "wallpaper").map { file ->
                    // 只保留最新一张：换壁纸时清掉旧的，避免私有目录越用越大
                    ImageStore.clearDirExcept(dir, file)
                    file
                }
            }
            result.onSuccess { file ->
                vm.setWallpaper(file.absolutePath)
                snackbar.showSnackbar("壁纸已设置（原图 ${ImageStore.prettySize(file.length())}）")
            }.onFailure { e ->
                snackbar.showSnackbar("导入失败：${e.message ?: "无法读取这张图片"}")
            }
        }
    })
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = vm.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                }
                snackbar.showSnackbar("已导出备份")
            } catch (e: Exception) {
                snackbar.showSnackbar("导出失败：${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                vm.importJson(text)
                snackbar.showSnackbar("已导入备份")
            } catch (e: Exception) {
                snackbar.showSnackbar("导入失败：${e.message}")
            }
        }
    }

    // 到期提醒通知权限（Android 13+ 需运行时申请）
    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.setNotify(true)
            ReminderScheduler.schedule(context)
        } else {
            scope.launch { snackbar.showSnackbar("未授予通知权限，无法发送到期提醒") }
        }
    }

    /** 开启/关闭到期提醒：开启时申请权限并调度，关闭时取消调度 */
    fun onNotifyToggle(want: Boolean) {
        if (want) {
            if (Build.VERSION.SDK_INT >= 33) {
                when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                    PackageManager.PERMISSION_GRANTED -> {
                        vm.setNotify(true)
                        ReminderScheduler.schedule(context)
                    }
                    else -> postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                vm.setNotify(true)
                ReminderScheduler.schedule(context)
            }
        } else {
            vm.setNotify(false)
            ReminderScheduler.cancel(context)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                // 已经在设置页了，右上角不再放「进设置」的齿轮（否则点了没反应，像 Bug）
                actions = { SettingsIconButton(actions = moreActions, visible = false) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            /* ==================== ① 外观 ==================== */
            item { GroupHeader("🎨", "外观", "主题 · 壁纸 · 字号") }
            item {
                Section(title = "外观") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("主题", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChip("跟随系统", "system", theme) { vm.setTheme("system") }
                            ThemeChip("浅色", "light", theme) { vm.setTheme("light") }
                            ThemeChip("深色", "dark", theme) { vm.setTheme("dark") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("壁纸", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    if (wallpaper.isBlank()) "未设置" else "已设置（点击更换）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "支持 $IMAGE_TYPES_HINT，原图完整保存不压缩；显示时按屏幕尺寸自动采样。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { pickWallpaper() }) { Text("选择图片") }
                        }
                        if (wallpaper.isNotBlank()) {
                            // v1.0.0：导入出问题时的明确退路（原来的「清除」藏在一行里，很多人没看见）
                            OutlinedButton(
                                onClick = {
                                    vm.setWallpaper("")
                                    scope.launch { snackbar.showSnackbar("已重置为默认背景") }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("重置为默认") }
                        }
                        if (wallpaper.isNotBlank()) {
                            // 拖动过程用本地草稿驱动滑块（跟手），落盘交给 VM 防抖
                            var alphaDraft by remember(wallpaper) { mutableFloatStateOf(wallpaperAlpha) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("壁纸不透明度", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${(alphaDraft * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Slider(
                                value = alphaDraft,
                                onValueChange = {
                                    alphaDraft = it
                                    vm.setWallpaperAlpha(it)
                                },
                                valueRange = 0.05f..0.9f
                            )
                        }
                        // v1.2.0：字号三档（对应 Web 端设置页「外观」组的字号）
                        Spacer(Modifier.height(4.dp))
                        Text("字号", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChip("标准", "std", fontScale) { vm.setFontScale("std") }
                            ThemeChip("大", "lg", fontScale) { vm.setFontScale("lg") }
                            ThemeChip("特大", "xl", fontScale) { vm.setFontScale("xl") }
                        }
                        Text(
                            "只放大内容文字，最大档约放大 20%。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            /* ==================== ② 通用 ==================== */
            item { GroupHeader("🧩", "通用", "提醒 · 音效 · AI") }
            item {
                Section(title = "到期提醒") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("到期提醒通知", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "待办到期当天推送提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = notify, onCheckedChange = { onNotifyToggle(it) })
                    }
                }
            }
            item {
                Section(title = "成就解锁提示") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("解锁音效", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "解锁成就时右下角弹卡片并播放一声轻响",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = achSound, onCheckedChange = { vm.setAchSound(it) })
                        }
                    }
                }
            }
            item {
                Section(title = "AI 对话") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cfg = vm.parseAiConfig(aiConfigJson)
                        Text(
                            "当前：${if (cfg.baseUrl.isBlank()) "未配置" else cfg.baseUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { showAiConfig = true }, modifier = Modifier.fillMaxWidth()) { Text("配置 AI 接口") }
                    }
                }
            }
            /* ==================== ③ 数据与隐私 ==================== */
            item { GroupHeader("🔒", "数据与隐私", "备份 · 同步") }
            item {
                Section(title = "数据备份") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { exportLauncher.launch("earth-online-backup.json") },
                                modifier = Modifier.weight(1f)
                            ) { Text("导出备份") }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) { Text("导入备份") }
                        }
                        // v1.2.0：双端语义统一为「按主键合并」
                        Text(
                            "导入采用按主键合并：同一条数据以备份为准，本地新增的部分保留（与网页端一致）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                AutoBackupSection(
                    snapshots = autoSnapshots,
                    onBackupNow = {
                        scope.launch {
                            val ok = vm.autoBackupNow()
                            autoSnapshots = vm.autoSnapshots()
                            snackbar.showSnackbar(if (ok) "已保存一份本地快照" else "备份失败")
                        }
                    },
                    onRestore = { s ->
                        scope.launch {
                            val ok = vm.restoreAutoSnapshot(s)
                            snackbar.showSnackbar(if (ok) "已从该快照恢复（按主键合并）" else "恢复失败")
                        }
                    }
                )
            }
            item {
                Section(title = "隐私政策") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "本应用不收集任何数据，所有内容只存在这台设备上。完整说明可随时查阅。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showPrivacy = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("查看隐私政策") }
                    }
                }
            }
            item {
                Section(title = "WebDAV 备份同步") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cfg = vm.parseWebdavConfig(webdavConfigJson)
                        Text(
                            "当前：${if (cfg.url.isBlank()) "未配置" else cfg.url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { showDavConfig = true }, modifier = Modifier.fillMaxWidth()) { Text("配置 WebDAV") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    davTesting = true
                                    scope.launch {
                                        val r = vm.testDav(cfg)
                                        davTesting = false
                                        snackbar.showSnackbar(r)
                                    }
                                },
                                enabled = !davTesting,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (davTesting) CircularProgressIndicator(Modifier.size(18.dp)) else Text("测试连接")
                            }
                            OutlinedButton(
                                onClick = {
                                    davSyncing = true
                                    scope.launch {
                                        val r = vm.syncToDav(cfg)
                                        davSyncing = false
                                        snackbar.showSnackbar(r)
                                    }
                                },
                                enabled = !davSyncing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (davSyncing) CircularProgressIndicator(Modifier.size(18.dp)) else Text("推送到云端")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                davPulling = true
                                scope.launch {
                                    val r = vm.syncFromDav(cfg)
                                    davPulling = false
                                    snackbar.showSnackbar(r)
                                }
                            },
                            enabled = !davPulling,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (davPulling) CircularProgressIndicator(Modifier.size(18.dp)) else Text("从云端拉取")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "自动同步（启动拉取 / 切后台推送）",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = autoSync, onCheckedChange = { vm.setAutoSync(it) })
                        }
                        Text(
                            if (lastSyncAt.isBlank()) "尚未同步过"
                            else "上次同步：${lastSyncAt.take(19).replace('T', ' ')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            /* ==================== ④ 关于 ==================== */
            item { GroupHeader("ℹ️", "关于", "版本 · 团队 · 赞助") }
            item {
                AboutSection(
                    onSponsor = { showSponsor = true },
                    onDetail = { showAbout = true },
                    onChangelog = { showChangelog = true },
                    onCheckUpdate = {
                        if (checkingUpdate) return@AboutSection
                        checkingUpdate = true
                        scope.launch {
                            vm.checkUpdate()
                                .onSuccess { c ->
                                    if (c.hasUpdate) updateAvailable = c
                                    else updateMsg = "已是最新版本 v${AppInfo.version}"
                                }
                                .onFailure { e ->
                                    updateMsg = "检查失败：${e.message ?: "无法连接更新服务器"}"
                                }
                            checkingUpdate = false
                        }
                    },
                    checkingUpdate = checkingUpdate
                )
            }
        }
    }

    // v1.2.0：赞助二维码（微信收款码，长按保存后在微信里「扫一扫 · 相册」识别）
    if (showSponsor) {
        AnimatedAlertDialog(
            onDismissRequest = { showSponsor = false },
            title = { Text("❤️ 赞助开发者") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "地球Online 完全免费、无广告、无服务器成本。如果它陪你走过了一段路，" +
                            "欢迎扫码请开发者喝一杯 ☕（金额随意）。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Image(
                        painter = painterResource(R.drawable.sponsor_qr),
                        contentDescription = "赞助收款码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "长按图片保存到相册，再用微信「扫一扫 · 相册」识别。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSponsor = false }) { Text("知道啦") } }
        )
    }

    // v1.2.0：完整「关于」页（开发 / 赞助 / 项目信息）
    if (showAbout) {
        AnimatedAlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 ${AppInfo.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        )
                        Column {
                            Text(
                                "${AppInfo.name} v${AppInfo.version}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(AppInfo.slogan, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "构建于 ${AppInfo.buildDate} · ${AppInfo.license}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("开发人员", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInfo.developers.forEach { PersonChip(it) }
                    }
                    Text("赞助者", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInfo.sponsors.forEach { PersonChip(it) }
                    }
                    Text("项目信息", style = MaterialTheme.typography.labelMedium)
                    AppInfo.facts.forEach { (k, v) ->
                        Text(
                            "$k：$v",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        AppInfo.offlineNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false; showSponsor = true }) { Text("赞助我") }
            },
            dismissButton = { TextButton(onClick = { showAbout = false }) { Text("关闭") } }
        )
    }

    // v1.2.1：隐私政策
    if (showPrivacy) {
        AnimatedAlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私政策") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "最后更新：${AppInfo.buildDate} · v${AppInfo.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppInfo.privacyPolicy.forEach { (title, body) ->
                        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("我已阅读") } }
        )
    }

    // v1.2.1：检查更新结果（无新版 / 失败共用一条提示；有新版单独弹卡片）
    updateMsg?.let { msg ->
        AnimatedAlertDialog(
            onDismissRequest = { updateMsg = null },
            title = { Text("检查更新") },
            text = { Text(msg, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { updateMsg = null }) { Text("好的") } }
        )
    }

    updateAvailable?.let { up ->
        val isApk = up.url.endsWith(".apk", ignoreCase = true)
        AnimatedAlertDialog(
            onDismissRequest = { updateAvailable = null },
            title = { Text("发现新版本 v${up.latest}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前版本 v${AppInfo.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (up.notes.isNotBlank()) {
                        Text(
                            up.notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 180.dp)
                        )
                    }
                    Text(
                        if (isApk) "下载将通过系统下载器进行，完成后自动提示安装；Android 8.0 及以上首次安装需授予「安装未知应用」权限。覆盖安装不会丢失本地数据。"
                        else "未找到 APK 直链，将打开 Release 页面手动下载。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateAvailable = null
                    if (isApk) {
                        beginInstallFlow(up.url)
                    } else if (!openUrlInBrowser(context, up.url, noBrowserMessage = "未找到可用的浏览器，请手动访问 Release 页面")) {
                        scope.launch { snackbar.showSnackbar("未找到可用的浏览器") }
                    }
                }) { Text(if (isApk) "下载并安装" else "前往更新") }
            },
            dismissButton = { TextButton(onClick = { updateAvailable = null }) { Text("稍后") } }
        )
    }

    // v1.2.2：更新说明
    if (showChangelog) {
        AnimatedAlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("📝 更新说明") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppInfo.changelog.forEach { (ver, date, items) ->
                        Text(
                            "v$ver · $date",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        items.forEach { item ->
                            Text(
                                "· $item",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("知道了") } }
        )
    }

    if (showAiConfig) {
        AiConfigDialog(
            initial = vm.parseAiConfig(aiConfigJson),
            onDismiss = { showAiConfig = false }
        ) { vm.saveAiConfig(it); showAiConfig = false }
    }
    if (showDavConfig) {
        WebDavConfigDialog(
            initial = vm.parseWebdavConfig(webdavConfigJson),
            onDismiss = { showDavConfig = false }
        ) { vm.saveWebDavConfig(it); showDavConfig = false }
    }
}

/**
 * v1.2.0：设置页分组标题。
 * 之前所有卡片平铺，长列表滚起来分不清哪块是哪块；现在四组各有标题 + 说明，
 * 组与组之间用一条渐变分割线（GroupDivider）断开。
 */
@Composable
private fun GroupHeader(emoji: String, title: String, hint: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        GroupDivider()
    }
}

/** 组间分割线（两端渐隐，比一条硬线更轻） */
@Composable
private fun GroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.colorScheme.outlineVariant,
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * v1.2.1：本地自动备份。
 * 写操作后 4 秒自动落一份 JSON（同一分钟内只留一份），最多保留 3 份 ——
 * 误删、误改之后不用连电脑，在这里直接挑一份回滚。
 */
@Composable
private fun AutoBackupSection(
    snapshots: List<com.example.earthonline.data.backup.AutoBackupManager.Snapshot>,
    onBackupNow: () -> Unit,
    onRestore: (com.example.earthonline.data.backup.AutoBackupManager.Snapshot) -> Unit
) {
    val fmt = remember { java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
    Section(title = "本地自动备份") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "每次改动后自动在本机留一份快照，只保留最近 3 份（卸载应用会一并删除，长期留档请用「导出备份」）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onBackupNow, modifier = Modifier.fillMaxWidth()) { Text("立即备份一份") }
            if (snapshots.isEmpty()) {
                Text(
                    "还没有自动快照",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                snapshots.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(java.util.Date(s.timeMillis)), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${s.bytes / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRestore(s) }) { Text("恢复") }
                    }
                }
            }
        }
    }
}

/** 关于组：应用标识 + 版本 + 开发/赞助 + 赞助入口 */
@Composable
private fun AboutSection(
    onSponsor: () -> Unit,
    onDetail: () -> Unit,
    onChangelog: () -> Unit,
    onCheckUpdate: () -> Unit,
    checkingUpdate: Boolean
) {
    Section(title = "关于") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                )
                Column(Modifier.weight(1f)) {
                    Text("${AppInfo.name} v${AppInfo.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(AppInfo.slogan, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "构建于 ${AppInfo.buildDate} · ${AppInfo.license} 开源许可",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("开发人员", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppInfo.developers.forEach { PersonChip(it) }
            }
            Text("赞助者", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppInfo.sponsors.forEach { PersonChip(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSponsor, modifier = Modifier.weight(1f)) { Text("❤️ 赞助我") }
                OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f)) { Text("项目信息") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChangelog, modifier = Modifier.weight(1f)) {
                    Text("📝 更新说明")
                }
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !checkingUpdate,
                    modifier = Modifier.weight(1f)
                ) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("检查更新")
                }
            }
            Text(
                AppInfo.latestChangeSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                AppInfo.offlineNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PersonChip(p: Person) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(p.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            // 赞助名单不带修饰词（只列名字），所以 tag 可能为空 —— 空则不渲染，避免留出多余空隙
            if (p.tag.isNotBlank()) {
                Text(
                    p.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ThemeChip(label: String, value: String, current: String, onClick: () -> Unit) {
    FilterChip(selected = current == value, onClick = onClick, label = { Text(label) })
}
