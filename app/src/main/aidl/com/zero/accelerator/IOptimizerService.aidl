// ZERO ACCELERATOR - privileged user-service contract.
// Implemented by OptimizerUserService and run by Shizuku as shell/root (UID 2000 / 0).
package com.zero.accelerator;

interface IOptimizerService {
    // Reserved by Shizuku (transaction 16777115 = FIRST_CALL_TRANSACTION + 16777114).
    void destroy() = 16777114;

    // mode: 0 = Battery Saver, 1 = Balanced, 2 = Monster
    void optimize(int mode, String targetPackage, String excludedPackages) = 1;

    String getLastReport() = 2;

    // "cpuKhz|cpuMaxKhz|gpuMhz|gpuMaxMhz|fps"  (fps = -1 when unknown)
    String telemetry(String targetPackage) = 3;

    // 0 = smart / system default, otherwise lock to this Hz
    void setRefreshRate(int hz) = 4;

    void setBrightnessLock(boolean on) = 5;

    // which = "wifi" | "data"
    void setNetwork(String which, boolean on) = 6;

    void setFixedPerformance(boolean on) = 7;

    // Undo everything changed during the session.
    void restore() = 8;
}
