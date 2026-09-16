package net.caffeinemc.mods.sodium.client;

public final class MercurizerVulkanDeviceInfo {
    public final String deviceName;
    public final boolean isIntegrated;
    public final int vramMb;

    MercurizerVulkanDeviceInfo(String deviceName, boolean isIntegrated, int vramMb) {
        this.deviceName = deviceName;
        this.isIntegrated = isIntegrated;
        this.vramMb = vramMb;
    }
}
