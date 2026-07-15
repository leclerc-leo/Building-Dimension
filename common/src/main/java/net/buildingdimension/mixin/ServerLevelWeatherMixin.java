package net.buildingdimension.mixin;

import net.buildingdimension.dimension.BuildingDimensionWeather;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects {@link ServerLevel#getWeatherData()} to a per-level override for building dimensions
 * when {@code buildingDimensionWeather} is configured to something other than {@code NORMAL} — see
 * {@link BuildingDimensionWeather} for why this is necessary (weather is server-wide in vanilla).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWeatherMixin {

    @Inject(method = "getWeatherData", at = @At("HEAD"), cancellable = true)
    private void buildingDimension$overrideWeather(CallbackInfoReturnable<WeatherData> cir) {
        WeatherData override = BuildingDimensionWeather.overrideFor((ServerLevel) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
