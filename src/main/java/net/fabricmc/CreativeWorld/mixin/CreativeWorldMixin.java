package net.fabricmc.CreativeWorld.mixin;

import net.fabricmc.CreativeWorld.CreativeWorld;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class CreativeWorldMixin {
	@Inject(at = @At("HEAD"), method = "init()V")
	private void init(CallbackInfo info) {
		CreativeWorld.LOGGER.info("This line is printed by an example mod mixin!");
	}
}
