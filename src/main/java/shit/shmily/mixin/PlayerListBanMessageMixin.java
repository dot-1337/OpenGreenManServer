package shit.shmily.mixin;

import shit.shmily.GreenManServer;
import shit.shmily.punishment.GreenManBanCountService;
import shit.shmily.punishment.GreenManPunishmentService;
import java.net.SocketAddress;
import net.minecraft.server.BannedIpEntry;
import net.minecraft.server.BannedIpList;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerManager.class})
public abstract class PlayerListBanMessageMixin {
   @Inject(
      method = {"checkCanJoin"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenman$replaceUserBanMessage(SocketAddress remoteAddress, PlayerConfigEntry playerIdentity, CallbackInfoReturnable<Text> callbackInfo) {
      if (playerIdentity != null) {
         PlayerManager playerList = (PlayerManager)(Object)this;
         BannedPlayerList userBanList = playerList.getUserBanList();
         if (userBanList != null && userBanList.contains(playerIdentity)) {
            BannedPlayerEntry banListEntry;
            try {
               banListEntry = (BannedPlayerEntry)userBanList.get(playerIdentity);
            } catch (RuntimeException var9) {
               GreenManServer.LOGGER.warn("读取玩家 {} 的封禁模板数据失败，将使用原版封禁界面", playerIdentity.name(), var9);
               return;
            }

            if (banListEntry != null) {
               int banCount = Math.max(1, GreenManBanCountService.getBanCount(playerIdentity.id()));
               Text renderedBanMessage = GreenManPunishmentService.renderBanMessage(
                  banListEntry.getReason(), banListEntry.getExpiryDate(), banListEntry.getSource(), banCount, playerIdentity.name()
               );
               if (renderedBanMessage != null) {
                  callbackInfo.setReturnValue(renderedBanMessage);
               }
            }
         } else {
            BannedIpList ipBanList = playerList.getIpBanList();
            if (ipBanList != null && remoteAddress != null && ipBanList.isBanned(remoteAddress)) {
               BannedIpEntry ipBanEntry;
               try {
                  ipBanEntry = ipBanList.get(remoteAddress);
               } catch (RuntimeException var10) {
                  GreenManServer.LOGGER.warn("读取IP封禁模板数据失败，将使用原版IP封禁界面", var10);
                  return;
               }

               if (ipBanEntry != null) {
                  Text renderedIpBanMessage = GreenManPunishmentService.renderBanMessage(
                     "您的IP已被封禁", ipBanEntry.getExpiryDate(), ipBanEntry.getSource(), 0, playerIdentity.name()
                  );
                  if (renderedIpBanMessage != null) {
                     callbackInfo.setReturnValue(renderedIpBanMessage);
                  }
               }
            }
         }
      }
   }
}
