package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;

public final class MercurizerRuntimePolicy {
    // Cached at class load time — hardware never changes at runtime
    private static final boolean SAFE_STAGING_PATH = computeSafeStagingPath();

    private MercurizerRuntimePolicy() {
    }

    private static boolean computeSafeStagingPath() {
        if (IntelWorkarounds.isUsingIntelGen8OrOlder()) {
            return true;
        }
        if (Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_GAME_OPTIMIZATION_BROKEN)) {
            return true;
        }
        return false;
    }

    public static boolean preferSafeStagingPath() {
        return SAFE_STAGING_PATH;
    }

    public static int getEffectiveCpuRenderAheadLimit(int configuredLimit) {
        if (SAFE_STAGING_PATH) {
            return Math.min(configuredLimit, 1);
        }
        return configuredLimit;
    }

    public static boolean shouldUseNoErrorContext(boolean configured) {
        return configured && !SAFE_STAGING_PATH;
    }

    public static int getEffectiveChunkWorkerCount(int configured) {
        if (SAFE_STAGING_PATH) {
            return 1;
        }
        int cores = Runtime.getRuntime().availableProcessors();
        int reasonable = Math.max(1, cores / 2);
        return Math.min(configured, reasonable);
    }

    public static boolean shouldAnimateVisibleTexturesThisFrame(long frameTimeNanos) {
        if (!SAFE_STAGING_PATH) {
            return true;
        }
        return frameTimeNanos < 33_000_000L;
    }
}
