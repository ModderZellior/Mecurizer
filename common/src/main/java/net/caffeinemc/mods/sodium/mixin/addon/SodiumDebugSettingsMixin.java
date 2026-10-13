package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SodiumGameOptions.DebugSettings.class)
public class SodiumDebugSettingsMixin {
    @Inject(method = "getSortBehavior", at = @At("RETURN"), cancellable = true)
    private void modifySortBehavior(CallbackInfoReturnable<SortBehavior> cir) {
        if (cir.getReturnValue() != SortBehavior.OFF) {
            cir.setReturnValue(SortBehavior.DYNAMIC_DEFER_NEARBY_ONE_FRAME);
        }
    }
}
