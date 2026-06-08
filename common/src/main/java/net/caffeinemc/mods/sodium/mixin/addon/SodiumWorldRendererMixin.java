package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SodiumWorldRenderer.class)
public class SodiumWorldRendererMixin {
    @ModifyArg(
            method = "initRenderer",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;<init>(Lnet/minecraft/client/multiplayer/ClientLevel;ILnet/caffeinemc/mods/sodium/client/render/chunk/translucent_sorting/SortBehavior;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;)V"),
            index = 2)
    private SortBehavior modifySortBehavior(SortBehavior original) {
        if (original == SortBehavior.OFF) {
            return original;
        }
        return SortBehavior.DYNAMIC_DEFER_NEARBY_ONE_FRAME;
    }
}
