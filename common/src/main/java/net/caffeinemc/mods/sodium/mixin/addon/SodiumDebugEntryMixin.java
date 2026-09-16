package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkResult;
import net.caffeinemc.mods.sodium.client.MercurizerCapabilities;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.caffeinemc.mods.sodium.client.gui.SodiumDebugEntry;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SodiumDebugEntry.class)
public class SodiumDebugEntryMixin {

    @WrapOperation(
            method = "display",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addToGroup(Lnet/minecraft/resources/Identifier;Ljava/lang/String;)V"))
    private void addMercurizerDebugInfo(DebugScreenDisplayer displayer, Identifier group, String text, Operation<Void> original) {
        original.call(displayer, group, text.replace("Sodium Renderer", "Mercurizer Renderer"));

        MercurizerCapabilities caps = MercurizerCapabilities.getCached();
        MercurizerBenchmarkResult result = MercurizerTuning.getLastResult();

        if (caps != null) {
            displayer.addToGroup(group, "GPU: " + caps.renderer);
            if (caps.totalVramMb > 0) {
                displayer.addToGroup(group, "VRAM: " + caps.totalVramMb + " MB");
            }
            if (caps.isIntegratedGpu()) {
                displayer.addToGroup(group, "WARNING: Running on integrated GPU - see Mercurizer settings");
            }
        }
        if (result != null) {
            displayer.addToGroup(group, String.format("GPU upload: %.0f MB/s (large) / %.0f MB/s (small)",
                    result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps));
            displayer.addToGroup(group, String.format("CPU: %.0f MOps/s x %d cores",
                    result.cpuThroughputMOpsPerSec, result.availableProcessors));
            displayer.addToGroup(group, "Benchmark: " + (result.isRefined ? "Refined" : "Synthetic"));
        }
        float liveFraction = MercurizerTuning.getUploadFraction();
        float baseFraction = MercurizerTuning.getBaseUploadFraction();
        long  liveBudget   = MercurizerTuning.getMinUploadBudgetNs();
        long  baseBudget   = MercurizerTuning.getBaseMinUploadBudgetNs();
        boolean dynamicActive = Math.abs(liveFraction - baseFraction) > 0.005f
                             || Math.abs(liveBudget - baseBudget) > 100_000L;
        if (dynamicActive) {
            displayer.addToGroup(group, String.format("Upload: base %.1f%% -> live %.1f%% (adapting) / budget %.2f ms",
                    baseFraction * 100, liveFraction * 100, liveBudget / 1_000_000.0));
        } else {
            displayer.addToGroup(group, String.format("Upload: %.1f%% frame / budget %.2f ms",
                    baseFraction * 100, liveBudget / 1_000_000.0));
        }
        if (caps != null) {
            String exts = (caps.hasDirectStateAccess ? "DSA " : "") +
                          (caps.hasBufferStorage ? "BufStore " : "") +
                          (caps.hasMultiDrawIndirect ? "MDI " : "") +
                          (caps.hasTimerQuery ? "Timer " : "") +
                          (caps.hasAnisotropicFiltering ? String.format("Aniso%.0fx ", caps.maxAnisotropy) : "");
            if (!exts.isBlank()) {
                displayer.addToGroup(group, "Ext: " + exts.strip());
            }
        }
    }
}
