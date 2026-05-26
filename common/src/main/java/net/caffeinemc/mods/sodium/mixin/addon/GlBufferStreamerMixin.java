package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferStreamer;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlBufferStreamer.class)
public class GlBufferStreamerMixin {
    @WrapOperation(
            method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gl/arena/staging/MappedStagingBuffer;isSupported(Lnet/caffeinemc/mods/sodium/client/gl/device/RenderDevice;)Z"))
    private boolean wrapIsSupportedForStreamer(RenderDevice device, Operation<Boolean> original) {
        return original.call(device) && !MercurizerRuntimePolicy.preferSafeStagingPath();
    }
}
