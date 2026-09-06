package shit.shmily.mixin;

import shit.shmily.anticheat.GreenManVanillaAnticheatController;
import shit.shmily.config.GreenManServerConfig;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerPlayNetworkHandler.class})
public abstract class ElytraMovementCheckMixin {
   @Shadow
   private boolean floating;
   @Shadow
   private int floatingTicks;
   @Shadow
   private boolean vehicleFloating;
   @Shadow
   private int vehicleFloatingTicks;

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void greenmanserver$disableVanillaFloatingKick(CallbackInfo callbackInfo) {
      if (GreenManVanillaAnticheatController.isGrimLoaded()) {
         this.floating = false;
         this.floatingTicks = 0;
         this.vehicleFloating = false;
         this.vehicleFloatingTicks = 0;
      }
   }

   @Inject(
      method = {"shouldCheckMovement"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$skipElytraSpeedRollback(boolean isFallFlying, CallbackInfoReturnable<Boolean> callbackInfo) {
      if (GreenManVanillaAnticheatController.isGrimLoaded()) {
         callbackInfo.setReturnValue(false);
      } else if (isFallFlying && GreenManServerConfig.isElytraMovementCheckBypassEnabled()) {
         callbackInfo.setReturnValue(false);
      }
   }

   @ModifyConstant(
      method = {"onPlayerMove"},
      constant = {@Constant(
         doubleValue = 0.0625
      )},
      require = 1
   )
   private double greenmanserver$disableVanillaPlayerMovedWrongly(double originalThreshold) {
      return !GreenManVanillaAnticheatController.isGrimLoaded() ? originalThreshold : Double.MAX_VALUE;
   }

   @ModifyConstant(
      method = {"onVehicleMove"},
      constant = {@Constant(
         doubleValue = 100.0
      )},
      require = 1
   )
   private double greenmanserver$disableVanillaVehicleMovedTooQuickly(double originalThreshold) {
      return !GreenManVanillaAnticheatController.isGrimLoaded() ? originalThreshold : Double.MAX_VALUE;
   }

   @ModifyConstant(
      method = {"onVehicleMove"},
      constant = {@Constant(
         doubleValue = 0.0625
      )},
      require = 1
   )
   private double greenmanserver$disableVanillaVehicleMovedWrongly(double originalThreshold) {
      return !GreenManVanillaAnticheatController.isGrimLoaded() ? originalThreshold : Double.MAX_VALUE;
   }
}
