package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MercurizerInfoScreen extends Screen {
    private final Screen returnScreen;

    public MercurizerInfoScreen(Screen returnScreen) {
        super(Component.literal("Mercurizer"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int bottomY = this.height - 30;

        addRenderableWidget(Button.builder(
                Component.literal("Re-run Benchmark"),
                btn -> {
                    MercurizerBenchmarkController.resetForRebenchmark();
                    Minecraft.getInstance().setScreen(new MercurizerBenchmarkScreen(this.returnScreen));
                })
                .bounds(centerX - 105, bottomY, 100, 20)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Close"), btn -> onClose())
                .bounds(centerX + 5, bottomY, 100, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xD0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(this.font, "Mercurizer", this.width / 2, 12, 0xFFFFFF55);

        int x = 20;
        int y = 30;
        for (String line : buildLines()) {
            if (line.isEmpty()) {
                y += 4;
            } else if (line.startsWith("§")) {
                graphics.text(this.font, line.substring(1), x, y, 0xFFAAAAAA);
                y += 10;
            } else {
                graphics.text(this.font, line, x, y, 0xFFFFFFFF);
                y += 10;
            }
        }
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        MercurizerCapabilities caps = MercurizerCapabilities.getCached();

        lines.add("§Hardware");
        if (caps != null) {
            lines.add("GPU: " + caps.renderer);
            lines.add("Vendor: " + caps.vendor);
            lines.add("Driver: " + caps.version);
            if (caps.totalVramMb > 0) lines.add("VRAM: " + caps.totalVramMb + " MB");
            lines.add("Type: " + (caps.isIntegratedGpu() ? "Integrated GPU" : "Dedicated GPU"));
        } else {
            lines.add("Hardware info unavailable (Vulkan backend or not yet probed)");
        }

        lines.add("");
        lines.add("§Benchmark");
        MercurizerBenchmarkResult result = MercurizerTuning.getLatestRaw();
        if (result != null) {
            if (result.bufferUploadBandwidthMBps >= 0) {
                lines.add(String.format("GPU upload: %.0f MB/s (large), %.0f MB/s (small)",
                        result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps));
            }
            lines.add(String.format("CPU: %.0f MOps/s x %d cores",
                    result.cpuThroughputMOpsPerSec, result.availableProcessors));
            if (result.smallBufferRoundTripNs > 0 && result.smallBufferSizeBytes > 0) {
                double scaledUs = result.smallBufferRoundTripNs * (128_000.0 / result.smallBufferSizeBytes) / 1000.0;
                lines.add(String.format("Round-trip latency (128KB): %.0f us", scaledUs));
            }
            if (result.benchmarkConfidenceScore > 0) {
                String confLine = String.format("Confidence: CoV %.0f%%", result.benchmarkConfidenceScore * 100);
                if (result.isLowConfidence) confLine += " [LOW]";
                lines.add(confLine);
            }
            lines.add("Source: " + (result.isRefined ? "Refined (adaptive)" : "Synthetic"));
        } else {
            lines.add("No benchmark results yet");
        }

        lines.add("");
        lines.add("§Active Tuning");
        lines.add(String.format("Upload fraction: %.1f%%", MercurizerTuning.getBaseUploadFraction() * 100));
        lines.add(String.format("Min upload budget: %.3f ms", MercurizerTuning.getBaseMinUploadBudgetNs() / 1_000_000.0));
        long texThresh = MercurizerTuning.getTextureAnimThresholdNs();
        lines.add("Texture animation: " + (texThresh == Long.MAX_VALUE
                ? "Always on" : String.format("%.0f ms threshold", texThresh / 1_000_000.0)));

        lines.add("");
        lines.add("§Optimisation Status");
        boolean safePath = MercurizerRuntimePolicy.preferSafeStagingPath();
        lines.add("Safe staging path: " + (safePath ? "Active" : "Inactive"));
        lines.add("RGSS: " + (safePath ? "Disabled (safe staging active)" : "Active"));
        lines.add("Dynamic upload scaling: Active");
        lines.add("Texture animation throttle: Active");
        if (result != null) {
            lines.add("Adaptive refinement: " + (result.isRefined ? "Complete" : "Pending (play 30s in a stable scene)"));
        }

        return lines;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.returnScreen);
    }
}
