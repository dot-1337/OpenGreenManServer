package shit.shmily.mixin;

import shit.shmily.config.GreenManServerConfig;
import shit.shmily.title.PlayerTitleTeamManager;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"net/minecraft/server/network/ServerCommonNetworkHandler"}
)
public abstract class ServerLatencyMixin {
   @Inject(
      method = {"onKeepAlive"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/server/network/ServerCommonNetworkHandler;latency:I",
         opcode = 181,
         shift = Shift.AFTER
      )}
   )
   private void recordSuccessfulLatencyMeasurement(KeepAliveC2SPacket packet, CallbackInfo callbackInfo) {
      if (GreenManServerConfig.isFeatureEnabled("latencyDisplay")) {
         if ((Object)this instanceof ServerPlayNetworkHandler gamePacketListener) {
            PlayerTitleTeamManager.recordMeasuredLatency(gamePacketListener.getPlayer());
         }
      }
   }

   @ModifyConstant(
      method = {"baseTick"},
      constant = {@Constant(
         longValue = 15000L
      )}
   )
   private long updateKeepAliveInterval(long originalIntervalMilliseconds) {
      if (!GreenManServerConfig.isFeatureEnabled("latencyDisplay")) {
         return originalIntervalMilliseconds;
      } else {
         return originalIntervalMilliseconds != 15000L ? originalIntervalMilliseconds : 1000L;
      }
   }
}
