package shit.shmily.mixin;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {TntEntity.class},
   priority = 1
)
public abstract class VanillaPrimedTntMixin {
   @Shadow
   private float explosionPower;

   @Shadow
   public abstract void setFuse(int var1);

   @Shadow
   public abstract int getFuse();

   @Inject(
      method = {"<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/entity/LivingEntity;)V"},
      at = {@At("RETURN")}
   )
   private void greenmanserver$restoreVanillaTntDefaults(World level, double x, double y, double z, LivingEntity owner, CallbackInfo callbackInfo) {
      if (GreenManServerConfig.isFeatureEnabled("vanillaRedstone")) {
         if (GreenManServerConfig.isFeatureEnabled("debugLogging") && (this.explosionPower != 4.0F || this.getFuse() != 80)) {
            GreenManServer.LOGGER.info("[debug] 拦截原版 TNT 参数修改：爆炸威力原始值={}，引信原始值={}tick，修正为4.0和80tick", this.explosionPower, this.getFuse());
         }

         this.setFuse(80);
         this.explosionPower = 4.0F;
      }
   }
}
