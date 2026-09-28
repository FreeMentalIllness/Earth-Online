package com.example.earthonline.ui.map

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.example.earthonline.data.local.entity.LocationEntity
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.LocationHelper
import com.example.earthonline.util.LocateError
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.todayStr
import kotlinx.coroutines.launch

/**
 * 地图是否启用真实高德地图。
 * Key 已配置在 AndroidManifest.xml 的 com.amap.api.v2.apikey。
 * 若需临时降级为足迹列表（如排查地图黑屏），改为 false 即可。
 */
private const val MAP_ENABLED = true

@Composable
fun MapRoute(moreActions: MoreMenuActions, vm: MapViewModel = hiltViewModel()) =
    MapScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel, moreActions: MoreMenuActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locations by vm.locations.collectAsStateWithLifecycle(initialValue = emptyList())

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingLatLng by remember { mutableStateOf<LatLng?>(null) }
    /** 是否正在定位（UI 显示进度，避免「点了没反应」） */
    var locating by remember { mutableStateOf(false) }

    /**
     * 主动定位（调用前请自行确保已授权）。每一步都有明确反馈：
     * 旧实现只读 getLastKnownLocation（多数真机为 null）且失败静默，是「点定位没反应」的根因。
     * @param silent true=静默模式（进入地图自动定位），失败不弹提示，避免打扰
     */
    fun locate(silent: Boolean) {
        if (locating) return
        locating = true
        if (!silent) scope.launch { snackbar.showSnackbar("正在定位…") }
        LocationHelper.requestCurrentLocation(context) { result ->
            locating = false
            when (result) {
                is com.example.earthonline.util.LocateResult.Success -> {
                    val ll = LatLng(result.lat, result.lng)
                    currentLatLng = ll
                    aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 15f))
                    aMap?.addMarker(MarkerOptions().position(ll).title("我的位置"))
                    if (!silent) scope.launch { snackbar.showSnackbar("已定位到当前位置") }
                }
                is com.example.earthonline.util.LocateResult.Failure -> scope.launch {
                    when (result.error) {
                        LocateError.NO_PERMISSION -> snackbar.showSnackbar("没有定位权限，请授予后再试")
                        LocateError.DISABLED -> snackbar.showSnackbar(
                            message = "系统定位已关闭，请开启后重试",
                            actionLabel = "去开启"
                        ).let {
                            if (it == SnackbarResult.ActionPerformed) {
                                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                        LocateError.TIMEOUT -> snackbar.showSnackbar("暂时定位不到，请到室外或打开 WiFi 后重试")
                        LocateError.UNAVAILABLE -> snackbar.showSnackbar("设备不支持定位")
                    }
                }
            }
        }
    }

    // 授权回调：拿到权限立即定位；被拒则明确告知去哪里开启
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate(false)
        else scope.launch { snackbar.showSnackbar("未授予定位权限，可在系统设置中开启") }
    }

    // 进入地图且已授权时，静默定位一次（失败不打扰用户）
    var autoLocateDone by remember { mutableStateOf(false) }
    LaunchedEffect(aMap) {
        if (!autoLocateDone && aMap != null && LocationHelper.hasPermission(context)) {
            autoLocateDone = true
            locate(true)
        }
    }

    // 足迹 marker 随数据变化刷新（容错：地图已销毁时静默跳过）
    LaunchedEffect(locations, aMap) {
        try {
            aMap?.let { map ->
                map.clear()
                locations.forEach { loc ->
                    if (loc.lat != 0.0 || loc.lng != 0.0) {
                        map.addMarker(MarkerOptions().position(LatLng(loc.lat, loc.lng)).title(loc.name).snippet(loc.note ?: ""))
                    }
                }
            }
        } catch (_: Exception) { /* MapView 已销毁等情况，忽略 */ }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("足迹地图") },
                actions = { SettingsIconButton(actions = moreActions) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = {
                        if (LocationHelper.hasPermission(context)) locate(false)
                        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                ) {
                    if (locating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = "定位到我的位置")
                    }
                }
                FloatingActionButton(onClick = {
                    pendingLatLng = aMap?.cameraPosition?.target ?: currentLatLng
                    showAdd = true
                }) { Icon(Icons.Filled.Add, null) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (MAP_ENABLED) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapsInitializer.updatePrivacyAgree(ctx, true)
                        MapsInitializer.updatePrivacyShow(ctx, true, true)
                        val mv = MapView(ctx)
                        // 注意：不在 factory 里调用 onCreate —— 生命周期统一交给下方的
                        // DisposableEffect(Unit)，保证 onCreate/onResume/onPause/onDestroy
                        // 顺序与配对正确，避免离开页面未正确销毁、再次进入时崩溃。
                        mapView = mv
                        aMap = mv.map
                        aMap?.uiSettings?.isMyLocationButtonEnabled = false
                        aMap?.setOnMapLongClickListener { latLng ->
                            pendingLatLng = latLng
                            showAdd = true
                        }
                        mv
                    }
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AmberPrimary.copy(alpha = 0.12f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("地图待配置高德 Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "将 AndroidManifest.xml 中 com.amap.api.v2.apikey 改为你的高德 Android Key，并把 MapScreen.kt 顶部 MAP_ENABLED 改为 true，即可显示交互式地图。当前以列表展示足迹。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LocationList(locations, vm::deleteLocation)
                }
            }
        }
    }

    // 地图生命周期：集中管理 onCreate/onResume/onPause/onDestroy 并全部 try/catch 容错。
    // 关键修复：此前 factory 里 onCreate、onRelease 里 onDestroy，与这里的 onResume/onPause
    // 释放顺序不保证，会出现「onPause 调用到已 onDestroy 的 MapView」异常；
    // 再次进入地图时 MapView 状态已损坏 → 闪退。现在统一在这里按正确顺序配对。
    DisposableEffect(Unit) {
        try { mapView?.onCreate(null) } catch (_: Exception) { }
        try { mapView?.onResume() } catch (_: Exception) { }
        onDispose {
            try { mapView?.onPause() } catch (_: Exception) { }
            try { mapView?.onDestroy() } catch (_: Exception) { }
            mapView = null
            aMap = null
        }
    }

    if (showAdd) {
        AddLocationDialog(
            initialDate = todayStr(),
            onDismiss = { showAdd = false; pendingLatLng = null },
            onConfirm = { name, date, note, tags ->
                val ll = pendingLatLng ?: currentLatLng
                if (ll != null) {
                    vm.addLocation(name, ll.latitude, ll.longitude, date, note, tags)
                } else {
                    scope.launch { snackbar.showSnackbar("尚无坐标：请先点击定位获取位置") }
                }
                showAdd = false
                pendingLatLng = null
            }
        )
    }
}

@Composable
private fun LocationList(locations: List<LocationEntity>, onDelete: (LocationEntity) -> Unit) {
    if (locations.isEmpty()) {
        Text("还没有足迹，点击右下角 + 添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            locations,
            key = { it.id },
            contentType = { "locationRow" }
        ) { loc ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Place, null, tint = AmberPrimary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(loc.name.ifBlank { "未命名足迹" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (loc.date.isNotBlank()) {
                            Text(loc.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!loc.note.isNullOrBlank()) {
                            Text(loc.note!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Text("%.4f, %.4f".format(loc.lat, loc.lng), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(loc) }) { Icon(Icons.Filled.Delete, null) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLocationDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: String, note: String?, tags: List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var note by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加足迹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, {}, readOnly = true, label = { Text("日期") },
                    trailingIcon = { IconButton({ showDate = true }) { Icon(Icons.Filled.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tagsText, { tagsText = it }, label = { Text("标签（逗号分隔）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onConfirm(name.ifBlank { "未命名足迹" }, date, note.ifBlank { null }, tags)
                },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )

    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { date = millisToDayStr(it) }
                showDate = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDate = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}
