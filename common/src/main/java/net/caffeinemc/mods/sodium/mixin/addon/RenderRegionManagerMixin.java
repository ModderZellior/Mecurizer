package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderRegionManager.class)
public class RenderRegionManagerMixin {
    @WrapOperation(
            method = "createStagingBuffer",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gl/arena/staging/MappedStagingBuffer;isSupported(Lnet/caffeinemc/mods/sodium/client/gl/device/RenderDevice;)Z"))
    private static boolean wrapIsSupportedForRegion(RenderDevice device, Operation<Boolean> original) {
        return original.call(device) && !MercurizerRuntimePolicy.preferSafeStagingPath();
    }
}
