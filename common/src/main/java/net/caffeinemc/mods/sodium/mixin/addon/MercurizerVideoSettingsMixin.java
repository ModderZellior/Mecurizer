package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VideoSettingsScreen.class)
public abstract class MercurizerVideoSettingsMixin extends Screen {
    protected MercurizerVideoSettingsMixin(Component title) {
        super(title);
    }
}
