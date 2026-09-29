package com.example.earthonline.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 全局网络状态：同步查询 + Compose 响应式订阅。
 *
 * 为什么自己写而不用官方 connectivity-compose：项目红线「不引入未经确认的新依赖」，
 * 而 ConnectivityManager 的 NetworkCallback 一共就二十行，没必要为此加一个库。
 */
object NetworkMonitor {

    /** 点击时机的同步查询（WebDAV 手动同步 / AI 发送前用） */
    fun isOnlineNow(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Compose 响应式网络状态。
 *
 * 注意：NetworkCallback 回调发生在系统 binder 线程，这里直接写 mutableStateOf 是安全的
 * （Compose Snapshot 状态本身线程安全），不需要切线程。
 */
@androidx.compose.runtime.Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val isOnline = remember { mutableStateOf(NetworkMonitor.isOnlineNow(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = true
            }

            override fun onLost(network: Network) {
                // 可能还有其他可用网络（如 WiFi 断了还有流量），以实时查询为准
                isOnline.value = NetworkMonitor.isOnlineNow(context)
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }
    return isOnline
}
