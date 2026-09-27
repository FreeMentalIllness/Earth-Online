package com.example.earthonline.ui.settings
import com.example.earthonline.ui.components.AnimatedAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.WebDavConfig
import com.example.earthonline.data.sync.CloudSyncManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigDialog(
    initial: AiConfig,
    onDismiss: () -> Unit,
    onSave: (AiConfig) -> Unit
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(AiConfig(baseUrl.trim(), apiKey.trim(), model.trim())) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("AI 对话配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    baseUrl, { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://.../v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    apiKey, { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    model, { model = it },
                    label = { Text("模型名（必填）") },
                    // v1.2.1：只举例子，不再暗示有一个默认值
                    placeholder = { Text("如 deepseek-chat / qwen-plus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "兼容 OpenAI / 类 OpenAI 接口。模型名请照抄服务商文档，留空则无法发起对话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavConfigDialog(
    initial: WebDavConfig,
    onDismiss: () -> Unit,
    onSave: (WebDavConfig) -> Unit
) {
    var url by remember { mutableStateOf(initial.url) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.pass) }
    var path by remember { mutableStateOf(initial.path) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(WebDavConfig(url.trim(), user.trim(), pass, path.trim())) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("WebDAV 配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://dav.example.com/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    user, { user = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    pass, { pass = it },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    path, { path = it },
                    label = { Text("存储路径（可留空）") },
                    placeholder = { Text(CloudSyncManager.DEFAULT_REMOTE_PATH) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "留空则使用 ${CloudSyncManager.DEFAULT_REMOTE_PATH}，与网页端默认路径一致，两端即可互相同步。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
