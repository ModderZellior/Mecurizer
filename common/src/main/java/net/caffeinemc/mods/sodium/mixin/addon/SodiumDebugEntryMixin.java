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
        }
        if (result != null) {
            displayer.addToGroup(group, String.format("GPU upload: %.0f MB/s (large) / %.0f MB/s (small)",
                    result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps));
            displayer.addToGroup(group, String.format("CPU: %.0f MOps/s x %d cores",
                    result.cpuThroughputMOpsPerSec, result.availableProcessors));
        }
        displayer.addToGroup(group, String.format("Upload budget: %.0f%% frame / min %.2f ms",
                MercurizerTuning.getUploadFraction() * 100,
                MercurizerTuning.getMinUploadBudgetNs() / 1_000_000.0));
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
