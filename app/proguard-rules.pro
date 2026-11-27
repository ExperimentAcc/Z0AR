# ---- Shizuku / Sui internals ----
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# ---- Our AIDL stub (Shizuku instantiates the service by class name) ----
-keep class com.zero.accelerator.IOptimizerService { *; }
-keep class com.zero.accelerator.IOptimizerService$Stub { *; }
-keep class com.zero.accelerator.IOptimizerService$Proxy { *; }
-keep class com.zero.accelerator.optimizer.OptimizerUserService { *; }

# Shizuku calls destroy() via a fixed transaction code; keep the method.
-keepclassmembers class com.zero.accelerator.optimizer.OptimizerUserService {
    public void destroy();
}

# Keep any IInterface implementations intact.
-keep class * implements android.os.IInterface { *; }

# Enums persisted by name()
-keepclassmembers class com.zero.accelerator.model.PerfMode {
    <fields>;
    public static ** values();
    public static ** valueOf(java.lang.String);
}

# Keep BuildConfig fields referenced from Shizuku.UserServiceArgs
-keep class com.zero.accelerator.BuildConfig { *; }

# Silence optional/missing refs from Shizuku & hidden APIs during R8
-dontwarn rikka.**
-dontwarn moe.shizuku.**
-dontwarn android.**

# ---- v2 ----
-keep class com.zero.accelerator.GameAccessibilityService { *; }
-keep class com.zero.accelerator.util.Sysfs { *; }
