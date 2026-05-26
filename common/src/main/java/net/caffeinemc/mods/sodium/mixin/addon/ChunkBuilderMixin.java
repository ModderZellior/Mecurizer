package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkBuilder.class)
public class ChunkBuilderMixin {
    @WrapOperation(
            method = "getThreadCount",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;getOptimalThreadCount()I"))
    private static int wrapGetOptimalThreadCount(Operation<Integer> original) {
        if (MercurizerRuntimePolicy.preferSafeStagingPath()) {
            return 1;
        }
        return MercurizerRuntimePolicy.getEffectiveChunkWorkerCount(original.call());
    }
}
