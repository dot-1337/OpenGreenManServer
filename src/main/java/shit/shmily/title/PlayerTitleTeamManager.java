package shit.shmily.title;

import shit.shmily.config.GreenManServerConfig;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class PlayerTitleTeamManager {
   private static final Map<UUID, Set<UUID>> CLIENT_ACTIVE_TITLE_TEAMS = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> DISPLAY_LATENCY_MILLISECONDS = new ConcurrentHashMap<>();
   private static final Set<UUID> MEASURED_LATENCY_PLAYERS = ConcurrentHashMap.newKeySet();

   private PlayerTitleTeamManager() {
   }

   public static void syncForAll(ServerPlayerEntity targetPlayer) {
      if (targetPlayer != null && targetPlayer.getEntityWorld().getServer() != null) {
         MinecraftServer server = targetPlayer.getEntityWorld().getServer();
         UUID targetUuid = targetPlayer.getUuid();
         if (!isHeadNameDecorationEnabled()) {
            removeTeamFromAllReceivers(server, targetPlayer);
            DISPLAY_LATENCY_MILLISECONDS.remove(targetUuid);
         } else {
            if (GreenManServerConfig.isFeatureEnabled("latencyDisplay")) {
               DISPLAY_LATENCY_MILLISECONDS.put(targetUuid, readStableLatency(targetPlayer));
            } else {
               DISPLAY_LATENCY_MILLISECONDS.remove(targetUuid);
            }

            for (ServerPlayerEntity receiver : server.getPlayerManager().getPlayerList()) {
               syncTeamToReceiver(receiver, targetPlayer);
            }
         }
      }
   }

   public static void syncExistingTitlesTo(ServerPlayerEntity receiver) {
      if (receiver != null && receiver.networkHandler != null && receiver.getEntityWorld().getServer() != null) {
         if (isHeadNameDecorationEnabled()) {
            for (ServerPlayerEntity targetPlayer : receiver.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
               if (!targetPlayer.getUuid().equals(receiver.getUuid())) {
                  syncTeamToReceiver(receiver, targetPlayer);
               }
            }
         }
      }
   }

   public static void removeForAll(ServerPlayerEntity targetPlayer) {
      if (targetPlayer != null && targetPlayer.getEntityWorld().getServer() != null) {
         removeTeamFromAllReceivers(targetPlayer.getEntityWorld().getServer(), targetPlayer);
         CLIENT_ACTIVE_TITLE_TEAMS.remove(targetPlayer.getUuid());
         DISPLAY_LATENCY_MILLISECONDS.remove(targetPlayer.getUuid());
         MEASURED_LATENCY_PLAYERS.remove(targetPlayer.getUuid());
      }
   }

   private static void syncTeamToReceiver(ServerPlayerEntity receiver, ServerPlayerEntity targetPlayer) {
      if (receiver != null && targetPlayer != null && receiver.networkHandler != null) {
         UUID receiverUuid = receiver.getUuid();
         Set<UUID> activeTargetUuids = CLIENT_ACTIVE_TITLE_TEAMS.computeIfAbsent(receiverUuid, unusedReceiverUuid -> ConcurrentHashMap.newKeySet());
         boolean shouldCreateTeam = activeTargetUuids.add(targetPlayer.getUuid());
         receiver.networkHandler.sendPacket(createTeamPacket(targetPlayer, shouldCreateTeam));
      }
   }

   private static void removeTeamFromAllReceivers(MinecraftServer server, ServerPlayerEntity targetPlayer) {
      if (server != null && targetPlayer != null) {
         UUID targetUuid = targetPlayer.getUuid();

         for (ServerPlayerEntity receiver : server.getPlayerManager().getPlayerList()) {
            if (receiver != null && receiver.networkHandler != null) {
               Set<UUID> activeTargetUuids = CLIENT_ACTIVE_TITLE_TEAMS.get(receiver.getUuid());
               if (activeTargetUuids != null && activeTargetUuids.remove(targetUuid)) {
                  receiver.networkHandler.sendPacket(createRemovePacket(targetPlayer));
                  if (activeTargetUuids.isEmpty()) {
                     CLIENT_ACTIVE_TITLE_TEAMS.remove(receiver.getUuid(), activeTargetUuids);
                  }
               }
            }
         }
      }
   }

   public static void recordMeasuredLatency(ServerPlayerEntity targetPlayer) {
      if (targetPlayer != null && targetPlayer.networkHandler != null) {
         MEASURED_LATENCY_PLAYERS.add(targetPlayer.getUuid());
         DISPLAY_LATENCY_MILLISECONDS.put(targetPlayer.getUuid(), Math.max(0, targetPlayer.networkHandler.getLatency()));
      }
   }

   public static String getDisplayLatencyText(ServerPlayerEntity targetPlayer) {
      if (targetPlayer != null && targetPlayer.networkHandler != null) {
         int cachedLatency = DISPLAY_LATENCY_MILLISECONDS.getOrDefault(targetPlayer.getUuid(), -1);
         if (cachedLatency < 0) {
            cachedLatency = readStableLatency(targetPlayer);
            DISPLAY_LATENCY_MILLISECONDS.put(targetPlayer.getUuid(), cachedLatency);
         }

         return cachedLatency < 0 ? "?ms" : cachedLatency + "ms";
      } else {
         return "?ms";
      }
   }

   private static int readStableLatency(ServerPlayerEntity targetPlayer) {
      if (targetPlayer != null && targetPlayer.networkHandler != null) {
         if (!MEASURED_LATENCY_PLAYERS.contains(targetPlayer.getUuid())) {
            return -1;
         } else {
            int measuredLatency = targetPlayer.networkHandler.getLatency();
            return measuredLatency < 0 ? -1 : measuredLatency;
         }
      } else {
         return -1;
      }
   }

   private static TeamS2CPacket createTeamPacket(ServerPlayerEntity targetPlayer, boolean addPlayers) {
      Scoreboard detachedScoreboard = new Scoreboard();
      Team virtualTeam = detachedScoreboard.addTeam(createTeamName(targetPlayer));
      Team realTeam = targetPlayer.getScoreboardTeam();
      MutableText prefix = GreenManServerConfig.getTitleComponent(targetPlayer);
      if (realTeam != null) {
         prefix.append(realTeam.getPrefix().copy());
         virtualTeam.setSuffix(createPingSuffix(targetPlayer).append(realTeam.getSuffix().copy()));
         virtualTeam.setColor(realTeam.getColor());
         virtualTeam.setNameTagVisibilityRule(realTeam.getNameTagVisibilityRule());
         virtualTeam.setDeathMessageVisibilityRule(realTeam.getDeathMessageVisibilityRule());
         virtualTeam.setCollisionRule(realTeam.getCollisionRule());
         virtualTeam.setFriendlyFireAllowed(realTeam.isFriendlyFireAllowed());
         virtualTeam.setShowFriendlyInvisibles(realTeam.shouldShowFriendlyInvisibles());
      } else {
         virtualTeam.setSuffix(createPingSuffix(targetPlayer));
         virtualTeam.setColor(Formatting.WHITE);
         virtualTeam.setNameTagVisibilityRule(VisibilityRule.ALWAYS);
      }

      virtualTeam.setPrefix(prefix);
      detachedScoreboard.addScoreHolderToTeam(targetPlayer.getNameForScoreboard(), virtualTeam);
      return TeamS2CPacket.updateTeam(virtualTeam, addPlayers);
   }

   private static MutableText createPingSuffix(ServerPlayerEntity targetPlayer) {
      if (!GreenManServerConfig.isFeatureEnabled("latencyDisplay")) {
         return Text.empty();
      } else {
         String latencyText = getDisplayLatencyText(targetPlayer);
         return Text.literal(" [" + latencyText + "]").formatted(Formatting.GRAY);
      }
   }

   private static TeamS2CPacket createRemovePacket(ServerPlayerEntity targetPlayer) {
      Team virtualTeam = new Team(new Scoreboard(), createTeamName(targetPlayer));
      return TeamS2CPacket.updateRemovedTeam(virtualTeam);
   }

   private static String createTeamName(ServerPlayerEntity targetPlayer) {
      return "gms" + targetPlayer.getUuid().toString().replace("-", "").substring(0, 13);
   }

   private static boolean isHeadNameDecorationEnabled() {
      return GreenManServerConfig.isFeatureEnabled("title") || GreenManServerConfig.isFeatureEnabled("latencyDisplay");
   }
}
