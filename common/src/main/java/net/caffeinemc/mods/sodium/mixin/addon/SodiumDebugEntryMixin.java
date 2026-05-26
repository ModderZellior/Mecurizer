package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.gui.SodiumDebugEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SodiumDebugEntry.class)
public class SodiumDebugEntryMixin {

    @ModifyArg(
            method = "display",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addToGroup(Lnet/minecraft/resources/Identifier;Ljava/lang/String;)V"),
            index = 1)
    private String modifyDebugLine(String text) {
        return text.replace("Sodium Renderer", "Mercurizer Renderer");
    }
}
