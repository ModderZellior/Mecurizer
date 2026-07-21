package net.caffeinemc.mods.sodium.client;

public final class MercurizerGpuProfiles {
    private MercurizerGpuProfiles() {}

    public static float getMultiplier(String renderer) {
        if (renderer == null) return 1.0f;
        String r = renderer.toLowerCase();
        if (r.contains("llvmpipe"))  return 0.50f;
        if (r.contains("softpipe"))  return 0.45f;
        if (r.contains("intel"))     return 0.70f;
        return 1.0f;
    }
}
