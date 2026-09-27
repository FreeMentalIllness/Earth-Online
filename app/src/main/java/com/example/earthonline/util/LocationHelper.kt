package com.example.earthonline.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** 定位失败原因（UI 据此给出可操作提示） */
enum class LocateError { NO_PERMISSION, DISABLED, TIMEOUT, UNAVAILABLE }

/** 定位结果 */
sealed class LocateResult {
    data class Success(val lat: Double, val lng: Double) : LocateResult()
    data class Failure(val error: LocateError) : LocateResult()
}

/**
 * 定位工具。
 *
 * 修复「地图点定位没反应」的根因：旧实现只读 `getLastKnownLocation()`，
 * 该值在绝大多数真机（尤其是首次定位 / 模拟器 / 刚开机）为 null，且失败时不做任何提示，
 * 表现即「点了没反应」。现在改为 **主动请求一次定位**：
 * 新鲜缓存(2 分钟内) → 直接返回；否则向 GPS + 网络双源注册监听，拿到第一个有效点即返回；
 * 超时(默认 12s) 再退化为「陈旧缓存」或明确报错，绝不再静默失败。
 *
 * 不引入高德定位 SDK / Google FusedLocation（国内机型常无 GMS），用系统 LocationManager 即可。
 */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 系统定位开关是否打开（GPS 或网络任一） */
    fun isEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    /**
     * 主动请求一次当前位置。回调保证只触发一次（主线程）。
     */
    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(
        context: Context,
        timeoutMs: Long = 12_000L,
        onResult: (LocateResult) -> Unit
    ) {
        if (!hasPermission(context)) {
            onResult(LocateResult.Failure(LocateError.NO_PERMISSION))
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onResult(LocateResult.Failure(LocateError.UNAVAILABLE))
            return
        }
        if (!isEnabled(context)) {
            onResult(LocateResult.Failure(LocateError.DISABLED))
            return
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onResult(LocateResult.Failure(LocateError.DISABLED))
            return
        }

        // 1) 有 2 分钟内的新鲜缓存，直接复用（省电且秒回）
        val cached = bestLastKnown(lm, providers)
        if (cached != null && abs(System.currentTimeMillis() - cached.time) < 120_000L) {
            onResult(LocateResult.Success(cached.latitude, cached.longitude))
            return
        }

        // 2) 主动等一个真实定位点
        FixRequester(lm, providers, timeoutMs, onResult).start()
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(lm: LocationManager, providers: List<String>): Location? {
        var best: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (l.latitude == 0.0 && l.longitude == 0.0) continue
            if (best == null || l.time > best!!.time) best = l
        }
        return best
    }

    /** 同时监听多个 provider，取第一个有效点；超时则退化为陈旧缓存或报错。 */
    private class FixRequester(
        private val lm: LocationManager,
        private val providers: List<String>,
        private val timeoutMs: Long,
        private val onResult: (LocateResult) -> Unit
    ) : LocationListener {

        private val handler = Handler(Looper.getMainLooper())
        private var done = false
        private val timeoutTask = Runnable { finish(null) }

        @SuppressLint("MissingPermission")
        fun start() {
            var started = false
            for (p in providers) {
                runCatching {
                    lm.requestLocationUpdates(p, 0L, 0f, this, Looper.getMainLooper())
                    started = true
                }
            }
            if (!started) {
                finish(null)
                return
            }
            handler.postDelayed(timeoutTask, timeoutMs)
        }

        override fun onLocationChanged(location: Location) {
            if (location.latitude == 0.0 && location.longitude == 0.0) return
            finish(location)
        }

        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        @SuppressLint("MissingPermission")
        private fun finish(hit: Location?) {
            if (done) return
            done = true
            handler.removeCallbacks(timeoutTask)
            runCatching { lm.removeUpdates(this) }

            val target = when {
                hit != null -> hit
                else -> runCatching { bestLastKnown(lm, providers) }.getOrNull()
            }
            if (target != null) {
                onResult(LocateResult.Success(target.latitude, target.longitude))
            } else {
                onResult(LocateResult.Failure(LocateError.TIMEOUT))
            }
        }
    }
}
