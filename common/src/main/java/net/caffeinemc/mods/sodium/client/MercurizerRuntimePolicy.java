package net.caffeinemc.mods.sodium.client;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;
import org.lwjgl.opengl.GL11C;

public final class MercurizerRuntimePolicy {
    private static final float DEFAULT_UPLOAD_FRACTION = 0.04f;
    private static final float SAFE_UPLOAD_FRACTION = 0.025f;
    private static final int DEFAULT_MAX_UPLOAD_RESULTS_PER_FRAME = 12;
    private static final int SAFE_MAX_UPLOAD_RESULTS_PER_FRAME = 6;

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
        // NVIDIA MX series and other low-end discrete GPUs share memory characteristics
        // with integrated graphics — detect via renderer string
        String renderer = GL11C.glGetString(GL11C.GL_RENDERER);
        if (renderer != null) {
            String r = renderer.toLowerCase();
            if (r.contains("mx") && r.contains("nvidia")) {
                return true;
            }
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

    public static float getUploadFraction() {
        return SAFE_STAGING_PATH ? SAFE_UPLOAD_FRACTION : DEFAULT_UPLOAD_FRACTION;
    }

    public static int getMaxUploadResultsPerFrame() {
        return SAFE_STAGING_PATH ? SAFE_MAX_UPLOAD_RESULTS_PER_FRAME : DEFAULT_MAX_UPLOAD_RESULTS_PER_FRAME;
    }

    public static boolean shouldAnimateVisibleTexturesThisFrame(long frameTimeNanos) {
        if (!SAFE_STAGING_PATH) {
            return true;
        }
        // Only skip texture animation when frame time is above 33ms (below ~30 FPS).
        // At higher frame rates the cost is acceptable; at low FPS every ms counts.
        return frameTimeNanos < 33_000_000L;
    }
}
