package shit.shmily.itemclear;

import shit.shmily.config.GreenManServerConfig;
import shit.shmily.text.GreenManTextFormatter;
import java.util.ArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class GreenManItemClearService {
   private static long lastClearTick = 0L;
   private static long countdownEndTick = 0L;
   private static int countdownStartSeconds = 0;
   private static int lastAnnouncedCountdownSecond = -1;
   private static boolean registered = false;

   private GreenManItemClearService() {
   }

   public static synchronized void register() {
      if (!registered) {
         registered = true;
         ServerTickEvents.END_SERVER_TICK.register(GreenManItemClearService::tick);
      }
   }

   private static void tick(MinecraftServer server) {
      if (server != null && GreenManServerConfig.isFeatureEnabled("itemClear")) {
         long currentTick = server.getTicks();
         if (currentTick < lastClearTick) {
            lastClearTick = currentTick;
            countdownEndTick = 0L;
            countdownStartSeconds = 0;
         }

         if (countdownEndTick > 0L) {
            handleCountdown(server, currentTick);
         } else {
            long intervalTicks = Math.min(GreenManServerConfig.getItemClearIntervalMinutes() * 60L * 20L, Long.MAX_VALUE);
            if (lastClearTick == 0L) {
               lastClearTick = currentTick;
            } else if (currentTick - lastClearTick >= intervalTicks) {
               long countdownTicks = Math.max(1L, GreenManServerConfig.getItemClearCountdownSeconds() * 20L);
               countdownStartSeconds = GreenManServerConfig.getItemClearCountdownSeconds();
               countdownEndTick = currentTick + countdownTicks;
               lastAnnouncedCountdownSecond = -1;
               handleCountdown(server, currentTick);
            }
         }
      } else {
         countdownEndTick = 0L;
         countdownStartSeconds = 0;
         lastAnnouncedCountdownSecond = -1;
      }
   }

   private static void handleCountdown(MinecraftServer server, long currentTick) {
      long remainingTicks = countdownEndTick - currentTick;
      if (remainingTicks <= 0L) {
         int removedItemCount = clearDroppedItems(server);
         lastClearTick = currentTick;
         countdownEndTick = 0L;
         countdownStartSeconds = 0;
         lastAnnouncedCountdownSecond = -1;
         broadcast(server, "&a掉落物清除完成，共清除 &e" + removedItemCount + " &a个掉落物");
      } else {
         int remainingSeconds = (int)Math.min(60L, (remainingTicks + 19L) / 20L);
         if (shouldAnnounceCountdown(remainingSeconds, countdownStartSeconds) && remainingSeconds != lastAnnouncedCountdownSecond) {
            lastAnnouncedCountdownSecond = remainingSeconds;
            broadcast(server, "&e将在 &c" + remainingSeconds + " &e秒后清除地面掉落物");
         }
      }
   }

   private static boolean shouldAnnounceCountdown(int remainingSeconds, int initialSeconds) {
      if (remainingSeconds <= 0 || initialSeconds <= 0) {
         return false;
      } else if (remainingSeconds <= 3) {
         return true;
      } else if (remainingSeconds != 5 && remainingSeconds != 10) {
         if (initialSeconds <= 20) {
            return remainingSeconds == initialSeconds;
         } else {
            for (int halfSeconds = initialSeconds / 2; halfSeconds > 10; halfSeconds /= 2) {
               if (remainingSeconds == halfSeconds) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return true;
      }
   }

   private static int clearDroppedItems(MinecraftServer server) {
      if (server == null) {
         return 0;
      } else {
         int removedItemCount = 0;

         for (ServerWorld serverLevel : server.getWorlds()) {
            if (serverLevel != null) {
               for (Object rawItemEntity : new ArrayList<>(serverLevel.getEntitiesByType(EntityType.ITEM, ignoredItemEntity -> true))) {
                   ItemEntity itemEntity = (ItemEntity) rawItemEntity;
                  if (itemEntity != null && itemEntity.isAlive()) {
                     itemEntity.discard();
                     removedItemCount++;
                  }
               }
            }
         }

         return removedItemCount;
      }
   }

   private static void broadcast(MinecraftServer server, String rawMessage) {
      if (server != null && rawMessage != null && !rawMessage.isBlank() && !server.getPlayerManager().getPlayerList().isEmpty()) {
         Text message = GreenManTextFormatter.parseRainbow(rawMessage, Style.EMPTY);

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player != null && player.networkHandler != null) {
               player.sendMessage(message);
            }
         }
      }
   }

   public static boolean startManualCountdown(MinecraftServer server) {
      if (server != null && GreenManServerConfig.isFeatureEnabled("itemClear")) {
         countdownEndTick = server.getTicks() + Math.max(1L, GreenManServerConfig.getItemClearCountdownSeconds() * 20L);
         countdownStartSeconds = GreenManServerConfig.getItemClearCountdownSeconds();
         lastAnnouncedCountdownSecond = -1;
         return true;
      } else {
         return false;
      }
   }

   public static String getStatus() {
      return "掉落物清除="
         + (GreenManServerConfig.isFeatureEnabled("itemClear") ? "开启" : "关闭")
         + "，间隔="
         + GreenManServerConfig.getItemClearIntervalMinutes()
         + "分钟，倒计时="
         + GreenManServerConfig.getItemClearCountdownSeconds()
         + "秒";
   }
}
