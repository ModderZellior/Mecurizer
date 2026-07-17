package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkController;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkResult;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkScreen;
import net.caffeinemc.mods.sodium.client.MercurizerCapabilities;
import net.caffeinemc.mods.sodium.client.MercurizerConfigIcon;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MercurizerConfigEntryPoint implements ConfigEntryPoint {

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var modOptions = builder.registerModOptions("mercurizer")
                .setName("Mercurizer")
                .setIcon(MercurizerConfigIcon.ID);

        var page = builder.createOptionPage()
                .setName(Component.literal("Mercurizer"));

        // Hardware group
        var hardwareGroup = builder.createOptionGroup()
                .setName(Component.literal("Hardware"));

        MercurizerCapabilities caps = MercurizerCapabilities.getCached();
        if (caps != null) {
            addLabel(builder, hardwareGroup, "GPU: " + caps.renderer);
            addLabel(builder, hardwareGroup, "Vendor: " + caps.vendor);
            addLabel(builder, hardwareGroup, "Driver: " + caps.version);
            if (caps.totalVramMb > 0) {
                addLabel(builder, hardwareGroup, "VRAM: " + caps.totalVramMb + " MB");
            }
            addLabel(builder, hardwareGroup, "GPU type: " + (caps.isIntegratedGpu() ? "Integrated — for best performance, switch to your dedicated GPU" : "Dedicated"));

            StringBuilder capStr = new StringBuilder();
            if (caps.hasDirectStateAccess)   capStr.append("DSA ");
            if (caps.hasBufferStorage)       capStr.append("BufStore ");
            if (caps.hasMultiDrawIndirect)   capStr.append("MDI ");
            if (caps.hasTimerQuery)          capStr.append("Timer ");
            if (caps.hasS3tcCompression)     capStr.append("S3TC ");
            if (caps.hasRgtcCompression)     capStr.append("RGTC ");
            if (capStr.length() > 0) {
                addLabel(builder, hardwareGroup, "Caps: " + capStr.toString().strip());
            }
        } else {
            addLabel(builder, hardwareGroup, "Hardware info not yet available");
        }

        page.addOptionGroup(hardwareGroup);

        // Benchmark group
        var benchmarkGroup = builder.createOptionGroup()
                .setName(Component.literal("Benchmark"));

        MercurizerBenchmarkResult result = MercurizerTuning.getLastResult();
        if (result != null) {
            addLabel(builder, benchmarkGroup, String.format("GPU upload: %.0f MB/s (large) / %.0f MB/s (small)",
                    result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps));
            addLabel(builder, benchmarkGroup, String.format("CPU: %.0f MOps/s × %d cores",
                    result.cpuThroughputMOpsPerSec, result.availableProcessors));
            addLabel(builder, benchmarkGroup, "Source: " + (result.isRefined ? "Refined (adaptive)" : "Synthetic"));
        } else {
            addLabel(builder, benchmarkGroup, "No benchmark results yet");
        }

        benchmarkGroup.addOption(
                builder.createExternalButtonOption(Identifier.fromNamespaceAndPath("mercurizer", "rebenchmark"))
                        .setName(Component.literal("Re-run GPU Benchmark"))
                        .setTooltip(Component.literal("Clears stored results and runs the benchmark again"))
                        .setScreenConsumer(parent -> {
                            MercurizerBenchmarkController.resetForRebenchmark();
                            Minecraft.getInstance().setScreen(new MercurizerBenchmarkScreen(parent));
                        })
        );

        page.addOptionGroup(benchmarkGroup);

        // Active tuning group
        var tuningGroup = builder.createOptionGroup()
                .setName(Component.literal("Active Tuning"));

        addLabel(builder, tuningGroup, String.format("Upload fraction: %.1f%%",
                MercurizerTuning.getBaseUploadFraction() * 100));
        addLabel(builder, tuningGroup, String.format("Min upload budget: %.3f ms",
                MercurizerTuning.getBaseMinUploadBudgetNs() / 1_000_000.0));
        long texThresh = MercurizerTuning.getTextureAnimThresholdNs();
        addLabel(builder, tuningGroup, "Texture animation: " + (texThresh == Long.MAX_VALUE
                ? "Always on" : String.format("%.0f ms threshold", texThresh / 1_000_000.0)));

        page.addOptionGroup(tuningGroup);

        // Optimisation status group
        var statusGroup = builder.createOptionGroup()
                .setName(Component.literal("Optimisation Status"));

        boolean safePath = MercurizerRuntimePolicy.preferSafeStagingPath();
        addLabel(builder, statusGroup, "Safe staging path: " + (safePath
                ? "Active — reduced memory pressure, limited parallelism"
                : "Inactive — full performance mode"));
        addLabel(builder, statusGroup, "CPU/GPU frame sync: " + (safePath ? "Active" : "Inactive"));
        addLabel(builder, statusGroup, "RGSS: " + (safePath ? "Disabled (safe staging active)" : "Active"));
        addLabel(builder, statusGroup, "Texture animation throttle: Active");
        addLabel(builder, statusGroup, "Dynamic upload scaling: Active");
        if (result != null) {
            addLabel(builder, statusGroup, "Adaptive refinement: " + (result.isRefined
                    ? "Complete" : "Pending (play for 30s in stable scene)"));
        }

        page.addOptionGroup(statusGroup);

        modOptions.addPage(page);
    }

    private static void addLabel(ConfigBuilder builder, OptionGroupBuilder group, String text) {
        group.addOption(
                builder.createExternalButtonOption(
                                Identifier.fromNamespaceAndPath("mercurizer", "info_" + Math.abs(text.hashCode())))
                        .setName(Component.literal(text))
                        .setEnabled(false)
                        .setScreenConsumer(s -> {})
        );
    }
}
