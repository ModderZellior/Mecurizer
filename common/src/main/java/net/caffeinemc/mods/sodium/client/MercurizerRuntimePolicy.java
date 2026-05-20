package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;

public final class MercurizerRuntimePolicy {
    private static final float DEFAULT_UPLOAD_FRACTION = 0.04f;
    private static final float SAFE_UPLOAD_FRACTION = 0.025f;
    private static final int DEFAULT_MAX_UPLOAD_RESULTS_PER_FRAME = 12;
    private static final int SAFE_MAX_UPLOAD_RESULTS_PER_FRAME = 6;

    private MercurizerRuntimePolicy() {
    }

    public static boolean preferSafeStagingPath() {
        return IntelWorkarounds.isUsingIntelGen8OrOlder()
                || Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_GAME_OPTIMIZATION_BROKEN);
    }

    public static int getEffectiveCpuRenderAheadLimit(int configuredLimit) {
        if (preferSafeStagingPath()) {
            return Math.min(configuredLimit, 1);
        }

        return configuredLimit;
    }

    public static boolean shouldUseNoErrorContext(boolean configured) {
        return configured && !preferSafeStagingPath();
    }

    public static int getEffectiveChunkWorkerCount(int configured) {
        if (preferSafeStagingPath()) {
            return Math.min(configured, 1);
        }

        return Math.min(configured, 2);
    }

    public static float getUploadFraction() {
        return preferSafeStagingPath() ? SAFE_UPLOAD_FRACTION : DEFAULT_UPLOAD_FRACTION;
    }

    public static int getMaxUploadResultsPerFrame() {
        return preferSafeStagingPath() ? SAFE_MAX_UPLOAD_RESULTS_PER_FRAME : DEFAULT_MAX_UPLOAD_RESULTS_PER_FRAME;
    }

    public static boolean shouldAnimateVisibleTexturesThisFrame(int frame) {
        if (!preferSafeStagingPath()) {
            return true;
        }

        return (frame & 1) == 0;
    }
}
