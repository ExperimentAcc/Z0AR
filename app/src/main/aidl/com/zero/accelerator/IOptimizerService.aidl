// ZERO ACCELERATOR — privileged user-service contract.
// Implemented by OptimizerUserService and run by Shizuku as shell/root (UID 2000 / 0).
package com.zero.accelerator;

interface IOptimizerService {
    // Called by Shizuku to tear the privileged process down.
    // NOTE: Shizuku invokes this at transaction code 16777115; AIDL needs 16777114.
    void destroy() = 16777114;

    // Run the cleanup routine for the given mode.
    //  mode = 0 LOW, 1 BALANCED, 2 MONSTER
    //  targetPackage = the bound game (never touched)
    //  excludedPackages = comma separated packages that must NOT be force-stopped
    void optimize(int mode, String targetPackage, String excludedPackages) = 1;

    // Last human readable report produced by optimize().
    String getLastReport() = 2;
}
