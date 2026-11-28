# R8 / ProGuard 规则（Release 构建启用 isMinifyEnabled = true 与 isShrinkResources = true）
#
# 标准 Android 组件、View/Parcelable 与各依赖库（AndroidX / Compose / Coroutines / OkHttp / Coil）
# 的基础规则由 proguard-android-optimize.txt 及各 AAR 自带的 consumer-rules 自动提供。

# 保留行号与脱敏文件名，便于配合 mapping.txt 还原 Release 崩溃堆栈，同时避免泄露本机绝对路径
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Release 环境下裁剪调试与诊断日志调用
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
