package com.example.earthonline.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.navigation.Screen
import kotlinx.coroutines.launch
import kotlin.text.Charsets

@Composable
fun SettingsRoute(moreActions: MoreMenuActions, vm: SettingsViewModel = hiltViewModel()) =
    SettingsScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, moreActions: MoreMenuActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val webdavConfigJson by vm.webdavConfigJson.collectAsStateWithLifecycle(initialValue = "")
    val autoSync by vm.autoSync.collectAsStateWithLifecycle(initialValue = true)
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle(initialValue = "")

    var davTesting by remember { mutableStateOf(false) }
    var davSyncing by remember { mutableStateOf(false) }
    var davPulling by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            /* ==================== 高频操作：导出 / 导入（常驻主页） ==================== */
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("数据备份", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                        Text(
                            "导入采用按主键合并：同一条数据以备份为准，本地新增的部分保留（与网页端一致）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            /* ==================== 高频操作：WebDAV 手动同步（常驻主页，无需进二级页） ==================== */
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WebDAV 同步", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        val cfg = vm.parseWebdavConfig(webdavConfigJson)
                        Text(
                            "当前：${if (cfg.url.isBlank()) "未配置" else cfg.url}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "自动同步（启动拉取 / 切后台推送）",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OnOffSwitch(checked = autoSync, onCheckedChange = { vm.setAutoSync(it) })
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

            /* ==================== 分组入口：点击进入独立二级页 ==================== */
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Palette,
                    label = "外观",
                    hint = "主题 · 壁纸 · 字号"
                ) { moreActions.onNavigate(Screen.SettingsAppearance.route) }
            }
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Build,
                    label = "通用",
                    hint = "提醒 · 音效 · AI 对话"
                ) { moreActions.onNavigate(Screen.SettingsGeneral.route) }
            }
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Backup,
                    label = "数据与备份",
                    hint = "WebDAV 配置 · 本地自动备份 · 隐私政策"
                ) { moreActions.onNavigate(Screen.SettingsBackupSync.route) }
            }
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Info,
                    label = "关于",
                    hint = "版本 · 团队 · 赞助 · 更新"
                ) { moreActions.onNavigate(Screen.SettingsAbout.route) }
            }
        }
    }
}
