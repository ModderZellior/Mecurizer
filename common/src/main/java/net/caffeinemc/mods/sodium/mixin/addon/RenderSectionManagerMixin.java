package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerChunkCost;
import net.caffeinemc.mods.sodium.client.MercurizerFrameTracker;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {
    @Unique private static final float HIGH_EFFORT_SHARE = 10.0f / 11.0f;

    @Shadow @Final private ChunkBuilder builder;

    @Unique private long lastUpdateNanos;
    @Unique private long lastFrameDuration;
    @Unique private long averageFrameDuration;
    @Unique private int effortBudget = Integer.MAX_VALUE;
    @Unique private int highEffortGranted;

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void recordFrameTime(CallbackInfo ci) {
        long now = System.nanoTime();
        if (this.lastUpdateNanos != 0L) {
            this.lastFrameDuration = now - this.lastUpdateNanos;
            long average = this.averageFrameDuration == 0L
                    ? this.lastFrameDuration
                    : this.averageFrameDuration + (long) ((this.lastFrameDuration - this.averageFrameDuration) * 0.05f);
            this.averageFrameDuration = Math.max(1_000_100L, Math.min(100_000_000L, average));
        }
        this.lastUpdateNanos = now;

        MercurizerFrameTracker.record(this.lastFrameDuration);
        MercurizerTuning.checkRefinement();

        float fraction = MercurizerTuning.getUploadFraction();
        long minBudget = MercurizerTuning.getMinUploadBudgetNs();
        double nanosPerEffort = MercurizerChunkCost.getNanosPerEffort();

        if (this.averageFrameDuration <= 0L || nanosPerEffort <= 0) {
            this.effortBudget = Integer.MAX_VALUE;
            return;
        }

        long timeBudget = Math.max((long) (this.averageFrameDuration * fraction), minBudget);
        int threads = Math.max(1, this.builder.getTotalThreadCount());
        this.effortBudget = (int) Math.min(Integer.MAX_VALUE, timeBudget * threads / nanosPerEffort);
    }

    @WrapOperation(method = "updateChunks", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;getHighEffortSchedulingBudget()I"))
    private int limitHighEffortBudget(ChunkBuilder chunkBuilder, Operation<Integer> original) {
        int sodiumBudget = original.call(chunkBuilder);
        int ours = (int) (this.effortBudget * HIGH_EFFORT_SHARE);
        this.highEffortGranted = Math.max(ChunkBuilder.HIGH_EFFORT, Math.min(sodiumBudget, ours));
        return this.highEffortGranted;
    }

    @WrapOperation(method = "updateChunks", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;getLowEffortSchedulingBudget()I"))
    private int limitLowEffortBudget(ChunkBuilder chunkBuilder, Operation<Integer> original) {
        int sodiumBudget = original.call(chunkBuilder);
        int remaining = this.effortBudget == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : this.effortBudget - this.highEffortGranted;
        return Math.max(ChunkBuilder.LOW_EFFORT, Math.min(sodiumBudget, remaining));
    }

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void limitTextureAnimation(CallbackInfo ci) {
        if (!MercurizerRuntimePolicy.shouldAnimateVisibleTexturesThisFrame(this.lastFrameDuration)) {
            ci.cancel();
        }
    }
}
