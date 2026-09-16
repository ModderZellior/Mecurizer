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
                Component.literal("Mercurizer Settings"), b -> Minecraft.getInstance().setScreenAndShow(new MercurizerInfoScreen(self)), true)
                .sprite(Identifier.fromNamespaceAndPath("mercurizer", "icon/mercurizer"), 15, 15)
                .size(20, 20)
                .tooltip(Component.literal("Mercurizer"))
                .build();

        // Find the actual Y of the existing small icon buttons dynamically
        java.util.OptionalInt rowY = this.children().stream()
                .filter(c -> c instanceof AbstractWidget w && w.getWidth() == 20 && w.getHeight() == 20)
                .mapToInt(c -> ((AbstractWidget) c).getY())
                .findFirst();
        int iconRowY = rowY.orElse(this.height / 4 + 48);
        int leftmostX = this.children().stream()
                .filter(c -> c instanceof AbstractWidget w && w.getWidth() == 20 && w.getHeight() == 20 && w.getY() == iconRowY)
                .mapToInt(c -> ((AbstractWidget) c).getX())
                .min()
                .orElse(this.width / 2 - 84);

        btn.setPosition(leftmostX - 24, iconRowY);
        addRenderableWidget(btn);
    }
}
