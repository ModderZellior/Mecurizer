package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.widgets.DonationButtonWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public class VideoSettingsScreenMixin {
    @Shadow
    private DonationButtonWidget donateButton;

    @ModifyArg(
            method = "<init>(Lnet/minecraft/client/gui/screens/Screen;Lnet/caffeinemc/mods/sodium/client/config/structure/OptionPage;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;<init>(Lnet/minecraft/network/chat/Component;)V"))
    private static Component modifyTitle(Component title) {
        return Component.literal("Mercurizer Video Settings");
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hideDonateButton(CallbackInfo ci) {
        this.donateButton.updateDisplay((VideoSettingsScreen) (Object) this, false);
    }

    @Inject(method = "openDonationPrompt", at = @At("HEAD"), cancellable = true)
    private void cancelDonationPrompt(SodiumOptions options, CallbackInfo ci) {
        ci.cancel();
    }
}
