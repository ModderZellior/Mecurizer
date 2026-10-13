package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkResult;
import net.caffeinemc.mods.sodium.client.MercurizerCapabilities;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.caffeinemc.mods.sodium.client.gui.SodiumDebugEntry;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(SodiumDebugEntry.class)
public class SodiumDebugEntryMixin {
    @Unique
    private static final ResourceLocation MERCURIZER_GROUP = ResourceLocation.fromNamespaceAndPath("mercurizer", "debug");

    @WrapOperation(
            method = "display",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addLine(Ljava/lang/String;)V"))
    private void renameRendererLine(DebugScreenDisplayer displayer, String text, Operation<Void> original) {
        original.call(displayer, text.replace("Sodium Renderer", "Mercurizer Renderer"));
    }

    @Inject(method = "display", at = @At("TAIL"))
    private void addMercurizerDebugInfo(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk, CallbackInfo ci) {
        List<String> lines = new ArrayList<>();
        MercurizerCapabilities caps = MercurizerCapabilities.getCached();
        MercurizerBenchmarkResult result = MercurizerTuning.getLastResult();

        if (caps != null) {
            lines.add("GPU: " + caps.renderer);
            if (caps.totalVramMb > 0) {
                lines.add("VRAM: " + caps.totalVramMb + " MB");
            }
            if (caps.isIntegratedGpu()) {
                lines.add("WARNING: Running on integrated GPU - see Mercurizer settings");
            }
        }
        if (result != null) {
            lines.add(String.format("GPU upload: %.0f MB/s (large) / %.0f MB/s (small)",
                    result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps));
            lines.add(String.format("CPU: %.0f MOps/s x %d cores",
                    result.cpuThroughputMOpsPerSec, result.availableProcessors));
            lines.add("Benchmark: " + (result.isRefined ? "Refined" : "Synthetic"));
        }
        float liveFraction = MercurizerTuning.getUploadFraction();
        float baseFraction = MercurizerTuning.getBaseUploadFraction();
        long  liveBudget   = MercurizerTuning.getMinUploadBudgetNs();
        long  baseBudget   = MercurizerTuning.getBaseMinUploadBudgetNs();
        boolean dynamicActive = Math.abs(liveFraction - baseFraction) > 0.005f
                             || Math.abs(liveBudget - baseBudget) > 100_000L;
        if (dynamicActive) {
            lines.add(String.format("Upload: base %.1f%% -> live %.1f%% (adapting) / budget %.2f ms",
                    baseFraction * 100, liveFraction * 100, liveBudget / 1_000_000.0));
        } else {
            lines.add(String.format("Upload: %.1f%% frame / budget %.2f ms",
                    baseFraction * 100, liveBudget / 1_000_000.0));
        }
        if (caps != null) {
            String exts = (caps.hasDirectStateAccess ? "DSA " : "") +
                          (caps.hasBufferStorage ? "BufStore " : "") +
                          (caps.hasMultiDrawIndirect ? "MDI " : "") +
                          (caps.hasTimerQuery ? "Timer " : "") +
                          (caps.hasAnisotropicFiltering ? String.format("Aniso%.0fx ", caps.maxAnisotropy) : "");
            if (!exts.isBlank()) {
                lines.add("Ext: " + exts.strip());
            }
        }

        displayer.addToGroup(MERCURIZER_GROUP, lines);
    }
}
