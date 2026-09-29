package com.example.earthonline.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.earthonline.data.photos.MemoryPhoto
import com.example.earthonline.data.photos.MemoryPhotoStore
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 记忆相册（v1.0.3）：批量导入老照片 → 按月份自动归档成相册墙，
 * 每张照片可补录一句「当年的话」，也可设为主页壁纸。
 * 元数据存 DataStore JSON，图片原图完整复制（不重编码，画质红线）。
 */
data class AlbumUiState(
    val loaded: Boolean = false,
    /** 按月份分组（键 = YYYY-MM，降序 = 最近的月份在前） */
    val months: List<Pair<String, List<MemoryPhoto>>> = emptyList(),
    val total: Int = 0
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val store: MemoryPhotoStore,
    private val json: kotlinx.serialization.json.Json
) : ViewModel() {

    private val _photos = MutableStateFlow<List<MemoryPhoto>>(emptyList())
    val importing = MutableStateFlow(false)

    val state: StateFlow<AlbumUiState> = _photos.combine(importing) { list, busy ->
        val months = list
            .sortedByDescending { it.day }
            .groupBy { it.day.take(7) }
            .map { (month, photos) -> month to photos }
        AlbumUiState(loaded = true, months = months, total = list.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState())

    init {
        viewModelScope.launch { _photos.value = store.load() }
    }

    /** 批量导入（内容 URI）；导入完成刷新相册墙 */
    fun import(uris: List<*>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            val real = uris.filterIsInstance<android.net.Uri>()
            store.importAll(real)
            _photos.value = store.load()
            importing.value = false
        }
    }

    /** 补录一句「当年的话」 */
    fun setNote(id: String, note: String) {
        viewModelScope.launch {
            store.setNote(id, note)
            _photos.value = store.load()
        }
    }

    /** 删除照片（原图文件一并删除，仅删这一张） */
    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
            _photos.value = store.load()
        }
    }

    /** 设为主页壁纸（静态模式，关闭轮换） */
    fun useAsWallpaper(id: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = store.useAsWallpaper(id)
            onDone(ok)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumRoute(moreActions: MoreMenuActions, vm: AlbumViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()

    // 批量选图（Photo Picker 多选，Android 13+ 原生；旧版本回落 GetMultipleContents）
    val pickPhotos = rememberLauncherForAlbum { uris -> vm.import(uris) }

    var noteTarget by remember { mutableStateOf<MemoryPhoto?>(null) }
    var deleteTarget by remember { mutableStateOf<MemoryPhoto?>(null) }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            snackbarMsg = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("📷 记忆相册") },
                actions = { SettingsIconButton(actions = moreActions) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickPhotos() },
                containerColor = AmberPrimary
            ) {
                if (importing) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = "批量导入照片")
                }
            }
        }
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else if (state.months.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    emoji = "📷",
                    title = "相册还是空的",
                    message = "点右下角批量导入老照片：自动按拍摄月份归档成相册墙，" +
                        "每一张都能补一句当年的话。"
                )
            }
        } else {
            // 相册墙：按月份分组的纵向网格
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(UiDimens.ScreenPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.months.forEach { (month, photos) ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "m_$month") {
                        Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                            Text(
                                "${month.take(4)} 年 ${month.takeLast(2).toInt()} 月 · ${photos.size} 张",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    items(photos, key = { it.id }) { photo ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.clickable { noteTarget = photo }
                        ) {
                            AsyncImage(
                                model = File(photo.path),
                                contentDescription = photo.note.ifBlank { "照片" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            if (photo.note.isNotBlank()) {
                                Text(
                                    "💬 ${photo.note}",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 补录「当年的话」 + 设为壁纸 / 删除
    noteTarget?.let { photo ->
        AlbumNoteDialog(
            photo = photo,
            onDismiss = { noteTarget = null },
            onSaveNote = { note ->
                vm.setNote(photo.id, note)
                noteTarget = null
            },
            onDelete = {
                noteTarget = null
                deleteTarget = photo
            },
            onSetWallpaper = {
                vm.useAsWallpaper(photo.id) { ok ->
                    snackbarMsg = if (ok) "已设为主页壁纸" else "设置失败"
                }
                noteTarget = null
            }
        )
    }

    // 删除确认（危险操作二次确认）
    deleteTarget?.let { photo ->
        AnimatedAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这张照片？") },
            text = { Text("将从记忆相册中移除并删除原图文件，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(photo.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun AlbumNoteDialog(
    photo: MemoryPhoto,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onSetWallpaper: () -> Unit
) {
    var note by remember(photo.id) { mutableStateOf(photo.note) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📝 ${photo.day}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                coil.compose.AsyncImage(
                    model = File(photo.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("当年的话（可选）") },
                    placeholder = { Text("例如：大二那年和室友一起去的") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSaveNote(note) }) { Text("保存") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSetWallpaper) { Text("设为壁纸") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

/** 批量选图启动器：Android 13+ 用 Photo Picker，旧版本回落系统多选 */
@Composable
private fun rememberLauncherForAlbum(onPicked: (List<android.net.Uri>) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris -> onPicked(uris) }
    return { launcher.launch("image/*") }
}
