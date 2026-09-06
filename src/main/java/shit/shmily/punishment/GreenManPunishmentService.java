package shit.shmily.punishment;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManBanWhitelistConfig;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.music.GreenManMusicService;
import shit.shmily.text.GreenManTextFormatter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.server.BanEntry;
import net.minecraft.server.BannedIpEntry;
import net.minecraft.server.BannedIpList;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManPunishmentService {
   private static final ZoneId CHINA_TIME_ZONE = ZoneId.of("Asia/Shanghai");
   private static final DateTimeFormatter EXPIRATION_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.CHINA)
      .withZone(CHINA_TIME_ZONE);
   private static final long MAXIMUM_BAN_DURATION_MILLISECONDS = Duration.ofDays(36500L).toMillis();
   private static final int MAXIMUM_REASON_CODE_POINTS = 512;
   private static final int MAXIMUM_RENDERED_TEMPLATE_CODE_POINTS = 4096;
   private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\{(server_title|action|reason|ban_count|solution|expires_at|operator)\\}");
   private static final Pattern BAN_CHAT_TEMPLATE_VARIABLE_PATTERN = Pattern.compile(
      "\\{(server_title|action|player_name|player_id|duration|reason|operator)\\}"
   );
   private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Text.literal("所选玩家均已被封禁"));
   private static final SimpleCommandExceptionType ERROR_NOT_BANNED = new SimpleCommandExceptionType(Text.literal("所选玩家均未被封禁"));
   private static final SimpleCommandExceptionType ERROR_NO_KICK_TARGET = new SimpleCommandExceptionType(Text.literal("没有可踢出的在线玩家"));
   private static final DynamicCommandExceptionType ERROR_INVALID_DURATION = new DynamicCommandExceptionType(
      invalidValue -> Text.literal("封禁时长无效：" + invalidValue + "；可用格式：30s、10m、2h、7d、0、permanent、forever")
   );
   private static final DynamicCommandExceptionType ERROR_INVALID_IP = new DynamicCommandExceptionType(
      invalidValue -> Text.literal("IP地址无效：" + invalidValue + "；请输入IPv4或IPv6地址")
   );
   private static final SimpleCommandExceptionType ERROR_IP_NOT_BANNED = new SimpleCommandExceptionType(Text.literal("该IP没有封禁记录"));
   private static final DynamicCommandExceptionType ERROR_BAN_WHITELISTED = new DynamicCommandExceptionType(
      targetName -> Text.literal("该目标位于封禁白名单，无法封禁：" + targetName)
   );
   private static final DynamicCommandExceptionType ERROR_INSUFFICIENT_OPERATOR_LEVEL = new DynamicCommandExceptionType(
      targetName -> Text.literal("不能封禁权限等级相同或更高的管理员：" + targetName)
   );
   private static final SimpleCommandExceptionType ERROR_BAN_STORAGE = new SimpleCommandExceptionType(Text.literal("封禁列表写入失败，请检查服务器目录权限和日志"));

   private GreenManPunishmentService() {
   }

   public static void registerEnhancedVanillaBanCommand(CommandDispatcher<ServerCommandSource> commandDispatcher) {
      if (commandDispatcher != null) {
         commandDispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("ban").requires(CommandManager.requirePermissionLevel(CommandManager.ADMINS_CHECK)))
               .then(
                  ((RequiredArgumentBuilder)CommandManager.argument("targets", GameProfileArgumentType.gameProfile())
                        .executes(
                           commandContext -> banPlayers(
                              (ServerCommandSource)commandContext.getSource(),
                              GameProfileArgumentType.getProfileArgument(commandContext, "targets"),
                              GreenManPunishmentService.BanDuration.permanentDuration(),
                              "未填写原因",
                              null
                           )
                        ))
                     .then(
                        CommandManager.argument("reason", MessageArgumentType.message())
                           .executes(
                              commandContext -> banPlayersFromVanillaDetails(
                                 (ServerCommandSource)commandContext.getSource(),
                                 GameProfileArgumentType.getProfileArgument(commandContext, "targets"),
                                 MessageArgumentType.getMessage(commandContext, "reason").getString()
                              )
                           )
                     )
               )
         );
      }
   }

   public static void registerEnhancedVanillaBanIpCommand(CommandDispatcher<ServerCommandSource> commandDispatcher) {
      if (commandDispatcher != null) {
         commandDispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("ban-ip").requires(CommandManager.requirePermissionLevel(CommandManager.ADMINS_CHECK)))
               .then(
                  ((RequiredArgumentBuilder)CommandManager.argument("target", StringArgumentType.word())
                        .executes(
                           commandContext -> banIp(
                              (ServerCommandSource)commandContext.getSource(), StringArgumentType.getString(commandContext, "target"), "forever", "未填写原因"
                           )
                        ))
                     .then(
                        CommandManager.argument("reason", MessageArgumentType.message())
                           .executes(
                              commandContext -> banIpFromVanillaDetails(
                                 (ServerCommandSource)commandContext.getSource(),
                                 StringArgumentType.getString(commandContext, "target"),
                                 MessageArgumentType.getMessage(commandContext, "reason").getString()
                              )
                           )
                     )
               )
         );
      }
   }

   private static int banIpFromVanillaDetails(ServerCommandSource commandSource, String targetText, String detailsText) throws CommandSyntaxException {
      String sanitizedDetails = sanitizeSingleLineText(detailsText, 512);
      if (sanitizedDetails.isEmpty()) {
         return banIp(commandSource, targetText, "forever", "未填写原因");
      } else {
         int firstWhitespaceIndex = findFirstWhitespaceIndex(sanitizedDetails);
         String firstWord = firstWhitespaceIndex < 0 ? sanitizedDetails : sanitizedDetails.substring(0, firstWhitespaceIndex);
         GreenManPunishmentService.BanDuration optionalDuration = tryParseDuration(firstWord);
         if (optionalDuration == null) {
            return banIp(commandSource, targetText, "forever", sanitizedDetails);
         } else {
            String remainingReason = firstWhitespaceIndex < 0 ? "" : sanitizedDetails.substring(firstWhitespaceIndex).trim();
            return banIp(commandSource, targetText, optionalDuration.inputText(), remainingReason);
         }
      }
   }

   public static int banPlayers(ServerCommandSource commandSource, Collection<PlayerConfigEntry> targetProfiles, String durationText, String reasonText) throws CommandSyntaxException {
      GreenManPunishmentService.BanDuration banDuration = parseRequiredDuration(durationText);
      return banPlayers(commandSource, targetProfiles, banDuration, reasonText, null);
   }

   public static int banPlayers(
      ServerCommandSource commandSource, Collection<PlayerConfigEntry> targetProfiles, String durationText, String reasonText, String operatorOverride
   ) throws CommandSyntaxException {
      GreenManPunishmentService.BanDuration banDuration = parseRequiredDuration(durationText);
      return banPlayers(commandSource, targetProfiles, banDuration, reasonText, operatorOverride);
   }

   private static int banPlayersFromVanillaDetails(ServerCommandSource commandSource, Collection<PlayerConfigEntry> targetProfiles, String detailsText) throws CommandSyntaxException {
      String sanitizedDetails = sanitizeSingleLineText(detailsText, 512);
      if (sanitizedDetails.isEmpty()) {
         return banPlayers(commandSource, targetProfiles, GreenManPunishmentService.BanDuration.permanentDuration(), "未填写原因", null);
      } else {
         int firstWhitespaceIndex = findFirstWhitespaceIndex(sanitizedDetails);
         String firstWord = firstWhitespaceIndex < 0 ? sanitizedDetails : sanitizedDetails.substring(0, firstWhitespaceIndex);
         GreenManPunishmentService.BanDuration optionalDuration = tryParseDuration(firstWord);
         if (optionalDuration == null) {
            return banPlayers(commandSource, targetProfiles, GreenManPunishmentService.BanDuration.permanentDuration(), sanitizedDetails, null);
         } else {
            String remainingReason = firstWhitespaceIndex < 0 ? "" : sanitizedDetails.substring(firstWhitespaceIndex).trim();
            String effectiveReason = remainingReason.isEmpty() ? "未填写原因" : remainingReason;
            return banPlayers(commandSource, targetProfiles, optionalDuration, effectiveReason, null);
         }
      }
   }

   private static int banPlayers(
      ServerCommandSource commandSource,
      Collection<PlayerConfigEntry> targetProfiles,
      GreenManPunishmentService.BanDuration banDuration,
      String reasonText,
      String operatorOverride
   ) throws CommandSyntaxException {
      if (commandSource == null) {
         throw ERROR_BAN_STORAGE.create();
      } else if (targetProfiles != null && !targetProfiles.isEmpty()) {
         GreenManPunishmentService.BanDuration effectiveDuration = banDuration == null
            ? GreenManPunishmentService.BanDuration.permanentDuration()
            : banDuration;
         String sanitizedReason = sanitizeSingleLineText(reasonText, 512);
         String effectiveReason = sanitizedReason.isEmpty() ? "未填写原因" : sanitizedReason;
         BannedPlayerList userBanList = commandSource.getServer().getPlayerManager().getUserBanList();
         int successfullyBannedCount = 0;
         Date createdDate = new Date();
         Date expirationDate = effectiveDuration.toExpirationDate(createdDate);
         String operatorName = sanitizeSingleLineText(
            operatorOverride != null && !operatorOverride.isBlank() ? operatorOverride : commandSource.getName(), 128
         );
         String effectiveOperatorName = operatorName.isEmpty() ? "Server" : operatorName;

         for (PlayerConfigEntry targetProfile : targetProfiles) {
            if (targetProfile != null) {
               boolean targetWhitelisted = isPlayerBanWhitelisted(targetProfile);
               boolean canBypassPlayerWhitelist = canBypassPlayerBanWhitelist(commandSource);
               if (targetWhitelisted && !canBypassPlayerWhitelist || !canOperatorBanTarget(commandSource, targetProfile)) {
                  throw targetWhitelisted
                     ? ERROR_BAN_WHITELISTED.create(targetProfile.name())
                     : ERROR_INSUFFICIENT_OPERATOR_LEVEL.create(targetProfile.name());
               }

               if (!userBanList.contains(targetProfile)) {
                  BannedPlayerEntry banListEntry = new BannedPlayerEntry(targetProfile, createdDate, effectiveOperatorName, expirationDate, effectiveReason);

                  boolean banEntryAdded;
                  try {
                     banEntryAdded = userBanList.add(banListEntry);
                  } catch (RuntimeException var22) {
                     GreenManServer.LOGGER.error("写入玩家 {} 的封禁记录失败", targetProfile.name(), var22);
                     continue;
                  }

                  if (banEntryAdded) {
                     successfullyBannedCount++;
                     int playerBanCount = GreenManBanCountService.incrementBanCount(targetProfile.id());
                     commandSource.sendFeedback(
                        () -> Text.literal(
                           "已封禁玩家 " + targetProfile.name() + "，到期时间：" + formatExpirationTime(expirationDate) + "，原因：" + effectiveReason
                        ),
                        false
                     );
                     broadcastBanChatAnnouncement(commandSource, targetProfile, effectiveDuration, effectiveReason, effectiveOperatorName);
                     ServerPlayerEntity onlineTarget = commandSource.getServer().getPlayerManager().getPlayer(targetProfile.id());
                     if (onlineTarget != null) {
                        onlineTarget.networkHandler
                           .disconnect(renderBanMessage(effectiveReason, expirationDate, effectiveOperatorName, playerBanCount, targetProfile.name()));
                     }
                  }
               }
            }
         }

         if (successfullyBannedCount == 0) {
            boolean hasUnbannedTarget = targetProfiles.stream()
               .filter(targetProfilex -> targetProfilex != null)
               .anyMatch(targetProfilex -> !userBanList.contains(targetProfilex));
            if (hasUnbannedTarget) {
               throw ERROR_BAN_STORAGE.create();
            } else {
               throw ERROR_ALREADY_BANNED.create();
            }
         } else {
            return successfullyBannedCount;
         }
      } else {
         throw ERROR_ALREADY_BANNED.create();
      }
   }

   public static boolean isPlayerBanWhitelisted(PlayerConfigEntry targetProfile) {
      if (targetProfile == null) {
         return false;
      } else {
         for (String whitelistEntry : GreenManBanWhitelistConfig.getPlayers()) {
            if (whitelistEntry != null
               && !whitelistEntry.isBlank()
               && (
                  whitelistEntry.equalsIgnoreCase(targetProfile.name())
                     || targetProfile.id() != null && whitelistEntry.equalsIgnoreCase(targetProfile.id().toString())
               )) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean canOperatorBanTarget(ServerCommandSource commandSource, PlayerConfigEntry targetProfile) {
      if (commandSource == null || commandSource.getServer() == null || targetProfile == null || targetProfile.id() == null) {
         return false;
      } else if (commandSource.getEntity() != null && !"Anticheat".equalsIgnoreCase(commandSource.getName())) {
         int operatorLevel = commandSource.getServer()
            .getPermissionLevel(new PlayerConfigEntry(commandSource.getEntity().getUuid(), commandSource.getName()))
            .getLevel()
            .getLevel();
         int targetLevel = commandSource.getServer()
            .getPermissionLevel(new PlayerConfigEntry(targetProfile.id(), targetProfile.name()))
            .getLevel()
            .getLevel();
         return operatorLevel >= 4 || operatorLevel > targetLevel;
      } else {
         return true;
      }
   }

   private static boolean canBypassPlayerBanWhitelist(ServerCommandSource commandSource) {
      if (commandSource == null || commandSource.getServer() == null) {
         return false;
      } else if (commandSource.getEntity() == null) {
         return true;
      } else {
         int operatorLevel = commandSource.getServer()
            .getPermissionLevel(new PlayerConfigEntry(commandSource.getEntity().getUuid(), commandSource.getName()))
            .getLevel()
            .getLevel();
         return operatorLevel >= 4;
      }
   }

   public static boolean isIpBanWhitelisted(String normalizedIpAddress) {
      if (normalizedIpAddress != null && !normalizedIpAddress.isBlank()) {
         for (String whitelistEntry : GreenManBanWhitelistConfig.getIpAddresses()) {
            String normalizedWhitelistEntry = normalizeIpAddress(whitelistEntry);
            if (normalizedIpAddress.equalsIgnoreCase(normalizedWhitelistEntry)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static int unbanPlayers(ServerCommandSource commandSource, Collection<PlayerConfigEntry> targetProfiles) throws CommandSyntaxException {
      if (commandSource != null && targetProfiles != null && !targetProfiles.isEmpty()) {
         BannedPlayerList userBanList = commandSource.getServer().getPlayerManager().getUserBanList();
         int successfullyUnbannedCount = 0;

         for (PlayerConfigEntry targetProfile : targetProfiles) {
            if (targetProfile != null && userBanList.contains(targetProfile)) {
               boolean banEntryRemoved = userBanList.remove(targetProfile);
               if (banEntryRemoved) {
                  successfullyUnbannedCount++;
                  commandSource.sendFeedback(() -> Text.literal("已解除玩家 " + targetProfile.name() + " 的封禁"), true);
               }
            }
         }

         if (successfullyUnbannedCount == 0) {
            throw ERROR_NOT_BANNED.create();
         } else {
            return successfullyUnbannedCount;
         }
      } else {
         throw ERROR_NOT_BANNED.create();
      }
   }

   public static int banIp(ServerCommandSource commandSource, String ipAddress, String durationText, String reasonText) throws CommandSyntaxException {
      return banIp(commandSource, ipAddress, durationText, reasonText, null);
   }

   public static int banIp(ServerCommandSource commandSource, String ipAddress, String durationText, String reasonText, String operatorOverride) throws CommandSyntaxException {
      if (commandSource != null && commandSource.getServer() != null && ipAddress != null && !ipAddress.isBlank()) {
         String normalizedIpAddress = resolveIpBanTarget(commandSource, ipAddress);
         if (normalizedIpAddress == null) {
            throw ERROR_INVALID_IP.create(ipAddress);
         } else if (isIpBanWhitelisted(normalizedIpAddress)) {
            throw ERROR_BAN_WHITELISTED.create(normalizedIpAddress);
         } else {
            for (ServerPlayerEntity onlinePlayer : commandSource.getServer().getPlayerManager().getPlayerList()) {
               if (onlinePlayer != null
                  && onlinePlayer.networkHandler != null
                  && normalizedIpAddress.equalsIgnoreCase(normalizeSocketAddress(onlinePlayer.networkHandler.getConnectionAddress()))) {
                  PlayerConfigEntry onlineIdentity = new PlayerConfigEntry(onlinePlayer.getUuid(), onlinePlayer.getName().getString());
                  if (isPlayerBanWhitelisted(onlineIdentity)) {
                     throw ERROR_BAN_WHITELISTED.create(onlinePlayer.getName().getString());
                  }

                  if (!canOperatorBanTarget(commandSource, onlineIdentity)) {
                     throw ERROR_INSUFFICIENT_OPERATOR_LEVEL.create(onlinePlayer.getName().getString());
                  }
               }
            }

            GreenManPunishmentService.BanDuration banDuration = parseRequiredDuration(durationText);
            String effectiveReason = "您的IP已被封禁";
            String effectiveOperator = defaultIfEmpty(
               sanitizeSingleLineText(operatorOverride != null && !operatorOverride.isBlank() ? operatorOverride : commandSource.getName(), 128), "Server"
            );
            BannedIpList ipBanList = commandSource.getServer().getPlayerManager().getIpBanList();
            if (ipBanList.isBanned(normalizedIpAddress)) {
               commandSource.sendError(Text.literal("该IP已经被封禁"));
               return 0;
            } else {
               Date createdDate = new Date();
               Date expirationDate = banDuration.toExpirationDate(createdDate);
               BannedIpEntry ipBanEntry = new BannedIpEntry(normalizedIpAddress, createdDate, effectiveOperator, expirationDate, effectiveReason);

               boolean added;
               try {
                  added = ipBanList.add(ipBanEntry);
               } catch (RuntimeException var19) {
                  GreenManServer.LOGGER.error("写入IP {} 的封禁记录失败", normalizedIpAddress, var19);
                  throw ERROR_BAN_STORAGE.create();
               }

               if (!added) {
                  throw ERROR_BAN_STORAGE.create();
               } else {
                  int disconnectedPlayerCount = 0;

                  for (ServerPlayerEntity onlinePlayerx : commandSource.getServer().getPlayerManager().getPlayerList()) {
                     if (onlinePlayerx != null && onlinePlayerx.networkHandler != null) {
                        try {
                           String onlineIpAddress = normalizeSocketAddress(onlinePlayerx.networkHandler.getConnectionAddress());
                           if (normalizedIpAddress.equals(onlineIpAddress)) {
                              onlinePlayerx.networkHandler
                                 .disconnect(renderBanMessage(effectiveReason, expirationDate, effectiveOperator, 0, onlinePlayerx.getName().getString()));
                              disconnectedPlayerCount++;
                           }
                        } catch (RuntimeException var18) {
                           GreenManServer.LOGGER.debug("读取在线玩家IP失败，跳过IP封禁断开：{}", onlinePlayerx.getName().getString(), var18);
                        }
                     }
                  }

                  int finalDisconnectedPlayerCount = disconnectedPlayerCount;
                  commandSource.sendFeedback(
                     () -> Text.literal(
                        "已封禁IP "
                           + normalizedIpAddress
                           + "，时长："
                           + banDuration.displayText()
                           + "，原因："
                           + effectiveReason
                           + "，已断开："
                           + finalDisconnectedPlayerCount
                           + "人"
                     ),
                     true
                  );
                  return 1;
               }
            }
         }
      } else {
         throw ERROR_INVALID_IP.create(ipAddress == null ? "空值" : ipAddress);
      }
   }

   public static int unbanIp(ServerCommandSource commandSource, String ipAddress) throws CommandSyntaxException {
      if (commandSource != null && commandSource.getServer() != null && ipAddress != null && !ipAddress.isBlank()) {
         String normalizedIpAddress = normalizeIpAddress(ipAddress);
         if (normalizedIpAddress == null) {
            throw ERROR_INVALID_IP.create(ipAddress);
         } else {
            BannedIpList ipBanList = commandSource.getServer().getPlayerManager().getIpBanList();
            if (!ipBanList.remove(normalizedIpAddress)) {
               throw ERROR_IP_NOT_BANNED.create();
            } else {
               commandSource.sendFeedback(() -> Text.literal("已解除IP " + normalizedIpAddress + " 的封禁"), true);
               return 1;
            }
         }
      } else {
         throw ERROR_INVALID_IP.create(ipAddress == null ? "空值" : ipAddress);
      }
   }

   private static String normalizeIpAddress(String rawIpAddress) {
      String sanitizedIpAddress = rawIpAddress == null ? "" : rawIpAddress.trim();
      if (!sanitizedIpAddress.isEmpty() && sanitizedIpAddress.matches("[0-9A-Fa-f:.]+")) {
         try {
            InetAddress parsedAddress = InetAddress.getByName(sanitizedIpAddress);
            String normalizedAddress = parsedAddress.getHostAddress();
            int zoneSeparatorIndex = normalizedAddress.indexOf(37);
            return zoneSeparatorIndex >= 0 ? normalizedAddress.substring(0, zoneSeparatorIndex) : normalizedAddress;
         } catch (Exception var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String resolveIpBanTarget(ServerCommandSource commandSource, String rawTarget) {
      String directIpAddress = normalizeIpAddress(rawTarget);
      if (directIpAddress != null) {
         return directIpAddress;
      } else if (commandSource != null && commandSource.getServer() != null && rawTarget != null && !rawTarget.isBlank()) {
         ServerPlayerEntity targetPlayer = commandSource.getServer().getPlayerManager().getPlayer(rawTarget.trim());
         return targetPlayer != null && targetPlayer.networkHandler != null ? normalizeSocketAddress(targetPlayer.networkHandler.getConnectionAddress()) : null;
      } else {
         return null;
      }
   }

   private static String normalizeSocketAddress(SocketAddress remoteAddress) {
      if (remoteAddress == null) {
         return null;
      } else {
         return remoteAddress instanceof InetSocketAddress inetSocketAddress && inetSocketAddress.getAddress() != null
            ? normalizeIpAddress(inetSocketAddress.getAddress().getHostAddress())
            : null;
      }
   }

   public static int showBanList(ServerCommandSource commandSource) {
      if (commandSource != null && commandSource.getServer() != null) {
         BannedPlayerList userBanList = commandSource.getServer().getPlayerManager().getUserBanList();
         if (userBanList.values().isEmpty()) {
            commandSource.sendFeedback(() -> Text.literal("当前没有被封禁的玩家"), false);
            return 0;
         } else {
            List<BannedPlayerEntry> banEntries = new ArrayList<>(userBanList.values());
            banEntries.sort(Comparator.comparing(BanEntry::getCreationDate, Comparator.nullsLast(Date::compareTo)));
            commandSource.sendFeedback(() -> Text.literal("当前封禁列表（" + banEntries.size() + "人）："), false);

            for (BannedPlayerEntry banEntry : banEntries) {
               if (banEntry != null && banEntry.getKey() != null) {
                  String playerName = ((PlayerConfigEntry)banEntry.getKey()).name() == null ? "未知名称" : ((PlayerConfigEntry)banEntry.getKey()).name();
                  String playerId = ((PlayerConfigEntry)banEntry.getKey()).id() == null
                     ? "未知UUID"
                     : ((PlayerConfigEntry)banEntry.getKey()).id().toString();
                  String createdText = banEntry.getCreationDate() == null ? "未知" : EXPIRATION_TIME_FORMATTER.format(banEntry.getCreationDate().toInstant());
                  String expirationText = banEntry.getExpiryDate() == null ? "永久" : EXPIRATION_TIME_FORMATTER.format(banEntry.getExpiryDate().toInstant());
                  String operatorText = banEntry.getSource() != null && !banEntry.getSource().isBlank() ? banEntry.getSource() : "未知";
                  String reasonText = banEntry.getReason() != null && !banEntry.getReason().isBlank() ? banEntry.getReason() : "未填写原因";
                  commandSource.sendFeedback(
                     () -> Text.literal(
                        "- "
                           + playerName
                           + "（"
                           + playerId
                           + "） | 封禁时间："
                           + createdText
                           + " | 到期时间："
                           + expirationText
                           + " | 处理人："
                           + operatorText
                           + " | 原因："
                           + reasonText
                     ),
                     false
                  );
               }
            }

            return banEntries.size();
         }
      } else {
         return 0;
      }
   }

   public static int kickPlayers(ServerCommandSource commandSource, Collection<ServerPlayerEntity> targetPlayers, String reasonText) throws CommandSyntaxException {
      if (commandSource != null && targetPlayers != null && !targetPlayers.isEmpty()) {
         String sanitizedReason = sanitizeSingleLineText(reasonText, 512);
         String effectiveReason = sanitizedReason.isEmpty() ? "被管理员踢出服务器" : sanitizedReason;
         String operatorName = sanitizeSingleLineText(commandSource.getName(), 128);
         String effectiveOperatorName = operatorName.isEmpty() ? "Server" : operatorName;
         int successfullyKickedCount = 0;

         for (ServerPlayerEntity targetPlayer : targetPlayers) {
            if (targetPlayer != null) {
               targetPlayer.networkHandler.disconnect(renderKickMessage(effectiveReason, effectiveOperatorName, targetPlayer.getName().getString()));
               successfullyKickedCount++;
               broadcastKickChatAnnouncement(commandSource, targetPlayer, effectiveReason, effectiveOperatorName);
               commandSource.sendFeedback(() -> Text.literal("已踢出玩家 " + targetPlayer.getName().getString() + "，原因：" + effectiveReason), false);
            }
         }

         if (successfullyKickedCount == 0) {
            throw ERROR_NO_KICK_TARGET.create();
         } else {
            return successfullyKickedCount;
         }
      } else {
         throw ERROR_NO_KICK_TARGET.create();
      }
   }

   public static Text renderBanMessage(String reasonText, Date expirationDate, String operatorName) {
      return renderBanMessage(reasonText, expirationDate, operatorName, 0);
   }

   public static Text renderBanMessage(String reasonText, Date expirationDate, String operatorName, int banCount) {
      return renderBanMessage(reasonText, expirationDate, operatorName, banCount, "未知玩家");
   }

   public static Text renderBanMessage(String reasonText, Date expirationDate, String operatorName, int banCount, String playerName) {
      String effectiveReason = defaultIfEmpty(sanitizeSingleLineText(reasonText, 512), "未填写原因");
      if (!GreenManServerConfig.isPunishmentTemplatesEnabled()) {
         MutableText vanillaMessage = Text.translatable("multiplayer.disconnect.banned.reason", new Object[]{effectiveReason});
         if (expirationDate != null) {
            vanillaMessage.append(Text.translatable("multiplayer.disconnect.banned.expiration", new Object[]{formatExpirationTime(expirationDate)}));
         }

         return vanillaMessage;
      } else {
         return renderTemplate(
            GreenManServerConfig.getBanScreenTemplate(),
            GreenManServerConfig.getBanActionText(),
            effectiveReason,
            expirationDate,
            operatorName,
            banCount,
            playerName
         );
      }
   }

   public static Text renderKickMessage(String reasonText, String operatorName) {
      return renderKickMessage(reasonText, operatorName, "未知玩家");
   }

   public static Text renderKickMessage(String reasonText, String operatorName, String playerName) {
      String effectiveReason = defaultIfEmpty(sanitizeSingleLineText(reasonText, 512), "被管理员踢出服务器");
      return (Text)(!GreenManServerConfig.isPunishmentTemplatesEnabled()
         ? Text.literal(effectiveReason)
         : renderTemplate(
            GreenManServerConfig.getKickScreenTemplate(), GreenManServerConfig.getKickActionText(), effectiveReason, null, operatorName, 0, playerName
         ));
   }

   private static void broadcastBanChatAnnouncement(
      ServerCommandSource commandSource, PlayerConfigEntry targetProfile, GreenManPunishmentService.BanDuration banDuration, String reasonText, String operatorName
   ) {
      broadcastPunishmentChatAnnouncement(
         commandSource,
         targetProfile,
         GreenManServerConfig.getBanActionText(),
         banDuration == null ? "永久" : banDuration.displayText(),
         reasonText,
         operatorName,
         true
      );
   }

   public static void broadcastKickChatAnnouncement(ServerCommandSource commandSource, ServerPlayerEntity targetPlayer, String reasonText, String operatorName) {
      if (targetPlayer != null) {
         PlayerConfigEntry targetProfile = new PlayerConfigEntry(targetPlayer.getUuid(), targetPlayer.getName().getString());
         broadcastPunishmentChatAnnouncement(commandSource, targetProfile, GreenManServerConfig.getKickActionText(), "仅本次连接", reasonText, operatorName, false);
      }
   }

   private static void broadcastPunishmentChatAnnouncement(
      ServerCommandSource commandSource,
      PlayerConfigEntry targetProfile,
      String actionText,
      String durationText,
      String reasonText,
      String operatorName,
      boolean shouldPlayBanSound
   ) {
      if (commandSource != null && targetProfile != null && commandSource.getServer() != null) {
         if (GreenManServerConfig.isPunishmentChatBanAnnouncementEnabled()) {
            String configuredTemplate = GreenManServerConfig.getPunishmentChatBanAnnouncementTemplate();
            String effectiveTemplate = configuredTemplate != null && !configuredTemplate.isEmpty()
               ? configuredTemplate
               : "&c[{server_title}]&e玩家{player_name}&c{action}&f，时长：&e{duration}&f，原因：&e{reason}&f，处理人：&b{operator}";
            if (!effectiveTemplate.contains("{action}") && effectiveTemplate.contains("被封禁")) {
               effectiveTemplate = effectiveTemplate.replace("被封禁", "{action}");
            }

            String safePlayerName = sanitizeSingleLineText(targetProfile.name(), 128);
            String safePlayerId = targetProfile.id() == null ? "未知" : targetProfile.id().toString();
            String safeAction = defaultIfEmpty(sanitizeSingleLineText(actionText, 64), "受到处罚");
            String safeReason = defaultIfEmpty(sanitizeSingleLineText(reasonText, 512), "未填写原因");
            String safeOperator = defaultIfEmpty(sanitizeSingleLineText(operatorName, 128), "Server");
            String safeDuration = defaultIfEmpty(sanitizeSingleLineText(durationText, 128), "永久");
            Matcher variableMatcher = BAN_CHAT_TEMPLATE_VARIABLE_PATTERN.matcher(effectiveTemplate);
            StringBuffer renderedTextBuffer = new StringBuffer();

            while (variableMatcher.find()) {
               String var18 = variableMatcher.group(1);

               String replacementText = switch (var18) {
                  case "server_title" -> defaultIfEmpty(sanitizeSingleLineText(GreenManServerConfig.getPunishmentServerTitle(), 128), "GreenManServer");
                  case "action" -> safeAction;
                  case "player_name" -> defaultIfEmpty(safePlayerName, "未知玩家");
                  case "player_id" -> safePlayerId;
                  case "duration" -> safeDuration;
                  case "reason" -> safeReason;
                  case "operator" -> safeOperator;
                  default -> variableMatcher.group();
               };
               variableMatcher.appendReplacement(renderedTextBuffer, Matcher.quoteReplacement(replacementText));
            }

            variableMatcher.appendTail(renderedTextBuffer);
            Text announcementComponent = GreenManTextFormatter.parseRainbow(
               sanitizeMultilineText(renderedTextBuffer.toString(), 4096), Style.EMPTY
            );
            if (!announcementComponent.getString().isEmpty()) {
               commandSource.getServer().getPlayerManager().broadcast(announcementComponent, false);
               if (shouldPlayBanSound) {
                  playBanAnnouncementSound(commandSource);
               }
            }
         }
      }
   }

   private static void playBanAnnouncementSound(ServerCommandSource commandSource) {
      if (commandSource != null && commandSource.getServer() != null && GreenManServerConfig.isPunishmentBanSoundEnabled()) {
         String soundMode = GreenManServerConfig.getPunishmentBanSoundMode();
         if (!"OFF".equals(soundMode)) {
            List<ServerPlayerEntity> soundRecipients = List.copyOf(commandSource.getServer().getPlayerManager().getPlayerList());
            if (!soundRecipients.isEmpty()) {
               if ("VANILLA".equals(soundMode) || "BOTH".equals(soundMode)) {
                  GreenManMusicService.playVanillaSounds(soundRecipients, GreenManServerConfig.getPunishmentBanVanillaSoundIds(), "封禁公告");
               }

               if ("INTERNAL".equals(soundMode) || "BOTH".equals(soundMode)) {
                  GreenManMusicService.playInternalAudio(soundRecipients, GreenManServerConfig.getPunishmentBanInternalMusicFile());
               }
            }
         }
      }
   }

   private static Text renderTemplate(
      String templateText, String actionText, String reasonText, Date expirationDate, String operatorName, int banCount, String playerName
   ) {
      String sanitizedTemplate = sanitizeMultilineText(templateText, 4096);
      String effectiveTemplate = sanitizedTemplate.isEmpty() ? "&c{server_title}\n&f操作：&e{action}\n&f原因：&e{reason}\n&f解决方法：&b{solution}" : sanitizedTemplate;
      Matcher variableMatcher = TEMPLATE_VARIABLE_PATTERN.matcher(effectiveTemplate);
      StringBuffer renderedTextBuffer = new StringBuffer();

      while (variableMatcher.find()) {
         String variableName = variableMatcher.group(1);

         String replacementText = switch (variableName) {
            case "server_title" -> defaultIfEmpty(sanitizeSingleLineText(GreenManServerConfig.getPunishmentServerTitle(), 128), "GreenManServer");
            case "action" -> "玩家"
               + defaultIfEmpty(sanitizeSingleLineText(playerName, 128), "未知玩家")
               + defaultIfEmpty(sanitizeSingleLineText(actionText, 64), "处罚");
            case "reason" -> defaultIfEmpty(sanitizeSingleLineText(reasonText, 512), "未填写原因");
            case "ban_count" -> banCount <= 0 ? "不适用" : Integer.toString(banCount);
            case "solution" -> defaultIfEmpty(sanitizeSingleLineText(GreenManServerConfig.getPunishmentSolutionText(), 512), "请联系服务器管理员。");
            case "expires_at" -> expirationDate == null ? "永久" : formatExpirationTime(expirationDate);
            case "operator" -> defaultIfEmpty(sanitizeSingleLineText(operatorName, 128), "Server");
            default -> variableMatcher.group();
         };
         variableMatcher.appendReplacement(renderedTextBuffer, Matcher.quoteReplacement(replacementText));
      }

      variableMatcher.appendTail(renderedTextBuffer);
      String boundedRenderedText = sanitizeMultilineText(renderedTextBuffer.toString(), 4096);
      return GreenManTextFormatter.parseRainbow(boundedRenderedText, Style.EMPTY);
   }

   private static Text parseLegacyFormatting(String formattedText) {
      MutableText renderedComponent = Text.empty();
      Style currentStyle = Style.EMPTY;
      StringBuilder pendingTextBuilder = new StringBuilder();
      if (formattedText != null && !formattedText.isEmpty()) {
         for (int characterIndex = 0; characterIndex < formattedText.length(); characterIndex++) {
            char currentCharacter = formattedText.charAt(characterIndex);
            if (currentCharacter == '&' && characterIndex + 1 < formattedText.length()) {
               char formattingCode = Character.toLowerCase(formattedText.charAt(characterIndex + 1));
               Formatting chatFormatting = Formatting.byCode(formattingCode);
               if (chatFormatting != null) {
                  appendStyledText(renderedComponent, pendingTextBuilder, currentStyle);
                  currentStyle = currentStyle.withExclusiveFormatting(chatFormatting);
                  characterIndex++;
                  continue;
               }
            }

            pendingTextBuilder.append(currentCharacter);
         }

         appendStyledText(renderedComponent, pendingTextBuilder, currentStyle);
         return renderedComponent.getString().isEmpty() ? Text.translatable("multiplayer.disconnect.kicked") : renderedComponent;
      } else {
         return Text.translatable("multiplayer.disconnect.kicked");
      }
   }

   private static void appendStyledText(MutableText destinationComponent, StringBuilder pendingTextBuilder, Style currentStyle) {
      if (pendingTextBuilder.length() != 0) {
         destinationComponent.append(Text.literal(pendingTextBuilder.toString()).fillStyle(currentStyle));
         pendingTextBuilder.setLength(0);
      }
   }

   private static GreenManPunishmentService.BanDuration parseRequiredDuration(String durationText) throws CommandSyntaxException {
      String sanitizedDuration = sanitizeSingleLineText(durationText, 32).toLowerCase(Locale.ROOT);
      GreenManPunishmentService.BanDuration parsedDuration = tryParseDuration(sanitizedDuration);
      if (parsedDuration == null) {
         throw ERROR_INVALID_DURATION.create(sanitizedDuration.isEmpty() ? "空值" : sanitizedDuration);
      } else {
         return parsedDuration;
      }
   }

   private static GreenManPunishmentService.BanDuration tryParseDuration(String durationText) {
      if (durationText != null && !durationText.isBlank()) {
         String normalizedDuration = durationText.trim().toLowerCase(Locale.ROOT);
         if (!"0".equals(normalizedDuration)
            && !"permanent".equals(normalizedDuration)
            && !"forever".equals(normalizedDuration)
            && !"永久".equals(normalizedDuration)) {
            if (normalizedDuration.length() < 2) {
               return null;
            } else {
               char durationUnit = normalizedDuration.charAt(normalizedDuration.length() - 1);
               String durationAmountText = normalizedDuration.substring(0, normalizedDuration.length() - 1);
               if (!durationAmountText.isEmpty() && durationAmountText.chars().allMatch(Character::isDigit)) {
                  long durationAmount;
                  try {
                     durationAmount = Long.parseLong(durationAmountText);
                  } catch (NumberFormatException var12) {
                     return null;
                  }

                  if (durationAmount <= 0L) {
                     return null;
                  } else {
                     long millisecondsPerUnit = switch (durationUnit) {
                        case 'd' -> 86400000L;
                        case 'h' -> 3600000L;
                        case 'm' -> 60000L;
                        case 's' -> 1000L;
                        default -> -1L;
                     };
                     if (millisecondsPerUnit < 0L) {
                        return null;
                     } else {
                        long durationMilliseconds;
                        try {
                           durationMilliseconds = Math.multiplyExact(durationAmount, millisecondsPerUnit);
                        } catch (ArithmeticException var11) {
                           return null;
                        }

                        return durationMilliseconds > MAXIMUM_BAN_DURATION_MILLISECONDS
                           ? null
                           : new GreenManPunishmentService.BanDuration(durationMilliseconds, false);
                     }
                  }
               } else {
                  return null;
               }
            }
         } else {
            return GreenManPunishmentService.BanDuration.permanentDuration();
         }
      } else {
         return null;
      }
   }

   private static int findFirstWhitespaceIndex(String text) {
      if (text != null && !text.isEmpty()) {
         for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            if (Character.isWhitespace(text.charAt(characterIndex))) {
               return characterIndex;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private static String formatExpirationTime(Date expirationDate) {
      return expirationDate == null ? "永久" : EXPIRATION_TIME_FORMATTER.format(expirationDate.toInstant());
   }

   private static String sanitizeSingleLineText(String rawText, int maximumCodePoints) {
      return sanitizeMultilineText(rawText, maximumCodePoints).replace('\n', ' ').trim();
   }

   private static String sanitizeMultilineText(String rawText, int maximumCodePoints) {
      if (rawText != null && maximumCodePoints > 0) {
         StringBuilder sanitizedTextBuilder = new StringBuilder();
         int acceptedCodePointCount = 0;
         int characterOffset = 0;

         while (characterOffset < rawText.length() && acceptedCodePointCount < maximumCodePoints) {
            int currentCodePoint = rawText.codePointAt(characterOffset);
            characterOffset += Character.charCount(currentCodePoint);
            if (currentCodePoint != 13 && (currentCodePoint == 10 || !Character.isISOControl(currentCodePoint) && currentCodePoint != 167)) {
               sanitizedTextBuilder.appendCodePoint(currentCodePoint);
               acceptedCodePointCount++;
            }
         }

         return sanitizedTextBuilder.toString().trim();
      } else {
         return "";
      }
   }

   private static String defaultIfEmpty(String text, String fallbackText) {
      return text != null && !text.isEmpty() ? text : fallbackText;
   }

   private record BanDuration(long durationMilliseconds, boolean permanent) {
      private String inputText() {
         return this.permanent ? "forever" : Math.max(1L, this.durationMilliseconds / 1000L) + "s";
      }

      private String displayText() {
         if (this.permanent) {
            return "永久";
         } else {
            long totalSeconds = Math.max(1L, this.durationMilliseconds / 1000L);
            long days = totalSeconds / 86400L;
            long hours = totalSeconds % 86400L / 3600L;
            long minutes = totalSeconds % 3600L / 60L;
            long seconds = totalSeconds % 60L;
            StringBuilder durationBuilder = new StringBuilder();
            if (days > 0L) {
               durationBuilder.append(days).append("天");
            }

            if (hours > 0L) {
               durationBuilder.append(hours).append("小时");
            }

            if (minutes > 0L) {
               durationBuilder.append(minutes).append("分钟");
            }

            if (seconds > 0L || durationBuilder.length() == 0) {
               durationBuilder.append(seconds).append("秒");
            }

            return durationBuilder.toString();
         }
      }

      private static GreenManPunishmentService.BanDuration permanentDuration() {
         return new GreenManPunishmentService.BanDuration(0L, true);
      }

      private Date toExpirationDate(Date createdDate) throws CommandSyntaxException {
         if (this.permanent) {
            return null;
         } else {
            Date effectiveCreatedDate = createdDate == null ? new Date() : createdDate;

            long expirationEpochMilliseconds;
            try {
               expirationEpochMilliseconds = Math.addExact(effectiveCreatedDate.getTime(), this.durationMilliseconds);
            } catch (ArithmeticException var7) {
               throw GreenManPunishmentService.ERROR_INVALID_DURATION.create(this.durationMilliseconds + "ms");
            }

            try {
               return Date.from(Instant.ofEpochMilli(expirationEpochMilliseconds));
            } catch (RuntimeException var6) {
               throw GreenManPunishmentService.ERROR_INVALID_DURATION.create(this.durationMilliseconds + "ms");
            }
         }
      }
   }
}
