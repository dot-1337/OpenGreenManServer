package shit.shmily.mixin;

import shit.shmily.config.GreenManServerConfig;
import shit.shmily.title.PlayerTitleTeamManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerPlayerEntity.class})
public abstract class PlayerInfoUpdatePacketMixin {
   @Inject(
      method = {"getPlayerListName"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void addTitleToTabListName(CallbackInfoReturnable<Text> callbackInfo) {
      ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
      if (player != null) {
         boolean titleEnabled = GreenManServerConfig.isFeatureEnabled("title");
         boolean latencyEnabled = GreenManServerConfig.isFeatureEnabled("latencyDisplay");
         if (titleEnabled || latencyEnabled) {
            String latencyText = PlayerTitleTeamManager.getDisplayLatencyText(player);
            MutableText tabDisplayName = GreenManServerConfig.getTitleComponent(player)
               .append(Text.literal(player.getName().getString()).formatted(Formatting.WHITE));
            if (latencyEnabled) {
               tabDisplayName.append(Text.literal(" ").formatted(Formatting.GRAY))
                  .append(Text.literal("(" + latencyText + ")").formatted(Formatting.GRAY));
            }

            callbackInfo.setReturnValue(tabDisplayName);
         }
      }
   }
}
