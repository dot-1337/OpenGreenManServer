package shit.shmily;

import shit.shmily.announcement.GreenManAnnouncementService;
import shit.shmily.announcement.GreenManScheduledAnnouncementService;
import shit.shmily.chat.GreenManChatArchive;
import shit.shmily.chat.GreenManChatHistory;
import shit.shmily.chat.GreenManChatLimiter;
import shit.shmily.chat.GreenManMentionService;
import shit.shmily.command.GreenManServerCommands;
import shit.shmily.config.GreenManBanWhitelistConfig;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.config.GreenManTweakerooWhitelistConfig;
import shit.shmily.itemclear.GreenManItemClearService;
import shit.shmily.join.GreenManJoinService;
import shit.shmily.join.GreenManJoinSoundScheduler;
import shit.shmily.join.GreenManPlayerWelcomeConfig;
import shit.shmily.memory.GreenManMemoryManager;
import shit.shmily.music.GreenManMusicService;
import shit.shmily.network.GreenManNetworkCheckBlocker;
import shit.shmily.performance.GreenManBackgroundTaskManager;
import shit.shmily.performance.GreenManChunkIoManager;
import shit.shmily.punishment.GreenManAnticheatService;
import shit.shmily.punishment.GreenManMuteService;
import shit.shmily.title.PlayerTitleTeamManager;
import shit.shmily.vote.GreenManVoteService;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreenManServer implements ModInitializer {
   public static final String MOD_ID = "greenmanserver";
   private static final long TAB_UPDATE_INTERVAL_NANOS = 1000000000L;
   private static long lastTabUpdateNanos = 0L;
   private static final long PERFORMANCE_WINDOW_NANOS = 10000000000L;
   private static final int MAX_PERFORMANCE_SAMPLES = 10000;
   private static long currentTickStartNanos = 0L;
   private static final Deque<GreenManServer.TickTimingSample> PERFORMANCE_SAMPLES = new ArrayDeque<>();
   private static long performanceDurationSumNanos = 0L;
   private static MinecraftServer performanceServer;
   private static double measuredTicksPerSecond = 20.0;
   private static double measuredMillisecondsPerTick = 50.0;
   private static final DateTimeFormatter CHINA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
   private static long serverStartTimeMillis = 0L;
   public static final Logger LOGGER = LoggerFactory.getLogger("greenmanserver");

   public void onInitialize() {
      if (serverStartTimeMillis == 0L) {
         serverStartTimeMillis = System.currentTimeMillis();
      }

      GreenManBanWhitelistConfig.load();
      GreenManTweakerooWhitelistConfig.load();
      GreenManServerConfig.load();
      GreenManPlayerWelcomeConfig.load();
      GreenManPlayerWelcomeConfig.syncAllowedPlayerTexts(GreenManServerConfig.getJoinMessageAllowedPlayerUuids());
      GreenManMusicService.initialize();
      GreenManJoinSoundScheduler.register();
      GreenManNetworkCheckBlocker.applyRuntimeConfig();
      GreenManBackgroundTaskManager.register();
      GreenManChunkIoManager.register();
      GreenManAnnouncementService.register();
      GreenManScheduledAnnouncementService.register();
      GreenManServerCommands.register();
      GreenManMuteService.register();
      GreenManAnticheatService.initialize();
      GreenManMentionService.register();
      GreenManVoteService.register();
      GreenManChatLimiter.register();
      GreenManChatHistory.register();
      GreenManChatArchive.register();
      GreenManItemClearService.register();
      ServerTickEvents.START_SERVER_TICK.register(GreenManServer::startPerformanceSample);
      ServerTickEvents.END_SERVER_TICK.register(GreenManServer::finishPerformanceSample);
      ServerTickEvents.END_SERVER_TICK.register(GreenManServer::updateTabList);
      ServerTickEvents.END_SERVER_TICK.register(GreenManMemoryManager::tick);
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         ServerPlayerEntity joinedPlayer = handler.getPlayer();
         refreshPlayerTabName(joinedPlayer);
         PlayerTitleTeamManager.syncExistingTitlesTo(joinedPlayer);
         GreenManMentionService.handlePlayerJoin(joinedPlayer);
         GreenManChatHistory.handlePlayerJoin(joinedPlayer);
         GreenManAnnouncementService.handlePlayerJoin(joinedPlayer);
         GreenManJoinService.handlePlayerJoin(joinedPlayer);
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         GreenManMentionService.handlePlayerDisconnect(handler.getPlayer());
         PlayerTitleTeamManager.removeForAll(handler.getPlayer());
         GreenManChatLimiter.removePlayer(handler.getPlayer());
      });
      LOGGER.info("GreenManServer 服务端功能已初始化");
   }

   public static void refreshPlayerTabName(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         PlayerTitleTeamManager.syncForAll(player);
         PlayerListS2CPacket tabDisplayNamePacket = new PlayerListS2CPacket(Action.UPDATE_DISPLAY_NAME, player);
         player.getEntityWorld().getServer().getPlayerManager().sendToAll(tabDisplayNamePacket);
      }
   }

   public static void refreshAllPlayerTabNames(MinecraftServer server) {
      if (server != null) {
         for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            refreshPlayerTabName(onlinePlayer);
         }
      }
   }

   private static void updateTabList(MinecraftServer server) {
      long currentTimeNanos = System.nanoTime();
      if (lastTabUpdateNanos == 0L || currentTimeNanos - lastTabUpdateNanos >= 1000000000L) {
         lastTabUpdateNanos = currentTimeNanos;
         List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
         if (!onlinePlayers.isEmpty()) {
            if (GreenManServerConfig.isFeatureEnabled("tabStatus")) {
               Text header = buildHeader(server);
               Text footer = buildFooter(onlinePlayers);
               PlayerListHeaderS2CPacket tabListPacket = new PlayerListHeaderS2CPacket(header, footer);
               server.getPlayerManager().sendToAll(tabListPacket);
            } else {
               server.getPlayerManager().sendToAll(new PlayerListHeaderS2CPacket(Text.empty(), Text.empty()));
            }

            for (ServerPlayerEntity onlinePlayer : onlinePlayers) {
               PlayerTitleTeamManager.syncForAll(onlinePlayer);
            }

            PlayerListS2CPacket playerDisplayNamesPacket = new PlayerListS2CPacket(EnumSet.of(Action.UPDATE_DISPLAY_NAME), onlinePlayers);
            server.getPlayerManager().sendToAll(playerDisplayNamesPacket);
         }
      }
   }

   private static void startPerformanceSample(MinecraftServer server) {
      if (server != null) {
         if (performanceServer != server) {
            resetPerformanceSamples(server);
         }

         currentTickStartNanos = System.nanoTime();
      }
   }

   private static void finishPerformanceSample(MinecraftServer server) {
      if (server != null && currentTickStartNanos != 0L) {
         long currentTickEndNanos = System.nanoTime();
         long tickStartNanos = currentTickStartNanos;
         currentTickStartNanos = 0L;
         if (currentTickEndNanos > tickStartNanos) {
            if (performanceServer != server) {
               resetPerformanceSamples(server);
            }

            long tickDurationNanos = currentTickEndNanos - tickStartNanos;
            GreenManServer.TickTimingSample timingSample = new GreenManServer.TickTimingSample(tickStartNanos, currentTickEndNanos, tickDurationNanos);
            PERFORMANCE_SAMPLES.addLast(timingSample);
            performanceDurationSumNanos = safeAddNanos(performanceDurationSumNanos, tickDurationNanos);

            while (PERFORMANCE_SAMPLES.size() > 10000) {
               removeOldestPerformanceSample();
            }

            long performanceWindowCutoffNanos = currentTickEndNanos - 10000000000L;

            while (!PERFORMANCE_SAMPLES.isEmpty() && PERFORMANCE_SAMPLES.peekFirst().endNanos() < performanceWindowCutoffNanos) {
               removeOldestPerformanceSample();
            }

            if (PERFORMANCE_SAMPLES.size() >= 2) {
               GreenManServer.TickTimingSample oldestSample = PERFORMANCE_SAMPLES.peekFirst();
               GreenManServer.TickTimingSample newestSample = PERFORMANCE_SAMPLES.peekLast();
               long elapsedWindowNanos = newestSample.endNanos() - oldestSample.endNanos();
               if (elapsedWindowNanos > 0L && performanceDurationSumNanos >= 0L) {
                  long measuredIntervalCount = Math.max(1L, PERFORMANCE_SAMPLES.size() - 1L);
                  double calculatedTicksPerSecond = measuredIntervalCount * 1.0E9 / elapsedWindowNanos;
                  double calculatedMillisecondsPerTick = (double)performanceDurationSumNanos / PERFORMANCE_SAMPLES.size() / 1000000.0;
                  if (Double.isFinite(calculatedTicksPerSecond) && calculatedTicksPerSecond >= 0.0) {
                     measuredTicksPerSecond = calculatedTicksPerSecond;
                  }

                  if (Double.isFinite(calculatedMillisecondsPerTick) && calculatedMillisecondsPerTick >= 0.0) {
                     measuredMillisecondsPerTick = calculatedMillisecondsPerTick;
                  }
               }
            }
         }
      }
   }

   private static void resetPerformanceSamples(MinecraftServer server) {
      performanceServer = server;
      PERFORMANCE_SAMPLES.clear();
      performanceDurationSumNanos = 0L;
      currentTickStartNanos = 0L;
      measuredTicksPerSecond = 20.0;
      measuredMillisecondsPerTick = 50.0;
   }

   private static void removeOldestPerformanceSample() {
      GreenManServer.TickTimingSample removedSample = PERFORMANCE_SAMPLES.pollFirst();
      if (removedSample != null) {
         performanceDurationSumNanos = Math.max(0L, performanceDurationSumNanos - removedSample.durationNanos());
      }
   }

   private static long safeAddNanos(long currentNanos, long additionalNanos) {
      if (additionalNanos < 0L) {
         return currentNanos;
      } else {
         return Long.MAX_VALUE - currentNanos < additionalNanos ? Long.MAX_VALUE : currentNanos + additionalNanos;
      }
   }

   private static Text buildHeader(MinecraftServer server) {
      double ticksPerSecond = sanitizePerformanceValue(measuredTicksPerSecond, 20.0);
      double millisecondsPerTick = sanitizePerformanceValue(measuredMillisecondsPerTick, 50.0);
      return Text.literal(GreenManServerConfig.getTabServerName())
         .formatted(Formatting.AQUA)
         .append(Text.literal("\n中国时间: ").formatted(Formatting.GRAY))
         .append(Text.literal(CHINA_TIME_FORMATTER.format(Instant.now())).formatted(Formatting.WHITE))
         .append(Text.literal("\n运行时间: ").formatted(Formatting.GRAY))
         .append(Text.literal(formatUptime()).formatted(Formatting.WHITE))
         .append(Text.literal("\nTPS: ").formatted(Formatting.GRAY))
         .append(Text.literal(String.format(Locale.ROOT, "%.2f", ticksPerSecond)).formatted(Formatting.GREEN))
         .append(Text.literal("  MSPT: ").formatted(Formatting.GRAY))
         .append(Text.literal(String.format(Locale.ROOT, "%.2f", millisecondsPerTick)).formatted(Formatting.GREEN));
   }

   private static double sanitizePerformanceValue(double value, double fallbackValue) {
      return Double.isFinite(value) && !(value < 0.0) ? value : fallbackValue;
   }

   private static Text buildFooter(List<ServerPlayerEntity> onlinePlayers) {
      MemoryUsage heapMemoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
      long usedBytes = Math.max(heapMemoryUsage.getUsed(), 0L);
      long maximumBytes = heapMemoryUsage.getMax();
      String memoryText = formatMemory(usedBytes, maximumBytes);
      return Text.literal("堆内存: ")
         .formatted(Formatting.GRAY)
         .append(Text.literal(memoryText).formatted(Formatting.WHITE))
         .append(Text.literal("\n在线玩家: ").formatted(Formatting.GRAY))
         .append(Text.literal(Integer.toString(onlinePlayers.size())).formatted(Formatting.WHITE));
   }

   private static String formatUptime() {
      long elapsedMilliseconds = Math.max(System.currentTimeMillis() - serverStartTimeMillis, 0L);
      Duration elapsedDuration = Duration.ofMillis(elapsedMilliseconds);
      long elapsedDays = elapsedDuration.toDays();
      long elapsedHours = elapsedDuration.toHoursPart();
      long elapsedMinutes = elapsedDuration.toMinutesPart();
      long elapsedSeconds = elapsedDuration.toSecondsPart();
      return String.format(Locale.ROOT, "%d天 %02d:%02d:%02d", elapsedDays, elapsedHours, elapsedMinutes, elapsedSeconds);
   }

   private static String formatMemory(long usedBytes, long maximumBytes) {
      double usedGib = usedBytes / 1.0737418E9F;
      if (maximumBytes <= 0L) {
         return String.format(Locale.ROOT, "%.2f GiB / 未知上限", usedGib);
      } else {
         double maximumGib = maximumBytes / 1.0737418E9F;
         double usagePercent = Math.min(100.0, Math.max(0.0, usedBytes * 100.0 / maximumBytes));
         return String.format(Locale.ROOT, "%.2f / %.2f GiB (%.1f%%)", usedGib, maximumGib, usagePercent);
      }
   }

   public static Identifier id(String path) {
      return Identifier.of("greenmanserver", path);
   }

   private record TickTimingSample(long startNanos, long endNanos, long durationNanos) {
   }
}
