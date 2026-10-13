package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkResult;
import net.caffeinemc.mods.sodium.client.MercurizerCapabilities;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class MercurizerDebugScreenOverlayMixin {

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void addMercurizerDebugInfo(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = cir.getReturnValue();

        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Sodium Renderer")) {
                header = i;
                break;
            }
        }
        if (header < 0) {
            return;
        }
        lines.set(header, lines.get(header).replace("Sodium Renderer", "Mercurizer Renderer"));

        int insertAt = header + 1;
        while (insertAt < lines.size() && !lines.get(insertAt).isEmpty()) {
            insertAt++;
        }

        List<String> extra = new ArrayList<>();
        MercurizerCapabilities caps = MercurizerCapabilities.getCached();
        MercurizerBenchmarkResult result = MercurizerTuning.getLastResult();

        if (caps != null) {
            extra.add("GPU: " + caps.renderer);
            if (caps.totalVramMb > 0) {
                extra.add("VRAM: " + caps.totalVramMb + " MB");
            }
            if (caps.isIntegratedGpu()) {
                extra.add("WARNING: Running on integrated GPU - see Mercurizer settings");
            }
        }
        if (result != null) {
            extra.add(String.format("GPU upload: %.0f MB/s (large) / %.0f MB/s (small)",
                    result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps));
            extra.add(String.format("CPU: %.0f MOps/s x %d cores",
                    result.cpuThroughputMOpsPerSec, result.availableProcessors));
            extra.add("Benchmark: " + (result.isRefined ? "Refined" : "Synthetic"));
        }
        float liveFraction = MercurizerTuning.getUploadFraction();
        float baseFraction = MercurizerTuning.getBaseUploadFraction();
        long  liveBudget   = MercurizerTuning.getMinUploadBudgetNs();
        long  baseBudget   = MercurizerTuning.getBaseMinUploadBudgetNs();
        boolean dynamicActive = Math.abs(liveFraction - baseFraction) > 0.005f
                             || Math.abs(liveBudget - baseBudget) > 100_000L;
        if (dynamicActive) {
            extra.add(String.format("Upload: base %.1f%% -> live %.1f%% (adapting) / budget %.2f ms",
                    baseFraction * 100, liveFraction * 100, liveBudget / 1_000_000.0));
        } else {
            extra.add(String.format("Upload: %.1f%% frame / budget %.2f ms",
                    baseFraction * 100, liveBudget / 1_000_000.0));
        }
        if (caps != null) {
            String exts = (caps.hasDirectStateAccess ? "DSA " : "") +
                          (caps.hasBufferStorage ? "BufStore " : "") +
                          (caps.hasMultiDrawIndirect ? "MDI " : "") +
                          (caps.hasTimerQuery ? "Timer " : "") +
                          (caps.hasAnisotropicFiltering ? String.format("Aniso%.0fx ", caps.maxAnisotropy) : "");
            if (!exts.isBlank()) {
                extra.add("Ext: " + exts.strip());
            }
        }

        lines.addAll(insertAt, extra);
    }
}
