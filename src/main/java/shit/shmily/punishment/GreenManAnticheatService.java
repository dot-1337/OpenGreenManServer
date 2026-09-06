package shit.shmily.punishment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManBanWhitelistConfig;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.config.GreenManTweakerooWhitelistConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class GreenManAnticheatService {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path HISTORY_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-anticheat-history.json");
   private static final Path TEMP_HISTORY_PATH = HISTORY_PATH.resolveSibling("greenmanserver-anticheat-history.json.tmp");
   private static final Object HISTORY_LOCK = new Object();
   private static final Map<String, Integer> PUNISHMENT_COUNTS = new LinkedHashMap<>();
   private static final Map<String, Boolean> KICK_COMPLETED = new LinkedHashMap<>();
   private static final Map<String, Integer> STREAK_COUNTS = new LinkedHashMap<>();
   private static final Map<String, Long> LAST_STREAK_REPORT_NANOS = new LinkedHashMap<>();
   private static final Map<String, Long> LAST_BAN_WHITELIST_LOG_NANOS = new LinkedHashMap<>();
   private static final long STREAK_DUPLICATE_WINDOW_NANOS = 500000000L;
   private static final int CURRENT_HISTORY_VERSION = 2;
   private static final Set<String> TWEAKEROO_COMPATIBLE_CHECKS = Set.of(
      "safewalk",
      "simulation",
      "groundspoof",
      "rotationplace",
      "duplicaterotplace",
      "multiplace",
      "airliquidplace",
      "positionplace",
      "fabricatedplace",
      "farplace",
      "invalidplacea",
      "invalidplaceb",
      "multiactionsa",
      "multiactionsb",
      "multiactionsc",
      "multiactionsd",
      "multiactionse",
      "multiactionsf",
      "multiactionsg",
      "multiinteracta",
      "multiinteractb",
      "packetordera",
      "packetorderb",
      "packetorderc",
      "packetorderd",
      "packetordere",
      "packetorderf",
      "packetorderg",
      "packetorderh",
      "packetorderi",
      "packetorderj",
      "packetorderm",
      "packetordern",
      "packetordero",
      "post",
      "badpacketsg",
      "badpacketsj",
      "badpacketsu",
      "badpacketsx",
      "badpacketsz",
      "fastbreak",
      "airliquidbreak",
      "wrongbreak",
      "multibreak",
      "noswingbreak",
      "positionbreaka",
      "positionbreakb",
      "rotationbreak",
      "invalidbreak",
      "farbreak"
   );
   private static boolean historyLoaded;
   private static boolean lifecycleRegistered;
   private static volatile MinecraftServer activeServer;

   private GreenManAnticheatService() {
   }

   public static void initialize() {
      synchronized (HISTORY_LOCK) {
         if (!historyLoaded) {
            loadHistoryLocked();
         }

         if (lifecycleRegistered) {
            return;
         }

         lifecycleRegistered = true;
      }

      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> activeServer = server);
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         activeServer = null;
         synchronized (HISTORY_LOCK) {
            STREAK_COUNTS.clear();
            LAST_STREAK_REPORT_NANOS.clear();
            LAST_BAN_WHITELIST_LOG_NANOS.clear();
         }
      });
   }

   public static int recordStreakReport(String playerName, String checkName, int violationLevel, String verboseText) {
      MinecraftServer serverSnapshot = activeServer;
      if (serverSnapshot == null) {
         return -1;
      } else if (playerName != null && !playerName.isBlank()) {
         ServerPlayerEntity targetPlayer = serverSnapshot.getPlayerManager().getPlayer(playerName.trim());
         if (targetPlayer != null && targetPlayer.networkHandler != null) {
            ServerCommandSource consoleSource = serverSnapshot.getCommandSource();

            try {
               return recordStreakAction(consoleSource, targetPlayer.getUuid(), checkName, Math.max(0, Math.min(1000000, violationLevel)), verboseText);
            } catch (CommandSyntaxException var8) {
               GreenManServer.LOGGER
                  .error("Grim反作弊直接联动处理失败，玩家：{}，检测：{}", new Object[]{targetPlayer.getName().getString(), sanitizeSingleLine(checkName, 64), var8});
               return -4;
            }
         } else {
            return -3;
         }
      } else {
         return -2;
      }
   }

   public static boolean shouldSkipTweakerooAutomaticPunishment(String playerName, String playerUuid, String checkName) {
      if (checkName != null && !checkName.isBlank()) {
         String normalizedCheckName = normalizeTweakerooCheckName(checkName);
         if (!isTweakerooCompatibleCheck(normalizedCheckName)) {
            return false;
         } else {
            return GreenManTweakerooWhitelistConfig.containsPlayer(playerName) ? true : GreenManTweakerooWhitelistConfig.containsPlayer(playerUuid);
         }
      } else {
         return false;
      }
   }

   public static boolean isTweakerooWhitelistPlayer(String playerName, String playerUuid) {
      String normalizedPlayerName = playerName == null ? "" : playerName.trim();
      String normalizedPlayerUuid = playerUuid == null ? "" : playerUuid.trim();
      if (normalizedPlayerName.isEmpty() && normalizedPlayerUuid.isEmpty()) {
         return false;
      } else {
         return GreenManTweakerooWhitelistConfig.containsPlayer(normalizedPlayerName)
            ? true
            : GreenManTweakerooWhitelistConfig.containsPlayer(normalizedPlayerUuid);
      }
   }

   public static boolean shouldSuppressBanWhitelistOperatorAlert(String playerName, String playerUuid) {
      String normalizedPlayerName = playerName == null ? "" : playerName.trim();
      String normalizedPlayerUuid = playerUuid == null ? "" : playerUuid.trim();
      if (normalizedPlayerName.isEmpty() && normalizedPlayerUuid.isEmpty()) {
         return false;
      } else {
         for (String whitelistEntry : GreenManBanWhitelistConfig.getPlayers()) {
            if (whitelistEntry != null
               && !whitelistEntry.isBlank()
               && (
                  !normalizedPlayerName.isEmpty() && whitelistEntry.equalsIgnoreCase(normalizedPlayerName)
                     || !normalizedPlayerUuid.isEmpty() && whitelistEntry.equalsIgnoreCase(normalizedPlayerUuid)
               )) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean shouldSkipVanillaWhitelistAnticheat(String playerName, String playerUuid) {
      MinecraftServer serverSnapshot = activeServer;
      if (serverSnapshot == null) {
         return false;
      } else {
         String normalizedPlayerName = playerName == null ? "" : playerName.trim();
         UUID normalizedPlayerUuid = null;
         if (playerUuid != null && !playerUuid.isBlank()) {
            try {
               normalizedPlayerUuid = UUID.fromString(playerUuid.trim());
            } catch (IllegalArgumentException var6) {
               normalizedPlayerUuid = null;
            }
         }

         if (!normalizedPlayerName.isEmpty() && normalizedPlayerUuid != null) {
            PlayerConfigEntry targetIdentity = new PlayerConfigEntry(normalizedPlayerUuid, normalizedPlayerName);
            return serverSnapshot.getPlayerManager().getWhitelist().isAllowed(targetIdentity);
         } else {
            return false;
         }
      }
   }

   private static String normalizeTweakerooCheckName(String checkName) {
      if (checkName != null && !checkName.isBlank()) {
         String baseCheckName = checkName.replaceFirst("[\\s(\\[].*$", "");
         String normalizedCheckName = baseCheckName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
         int lastDotIndex = checkName.lastIndexOf(46);
         if (lastDotIndex >= 0 && lastDotIndex + 1 < checkName.length()) {
            String suffixName = checkName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!suffixName.isEmpty()) {
               return suffixName;
            }
         }

         return normalizedCheckName;
      } else {
         return "";
      }
   }

   private static boolean isTweakerooCompatibleCheck(String normalizedCheckName) {
      return normalizedCheckName == null || normalizedCheckName.isEmpty() ? false : TWEAKEROO_COMPATIBLE_CHECKS.contains(normalizedCheckName);
   }

   public static int punishFromGrim(ServerCommandSource commandSource, UUID playerId, String checkName, int violationLevel, String verboseText) throws CommandSyntaxException {
      if (commandSource != null && commandSource.getServer() != null && playerId != null) {
         if (!GreenManServerConfig.isGrimAnticheatEnabled()) {
            commandSource.sendError(Text.literal("Grim反作弊联动已关闭"));
            return 0;
         } else {
            ServerPlayerEntity targetPlayer = commandSource.getServer().getPlayerManager().getPlayer(playerId);
            if (targetPlayer != null && targetPlayer.networkHandler != null) {
               if (shouldSkipVanillaWhitelistAnticheat(targetPlayer.getName().getString(), targetPlayer.getUuid().toString())) {
                  return 1;
               } else if (isBanWhitelistPlayer(targetPlayer)) {
                  logBanWhitelistReport(targetPlayer, checkName, violationLevel, verboseText);
                  return 1;
               } else if (GreenManServerConfig.isGrimSafeWalkExemptEnabled() && checkName != null && checkName.trim().equalsIgnoreCase("safewalk")) {
                  notifyOnlineOperators(commandSource.getServer(), targetPlayer, checkName, violationLevel, verboseText, 1, true);
                  return 1;
               } else {
                  String normalizedIpAddress = normalizeRemoteAddress(targetPlayer.networkHandler.getConnectionAddress());
                  String historyKey = normalizedIpAddress == null ? "player:" + playerId : "ip:" + normalizedIpAddress;
                  int nextPunishmentCount;
                  synchronized (HISTORY_LOCK) {
                     if (!historyLoaded) {
                        loadHistoryLocked();
                     }

                     int previousPunishmentCount = Math.max(0, PUNISHMENT_COUNTS.getOrDefault(historyKey, 0));
                     nextPunishmentCount = previousPunishmentCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : previousPunishmentCount + 1;
                  }

                  boolean shouldPermanentlyBanIp = nextPunishmentCount >= GreenManServerConfig.getGrimPermanentIpBanAfterCount();
                  String durationText = shouldPermanentlyBanIp ? "forever" : GreenManServerConfig.getGrimBanDuration(nextPunishmentCount);
                  String punishmentReason = buildPunishmentReason(checkName, violationLevel, verboseText);
                  PlayerConfigEntry targetIdentity = new PlayerConfigEntry(targetPlayer.getUuid(), targetPlayer.getName().getString());
                  int bannedPlayerCount = GreenManPunishmentService.banPlayers(
                     commandSource, List.of(targetIdentity), durationText, punishmentReason, "Anticheat"
                  );
                  if (bannedPlayerCount <= 0) {
                     return bannedPlayerCount;
                  } else {
                     synchronized (HISTORY_LOCK) {
                        PUNISHMENT_COUNTS.put(historyKey, nextPunishmentCount);
                        if (!saveHistoryLocked()) {
                           commandSource.sendError(Text.literal("玩家已封禁，但反作弊IP次数保存失败，请检查日志"));
                        }
                     }

                     boolean ipBanWhitelisted = GreenManPunishmentService.isIpBanWhitelisted(normalizedIpAddress);
                     if (shouldPermanentlyBanIp && normalizedIpAddress != null && !ipBanWhitelisted) {
                        try {
                           GreenManPunishmentService.banIp(commandSource, normalizedIpAddress, "forever", punishmentReason, "Anticheat");
                        } catch (CommandSyntaxException var16) {
                           GreenManServer.LOGGER.error("Grim永久IP封禁失败，IP：{}", normalizedIpAddress, var16);
                           commandSource.sendError(Text.literal("玩家已永久封禁，但IP封禁失败，请检查日志"));
                        }
                     }

                     if (shouldPermanentlyBanIp && normalizedIpAddress != null && ipBanWhitelisted) {
                        GreenManServer.LOGGER.info("Grim已永久封禁玩家{}，IP {} 位于IP封禁白名单，已跳过banip", targetPlayer.getName().getString(), normalizedIpAddress);
                     }

                     if (shouldPermanentlyBanIp && normalizedIpAddress == null) {
                        GreenManServer.LOGGER.warn("Grim已永久封禁玩家{}，但连接IP不可识别，未执行banip", targetPlayer.getName().getString());
                     }

                     return bannedPlayerCount;
                  }
               }
            } else {
               commandSource.sendError(Text.literal("Grim处罚失败：目标玩家已经离线"));
               return 0;
            }
         }
      } else {
         if (commandSource != null) {
            commandSource.sendError(Text.literal("Grim处罚失败：服务器或玩家UUID无效"));
         }

         return 0;
      }
   }

   public static int recordStreakAction(ServerCommandSource commandSource, UUID playerId, String checkName, int violationLevel, String verboseText) throws CommandSyntaxException {
      if (commandSource != null && commandSource.getServer() != null && playerId != null) {
         if (!GreenManServerConfig.isGrimAnticheatEnabled()) {
            commandSource.sendError(Text.literal("Grim反作弊联动已关闭"));
            return 0;
         } else {
            ServerPlayerEntity targetPlayer = commandSource.getServer().getPlayerManager().getPlayer(playerId);
            if (targetPlayer != null && targetPlayer.networkHandler != null) {
               if (shouldSkipVanillaWhitelistAnticheat(targetPlayer.getName().getString(), targetPlayer.getUuid().toString())) {
                  return 1;
               } else if (isBanWhitelistPlayer(targetPlayer)) {
                  logBanWhitelistReport(targetPlayer, checkName, violationLevel, verboseText);
                  return 1;
               } else {
                  String normalizedCheckName = normalizeStreakCheckName(checkName);
                  String playerHistoryKey = createStreakKey(playerId, normalizedCheckName);
                  long currentReportNanos = System.nanoTime();
                  if (GreenManServerConfig.isGrimSafeWalkExemptEnabled() && checkName != null && checkName.trim().equalsIgnoreCase("safewalk")) {
                     notifyOnlineOperators(commandSource.getServer(), targetPlayer, checkName, violationLevel, verboseText, 1, true);
                     return 1;
                  } else {
                     boolean hasCompletedFirstKick;
                     int currentStreakCount;
                     synchronized (HISTORY_LOCK) {
                        if (!historyLoaded) {
                           loadHistoryLocked();
                        }

                        Long previousReportNanos = LAST_STREAK_REPORT_NANOS.get(playerHistoryKey);
                        if (previousReportNanos != null && currentReportNanos - previousReportNanos < 500000000L) {
                           return 0;
                        }

                        LAST_STREAK_REPORT_NANOS.put(playerHistoryKey, currentReportNanos);
                        hasCompletedFirstKick = Boolean.TRUE.equals(KICK_COMPLETED.get(playerHistoryKey));
                        int previousStreakCount = Math.max(0, STREAK_COUNTS.getOrDefault(playerHistoryKey, 0));
                        currentStreakCount = previousStreakCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : previousStreakCount + 1;
                        STREAK_COUNTS.put(playerHistoryKey, currentStreakCount);
                     }

                     int streakThreshold = GreenManServerConfig.getGrimStreakKickThreshold();
                     if (currentStreakCount <= 2 || currentStreakCount >= streakThreshold) {
                        GreenManServer.LOGGER
                           .info(
                              "Grim联动计数 玩家={} 检测={} 次数={}/{}",
                              new Object[]{targetPlayer.getName().getString(), sanitizeSingleLine(checkName, 64), currentStreakCount, streakThreshold}
                           );
                     }

                     if (currentStreakCount <= 2 || currentStreakCount >= streakThreshold) {
                        notifyOnlineOperators(commandSource.getServer(), targetPlayer, checkName, violationLevel, verboseText, currentStreakCount, false);
                     }

                     if (currentStreakCount < streakThreshold) {
                        return 0;
                     } else if (hasCompletedFirstKick) {
                        PlayerConfigEntry targetIdentity = new PlayerConfigEntry(targetPlayer.getUuid(), targetPlayer.getName().getString());
                        if (GreenManPunishmentService.isPlayerBanWhitelisted(targetIdentity)) {
                           synchronized (HISTORY_LOCK) {
                              STREAK_COUNTS.remove(playerHistoryKey);
                              KICK_COMPLETED.remove(playerHistoryKey);
                              LAST_STREAK_REPORT_NANOS.remove(playerHistoryKey);
                              if (!saveHistoryLocked()) {
                                 commandSource.sendError(Text.literal("反作弊白名单状态已生效，但历史清理保存失败，请检查日志"));
                              }
                           }

                           commandSource.sendFeedback(() -> Text.literal("Grim已跳过封禁白名单玩家 " + targetPlayer.getName().getString()), false);
                           return 0;
                        } else {
                           int punishedPlayerCount = punishFromGrim(commandSource, playerId, checkName, violationLevel, verboseText);
                           if (punishedPlayerCount > 0) {
                              synchronized (HISTORY_LOCK) {
                                 STREAK_COUNTS.remove(playerHistoryKey);
                                 KICK_COMPLETED.remove(playerHistoryKey);
                                 LAST_STREAK_REPORT_NANOS.remove(playerHistoryKey);
                                 if (!saveHistoryLocked()) {
                                    commandSource.sendError(Text.literal("玩家已按配置时长封禁，但反作弊阶段状态保存失败，请检查日志"));
                                 }
                              }
                           } else {
                              synchronized (HISTORY_LOCK) {
                                 STREAK_COUNTS.put(playerHistoryKey, streakThreshold);
                              }
                           }

                           return punishedPlayerCount;
                        }
                     } else {
                        String kickReason = buildPunishmentReason(checkName, violationLevel, verboseText);
                        targetPlayer.networkHandler
                           .disconnect(GreenManPunishmentService.renderKickMessage(kickReason, "Anticheat", targetPlayer.getName().getString()));
                        synchronized (HISTORY_LOCK) {
                           STREAK_COUNTS.remove(playerHistoryKey);
                           KICK_COMPLETED.put(playerHistoryKey, Boolean.TRUE);
                           LAST_STREAK_REPORT_NANOS.remove(playerHistoryKey);
                           if (!saveHistoryLocked()) {
                              commandSource.sendError(Text.literal("反作弊踢出已执行，但踢出记录保存失败，请检查日志"));
                           }
                        }

                        GreenManPunishmentService.broadcastKickChatAnnouncement(commandSource, targetPlayer, kickReason, "Anticheat");
                        commandSource.sendFeedback(() -> Text.literal("Grim连续异常达到阈值，已踢出玩家 " + targetPlayer.getName().getString() + " 并记录"), true);
                        return 1;
                     }
                  }
               }
            } else {
               commandSource.sendError(Text.literal("Grim连续处罚失败：目标玩家已经离线"));
               return 0;
            }
         }
      } else {
         if (commandSource != null) {
            commandSource.sendError(Text.literal("Grim连续处罚失败：玩家身份或服务器无效"));
         }

         return 0;
      }
   }

   private static void notifyOnlineOperators(
      MinecraftServer server, ServerPlayerEntity targetPlayer, String checkName, int violationLevel, String verboseText, int streakCount, boolean safeWalkOnly
   ) {
      if (server != null && targetPlayer != null) {
         String safeCheckName = sanitizeSingleLine(checkName, 64);
         String safeVerboseText = sanitizeSingleLine(verboseText, 256);
         String reminderText = "反作弊提醒：玩家 "
            + targetPlayer.getName().getString()
            + " 触发 "
            + safeCheckName
            + "，连续次数："
            + streakCount
            + "，VL："
            + Math.max(0, violationLevel);
         if (safeWalkOnly) {
            reminderText = reminderText + "，SafeWalk当前仅提醒不处罚";
         }

         if (!safeVerboseText.isEmpty()) {
            reminderText = reminderText + "，详情：" + safeVerboseText;
         }

         for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            if (onlinePlayer != null) {
               try {
                  if (CommandManager.requirePermissionLevel(CommandManager.MODERATORS_CHECK).test(onlinePlayer.getCommandSource())) {
                     onlinePlayer.sendMessage(Text.literal(reminderText));
                  }
               } catch (RuntimeException var13) {
                  GreenManServer.LOGGER.debug("发送反作弊OP提醒失败：{}", onlinePlayer.getName().getString(), var13);
               }
            }
         }
      }
   }

   private static boolean isBanWhitelistPlayer(ServerPlayerEntity targetPlayer) {
      return targetPlayer != null && targetPlayer.getUuid() != null && targetPlayer.getName() != null
         ? GreenManPunishmentService.isPlayerBanWhitelisted(new PlayerConfigEntry(targetPlayer.getUuid(), targetPlayer.getName().getString()))
         : false;
   }

   private static void logBanWhitelistReport(ServerPlayerEntity targetPlayer, String checkName, int violationLevel, String verboseText) {
      if (targetPlayer != null) {
         String normalizedCheckName = normalizeStreakCheckName(checkName);
         String reportKey = "ban-whitelist|player:" + targetPlayer.getUuid() + "|check:" + normalizedCheckName;
         int cooldownSeconds = Math.max(0, GreenManServerConfig.getGrimAlertLogCooldownSeconds());
         long currentReportNanos = System.nanoTime();
         if (cooldownSeconds > 0) {
            synchronized (HISTORY_LOCK) {
               Long previousReportNanos = LAST_BAN_WHITELIST_LOG_NANOS.get(reportKey);
               if (previousReportNanos != null && currentReportNanos - previousReportNanos < cooldownSeconds * 1000000000L) {
                  return;
               }

               LAST_BAN_WHITELIST_LOG_NANOS.put(reportKey, currentReportNanos);
            }
         }

         String safeCheckName = sanitizeSingleLine(checkName, 64);
         String safeVerboseText = sanitizeSingleLine(verboseText, 256);
         GreenManServer.LOGGER
            .info(
               "反作弊检测到封禁白名单玩家={}，检测={}，VL={}，详情={}，已跳过在线管理员提醒和自动处罚",
               new Object[]{
                  targetPlayer.getName().getString(),
                  safeCheckName.isEmpty() ? "unknown" : safeCheckName,
                  Math.max(0, Math.min(1000000, violationLevel)),
                  safeVerboseText.isEmpty() ? "无" : safeVerboseText
               }
            );
      }
   }

   private static String normalizeStreakCheckName(String checkName) {
      if (checkName != null && !checkName.isBlank()) {
         String baseCheckName = checkName.replaceFirst("[\\s(\\[].*$", "");
         String normalizedName = baseCheckName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
         return normalizedName.isEmpty() ? "unknown" : normalizedName;
      } else {
         return "unknown";
      }
   }

   private static String createStreakKey(UUID playerId, String normalizedCheckName) {
      String safePlayerId = playerId == null ? "unknown" : playerId.toString();
      String safeCheckName = normalizedCheckName != null && !normalizedCheckName.isBlank() ? normalizedCheckName : "unknown";
      return "player:" + safePlayerId + "|check:" + safeCheckName;
   }

   private static String buildPunishmentReason(String checkName, int violationLevel, String verboseText) {
      String safeCheckName = sanitizeSingleLine(checkName, 64);
      if (safeCheckName.isEmpty()) {
         safeCheckName = "unknown";
      }

      int safeViolationLevel = Math.max(0, Math.min(1000000, violationLevel));
      String safeVerboseText = sanitizeSingleLine(verboseText, 320);
      String baseReason = "anticheat: " + safeCheckName + ", VL: " + safeViolationLevel;
      return safeVerboseText.isEmpty() ? baseReason : baseReason + ", " + safeVerboseText;
   }

   private static String normalizeRemoteAddress(SocketAddress remoteAddress) {
      if (remoteAddress instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null) {
         String hostAddress = inetSocketAddress.getAddress().getHostAddress();
         if (hostAddress != null && !hostAddress.isBlank()) {
            int scopeSeparatorIndex = hostAddress.indexOf(37);
            return (scopeSeparatorIndex >= 0 ? hostAddress.substring(0, scopeSeparatorIndex) : hostAddress).toLowerCase(Locale.ROOT);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String sanitizeSingleLine(String rawText, int maximumCodePoints) {
      if (rawText != null && maximumCodePoints > 0) {
         StringBuilder sanitizedTextBuilder = new StringBuilder();
         int acceptedCodePointCount = 0;
         int characterOffset = 0;

         while (characterOffset < rawText.length() && acceptedCodePointCount < maximumCodePoints) {
            int currentCodePoint = rawText.codePointAt(characterOffset);
            characterOffset += Character.charCount(currentCodePoint);
            if (!Character.isISOControl(currentCodePoint) && currentCodePoint != 167) {
               sanitizedTextBuilder.appendCodePoint(currentCodePoint);
               acceptedCodePointCount++;
            }
         }

         return sanitizedTextBuilder.toString().replaceAll("\\s+", " ").trim();
      } else {
         return "";
      }
   }

   private static void loadHistoryLocked() {
      PUNISHMENT_COUNTS.clear();
      KICK_COMPLETED.clear();
      if (Files.notExists(HISTORY_PATH)) {
         historyLoaded = true;
      } else {
         try (Reader historyReader = Files.newBufferedReader(HISTORY_PATH, StandardCharsets.UTF_8)) {
            GreenManAnticheatService.HistoryData historyData = (GreenManAnticheatService.HistoryData)GSON.fromJson(
               historyReader, GreenManAnticheatService.HistoryData.class
            );
            boolean historyVersionSupported = historyData != null && historyData.historyVersion == 2;
            if (historyVersionSupported && historyData.punishmentCounts != null) {
               for (Entry<String, Integer> historyEntry : historyData.punishmentCounts.entrySet()) {
                  if (historyEntry.getKey() != null && !historyEntry.getKey().isBlank() && historyEntry.getValue() != null && historyEntry.getValue() > 0) {
                     PUNISHMENT_COUNTS.put(sanitizeSingleLine(historyEntry.getKey(), 128), Math.max(1, historyEntry.getValue()));
                  }
               }
            }

            if (historyVersionSupported && historyData.kickCompleted != null) {
               for (Entry<String, Boolean> kickEntry : historyData.kickCompleted.entrySet()) {
                  if (kickEntry.getKey() != null && !kickEntry.getKey().isBlank() && Boolean.TRUE.equals(kickEntry.getValue())) {
                     String sanitizedKickHistoryKey = sanitizeSingleLine(kickEntry.getKey(), 128);
                     if (isScopedKickHistoryKey(sanitizedKickHistoryKey)) {
                        KICK_COMPLETED.put(sanitizedKickHistoryKey, Boolean.TRUE);
                     }
                  }
               }
            }

            if (historyData != null && !historyVersionSupported) {
               GreenManServer.LOGGER.info("检测到旧版反作弊处罚历史，已重置异常累计状态并保留原版封禁名单");
            }
         } catch (RuntimeException | IOException var8) {
            GreenManServer.LOGGER.error("读取Grim反作弊处罚历史失败，将从空历史继续", var8);
            PUNISHMENT_COUNTS.clear();
            KICK_COMPLETED.clear();
         }

         historyLoaded = true;
      }
   }

   private static boolean saveHistoryLocked() {
      GreenManAnticheatService.HistoryData historyData = new GreenManAnticheatService.HistoryData();
      historyData.historyVersion = 2;
      historyData.punishmentCounts = new LinkedHashMap<>(PUNISHMENT_COUNTS);
      historyData.kickCompleted = new LinkedHashMap<>(KICK_COMPLETED);

      try {
         Files.createDirectories(HISTORY_PATH.getParent());

         try (Writer historyWriter = Files.newBufferedWriter(TEMP_HISTORY_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(historyData, historyWriter);
         }

         try {
            Files.move(TEMP_HISTORY_PATH, HISTORY_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException var6) {
            Files.move(TEMP_HISTORY_PATH, HISTORY_PATH, StandardCopyOption.REPLACE_EXISTING);
         }

         return true;
      } catch (RuntimeException | IOException var8) {
         GreenManServer.LOGGER.error("保存Grim反作弊处罚历史失败", var8);

         try {
            Files.deleteIfExists(TEMP_HISTORY_PATH);
         } catch (IOException var4) {
            GreenManServer.LOGGER.debug("清理Grim反作弊历史临时文件失败", var4);
         }

         return false;
      }
   }

   private static boolean isScopedKickHistoryKey(String historyKey) {
      if (historyKey != null && !historyKey.isBlank() && historyKey.length() <= 128 && historyKey.startsWith("player:")) {
         int checkSeparatorIndex = historyKey.indexOf("|check:");
         if (checkSeparatorIndex > "player:".length() && checkSeparatorIndex + "|check:".length() < historyKey.length()) {
            String playerIdText = historyKey.substring("player:".length(), checkSeparatorIndex);
            String normalizedCheckName = historyKey.substring(checkSeparatorIndex + "|check:".length());
            if (!normalizedCheckName.matches("[a-z0-9]+")) {
               return false;
            } else {
               try {
                  UUID.fromString(playerIdText);
                  return true;
               } catch (IllegalArgumentException var5) {
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static final class HistoryData {
      private int historyVersion;
      private Map<String, Integer> punishmentCounts = new LinkedHashMap<>();
      private Map<String, Boolean> kickCompleted = new LinkedHashMap<>();
   }
}
