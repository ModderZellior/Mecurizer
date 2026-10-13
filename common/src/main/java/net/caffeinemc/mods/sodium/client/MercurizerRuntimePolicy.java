package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;

public final class MercurizerRuntimePolicy {
    private static final boolean DETECTED_SAFE_STAGING_PATH = computeSafeStagingPath();
    private static final boolean SAFE_STAGING_PATH = MercurizerConfig.get().safeStagingPath != null
            ? MercurizerConfig.get().safeStagingPath
            : DETECTED_SAFE_STAGING_PATH;

    private static volatile int defaultChunkWorkerCount = -1;

    private MercurizerRuntimePolicy() {
    }

    private static boolean computeSafeStagingPath() {
        if (IntelWorkarounds.isUsingIntelGen8OrOlder()) {
            return true;
        }
        if (OsUtils.getOs() == OsUtils.OperatingSystem.WIN && isAmdGpuPresent()) {
            return true;
        }
        return false;
    }

    private static boolean isAmdGpuPresent() {
        return GraphicsAdapterProbe.getAdapters().stream()
                .anyMatch(adapter -> adapter.vendor() == GraphicsAdapterVendor.AMD);
    }

    public static boolean preferSafeStagingPath() {
        return SAFE_STAGING_PATH;
    }

    public static boolean isSafeStagingPathDetected() {
        return DETECTED_SAFE_STAGING_PATH;
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

    public static int getEffectiveChunkWorkerCount(int sodiumDefault) {
        int cores = Runtime.getRuntime().availableProcessors();
        defaultChunkWorkerCount = SAFE_STAGING_PATH ? 1 : Math.min(sodiumDefault, Math.max(1, cores / 2));
        Integer configured = MercurizerConfig.get().chunkBuilderThreads;
        if (configured != null) {
            return Math.max(1, Math.min(cores, configured));
        }
        return defaultChunkWorkerCount;
    }

    public static int getDefaultChunkWorkerCount() {
        if (defaultChunkWorkerCount > 0) return defaultChunkWorkerCount;
        return SAFE_STAGING_PATH ? 1 : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }

    public static boolean shouldAnimateVisibleTexturesThisFrame(long frameTimeNanos) {
        if (SAFE_STAGING_PATH && MercurizerConfig.get().textureAnimThresholdMs == null) {
            return frameTimeNanos < 33_000_000L;
        }
        return frameTimeNanos < MercurizerTuning.getTextureAnimThresholdNs();
    }
}
