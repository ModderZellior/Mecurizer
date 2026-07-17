package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerInfoScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MercurizerPauseScreenMixin extends Screen {
    protected MercurizerPauseScreenMixin() { super(null); }

    @Inject(method = "init", at = @At("TAIL"))
    private void addMercurizerButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        SpriteIconButton btn = SpriteIconButton.builder(
                Component.literal("Mercurizer Settings"), b -> Minecraft.getInstance().setScreen(new MercurizerInfoScreen(self)), true)
                .sprite(Identifier.fromNamespaceAndPath("mercurizer", "icon/mercurizer"), 15, 15)
                .size(20, 20)
                .build();

        // Find the Options row (98×20 buttons) and place beside it
        java.util.OptionalInt optionsRowY = this.children().stream()
                .filter(c -> c instanceof AbstractWidget w && w.getWidth() == 98 && w.getHeight() == 20)
                .mapToInt(c -> ((AbstractWidget) c).getY())
                .max();
        int btnY  = optionsRowY.orElse(this.height / 4 + 78);
        int leftX = this.children().stream()
                .filter(c -> c instanceof AbstractWidget w && w.getWidth() == 98 && w.getHeight() == 20 && w.getY() == btnY)
                .mapToInt(c -> ((AbstractWidget) c).getX())
                .min()
                .orElse(this.width / 2 - 102);

        btn.setPosition(leftX - 24, btnY);
        addRenderableWidget(btn);
    }
}
