# 保留 Application 子类与 Hilt 生成的入口
-keep class com.example.earthonline.EarthOnlineApp { *; }
-keep class * implements dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# 高德 SDK
-keep class com.amap.api.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.** { *; }
-keep class * implements java.io.Serializable

# 只接入了 3dmap，未引入定位 SDK（定位走 Android 原生 LocationManager）。
# 3dmap 内部引用 com.amap.api.location.*，R8 会报 Missing class 并中断构建，这里忽略。
-dontwarn com.amap.api.location.**
-dontwarn com.autonavi.**

# R8 8.5.35 的优化期类合并（class merging）在合并 Hilt/WorkManager 生成代码时会触发内部
# ConcurrentModificationException（shaking.M）。直接关闭优化即可绕过该工具 bug，
# 代码瘦身（shrinking）仍生效，仅失去内联/合并，APK 体积略增但可正常构建。
-dontoptimize
# R8 严格模式下缺失类会直接失败；其余第三方缺失项按警告处理
-ignorewarnings

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# kotlinx.serialization（保留实体字段名与 @Serializable 注解）
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlinx.serialization.** { *; }
-keep class com.example.earthonline.data.local.entity.** { *; }
-keep class com.example.earthonline.data.network.** { *; }
-keep class com.example.earthonline.data.model.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# 到期提醒：Worker / 调度器 / 广播接收器（Hilt + WorkManager 反射引用，显式保留防 R8 误删）
-keep class com.example.earthonline.reminder.** { *; }
