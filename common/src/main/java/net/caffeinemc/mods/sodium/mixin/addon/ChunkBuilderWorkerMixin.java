package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerChunkCost;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder$WorkerRunnable")
public class ChunkBuilderWorkerMixin {

    @WrapOperation(
            method = "run",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJob;execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;)V"))
    private void measureJob(ChunkJob job, ChunkBuildContext context, Operation<Void> original) {
        long start = System.nanoTime();
        original.call(job, context);
        if (!job.isCancelled()) {
            MercurizerChunkCost.record(job.getEffort(), System.nanoTime() - start);
        }
    }
}
