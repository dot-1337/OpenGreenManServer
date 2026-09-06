package shit.shmily.punishment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import shit.shmily.GreenManServer;
import shit.shmily.chat.GreenManChatHistory;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.text.GreenManTextFormatter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents.AllowChatMessage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManMuteService {
   private static final Path MUTE_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-mutes.json");
   private static final Path MUTE_TEMP_PATH = MUTE_PATH.resolveSibling("greenmanserver-mutes.json.tmp");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.of("Asia/Shanghai"));
   private static final Map<UUID, GreenManMuteService.MuteEntry> MUTES = new LinkedHashMap<>();
   private static boolean loaded;
   private static boolean registered;
   private static final SimpleCommandExceptionType ERROR_NO_TARGET = new SimpleCommandExceptionType(Text.literal("没有可操作的禁言目标"));

   private GreenManMuteService() {
   }

   public static synchronized void register() {
      if (!registered) {
         registered = true;
         load();
         ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((AllowChatMessage)(message, sender, chatType) -> allowChatMessage(message, sender));
         ServerTickEvents.END_SERVER_TICK.register(GreenManMuteService::tick);
         ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> notifyMutedPlayer(handler.getPlayer()));
      }
   }

   private static boolean allowChatMessage(SignedMessage message, ServerPlayerEntity sender) {
      if (message != null && sender != null && GreenManServerConfig.isFeatureEnabled("mute")) {
         GreenManMuteService.MuteEntry muteEntry = getActiveMute(sender.getUuid());
         if (muteEntry == null) {
            return true;
         } else {
            sender.sendMessage(
               Text.literal("您已被禁言，原因：" + muteEntry.reason + "，解禁时间：" + formatExpiration(toInstant(muteEntry.expiresAtMillis)))
                  .formatted(Formatting.RED)
            );
            sendActionBar(sender, "您已被禁言，解禁时间：" + formatExpiration(toInstant(muteEntry.expiresAtMillis)));
            return false;
         }
      } else {
         return true;
      }
   }

   private static void notifyMutedPlayer(ServerPlayerEntity player) {
      if (player != null && GreenManServerConfig.isFeatureEnabled("mute")) {
         GreenManMuteService.MuteEntry muteEntry = getActiveMute(player.getUuid());
         if (muteEntry != null) {
            sendActionBar(player, "您已被禁言，解禁时间：" + formatExpiration(toInstant(muteEntry.expiresAtMillis)));
         }
      }
   }

   private static void tick(MinecraftServer server) {
      if (server != null && !MUTES.isEmpty()) {
         if (server.getTicks() % 40 == 0) {
            boolean changed = false;
            Instant now = Instant.now();

            for (UUID playerId : List.copyOf(MUTES.keySet())) {
               GreenManMuteService.MuteEntry muteEntry = MUTES.get(playerId);
               if (muteEntry != null && muteEntry.expiresAtMillis != null && !now.isBefore(Instant.ofEpochMilli(muteEntry.expiresAtMillis))) {
                  MUTES.remove(playerId);
                  changed = true;
               }
            }

            if (changed) {
               save();
            }
         }
      }
   }

   public static int mutePlayers(ServerCommandSource source, Collection<PlayerConfigEntry> targetProfiles, String durationText, String reasonText) throws CommandSyntaxException {
      if (source != null && source.getServer() != null && targetProfiles != null && !targetProfiles.isEmpty()) {
         GreenManMuteService.BanDuration duration = GreenManMuteService.BanDuration.parse(durationText);
         if (duration == null) {
            throw new SimpleCommandExceptionType(Text.literal("禁言时长无效，可用 30s、10m、2h、7d、0 或 permanent")).create();
         } else {
            String reason = sanitize(reasonText).isEmpty() ? "未填写原因" : sanitize(reasonText);
            String operator = sanitize(source.getName()).isEmpty() ? "Server" : sanitize(source.getName());
            int operatorLevel = getSourcePermissionLevel(source);
            int successCount = 0;
            List<GreenManMuteService.MuteEntry> newlyMutedEntries = new ArrayList<>();
            Instant createdAt = Instant.now();
            Instant expiresAt = duration.toExpirationInstant(createdAt);

            for (PlayerConfigEntry targetProfile : targetProfiles) {
               if (targetProfile != null && targetProfile.id() != null) {
                  if (operatorLevel < 4 && getPlayerPermissionLevel(source.getServer(), targetProfile.id()) >= operatorLevel) {
                     source.sendError(Text.literal("不能禁言权限等级相同或更高的管理员：" + targetProfile.name()));
                  } else {
                     GreenManMuteService.MuteEntry muteEntry = new GreenManMuteService.MuteEntry();
                     muteEntry.playerId = targetProfile.id();
                     muteEntry.playerName = sanitize(targetProfile.name());
                     muteEntry.createdAtMillis = createdAt.toEpochMilli();
                     muteEntry.expiresAtMillis = expiresAt == null ? null : expiresAt.toEpochMilli();
                     muteEntry.reason = reason;
                     muteEntry.operator = operator;
                     MUTES.put(targetProfile.id(), muteEntry);
                     successCount++;
                     newlyMutedEntries.add(muteEntry);
                     ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetProfile.id());
                     if (onlineTarget != null) {
                        onlineTarget.sendMessage(
                           Text.literal("您已被 " + operator + " 禁言，原因：" + reason + "，解禁时间：" + formatExpiration(expiresAt))
                              .formatted(Formatting.RED)
                        );
                        sendActionBar(onlineTarget, "您已被禁言，解禁时间：" + formatExpiration(expiresAt));
                     }
                  }
               }
            }

            if (successCount == 0) {
               throw ERROR_NO_TARGET.create();
            } else if (!save()) {
               for (GreenManMuteService.MuteEntry muteEntry : newlyMutedEntries) {
                  MUTES.remove(muteEntry.playerId);
               }

               throw new SimpleCommandExceptionType(Text.literal("禁言记录保存失败，请检查配置目录权限和日志")).create();
            } else {
               for (GreenManMuteService.MuteEntry muteEntry : newlyMutedEntries) {
                  broadcastMuteAnnouncement(source.getServer(), muteEntry, duration.displayText());
               }

               return successCount;
            }
         }
      } else {
         throw ERROR_NO_TARGET.create();
      }
   }

   public static int unmutePlayers(ServerCommandSource source, Collection<PlayerConfigEntry> targetProfiles) throws CommandSyntaxException {
      if (source != null && targetProfiles != null && !targetProfiles.isEmpty()) {
         int removedCount = 0;

         for (PlayerConfigEntry targetProfile : targetProfiles) {
            if (targetProfile != null && MUTES.remove(targetProfile.id()) != null) {
               removedCount++;
               source.sendFeedback(() -> Text.literal("已解除 " + targetProfile.name() + " 的禁言"), true);
            }
         }

         if (removedCount == 0) {
            throw new SimpleCommandExceptionType(Text.literal("所选玩家没有有效禁言记录")).create();
         } else if (!save()) {
            throw new SimpleCommandExceptionType(Text.literal("解除禁言后保存失败，请检查日志")).create();
         } else {
            return removedCount;
         }
      } else {
         throw ERROR_NO_TARGET.create();
      }
   }

   public static int showMuteList(ServerCommandSource source) {
      if (source == null) {
         return 0;
      } else {
         List<GreenManMuteService.MuteEntry> entries = new ArrayList<>(MUTES.values());
         entries.sort(Comparator.comparingLong(entryx -> entryx.createdAtMillis));
         if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("当前没有被禁言的玩家"), false);
            return 0;
         } else {
            for (GreenManMuteService.MuteEntry entry : entries) {
               source.sendFeedback(
                  () -> Text.literal(
                     "- "
                        + entry.playerName
                        + "（"
                        + entry.playerId
                        + "） | 禁言时间："
                        + formatExpiration(Instant.ofEpochMilli(entry.createdAtMillis))
                        + " | 解禁时间："
                        + formatExpiration(toInstant(entry.expiresAtMillis))
                        + " | 处理人："
                        + entry.operator
                        + " | 原因："
                        + entry.reason
                  ),
                  false
               );
            }

            return entries.size();
         }
      }
   }

   private static synchronized GreenManMuteService.MuteEntry getActiveMute(UUID playerId) {
      GreenManMuteService.MuteEntry muteEntry = MUTES.get(playerId);
      if (muteEntry == null) {
         return null;
      } else if (muteEntry.expiresAtMillis != null && !Instant.now().isBefore(Instant.ofEpochMilli(muteEntry.expiresAtMillis))) {
         MUTES.remove(playerId);
         save();
         return null;
      } else {
         return muteEntry;
      }
   }

   private static void sendActionBar(ServerPlayerEntity player, String message) {
      if (player != null && message != null && !message.isEmpty()) {
         player.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal(message).formatted(Formatting.RED)));
      }
   }

   private static void broadcastMuteAnnouncement(MinecraftServer server, GreenManMuteService.MuteEntry muteEntry, String durationText) {
      if (server != null && muteEntry != null) {
         String announcement = GreenManServerConfig.getMuteChatAnnouncementTemplate()
            .replace("{server_title}", GreenManServerConfig.getTabServerName())
            .replace("{player_name}", muteEntry.playerName)
            .replace("{player_id}", muteEntry.playerId.toString())
            .replace("{duration}", durationText)
            .replace("{reason}", muteEntry.reason)
            .replace("{operator}", muteEntry.operator)
            .replace("{created_at}", formatExpiration(Instant.ofEpochMilli(muteEntry.createdAtMillis)))
            .replace("{expires_at}", formatExpiration(toInstant(muteEntry.expiresAtMillis)));
         Text component = GreenManTextFormatter.parseRainbow(announcement, Style.EMPTY);
         server.getPlayerManager().broadcast(component, false);
         GreenManChatHistory.cacheSystemMessage(component.getString());
      }
   }

   private static synchronized void load() {
      if (!loaded) {
         loaded = true;

         try {
            label91: {
               Files.createDirectories(MUTE_PATH.getParent());
               if (Files.notExists(MUTE_PATH)) {
                  return;
               }

               try (Reader reader = Files.newBufferedReader(MUTE_PATH, StandardCharsets.UTF_8)) {
                  JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
                  if (root != null) {
                     for (Entry<String, JsonElement> rawEntry : root.entrySet()) {
                        try {
                           UUID playerId = UUID.fromString(rawEntry.getKey());
                           GreenManMuteService.MuteEntry muteEntry = (GreenManMuteService.MuteEntry)GSON.fromJson(
                              rawEntry.getValue(), GreenManMuteService.MuteEntry.class
                           );
                           if (muteEntry != null && muteEntry.playerId == null) {
                              muteEntry.playerId = playerId;
                           }

                           if (muteEntry != null) {
                              MUTES.put(playerId, muteEntry);
                           }
                        } catch (RuntimeException var7) {
                           GreenManServer.LOGGER.warn("跳过损坏的禁言记录 {}", rawEntry.getKey());
                        }
                     }
                     break label91;
                  }
               }

               return;
            }
         } catch (RuntimeException | IOException var9) {
            GreenManServer.LOGGER.error("读取禁言记录失败，将使用内存空记录", var9);
         }
      }
   }

   private static synchronized boolean save() {
      try {
         boolean var8;
         try (Writer writer = Files.newBufferedWriter(MUTE_TEMP_PATH, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonObject();

            for (Entry<UUID, GreenManMuteService.MuteEntry> entry : MUTES.entrySet()) {
               root.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
            }

            GSON.toJson(root, writer);
            Files.move(MUTE_TEMP_PATH, MUTE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            var8 = true;
         }

         return var8;
      } catch (IOException var7) {
         try {
            Files.move(MUTE_TEMP_PATH, MUTE_PATH, StandardCopyOption.REPLACE_EXISTING);
            return true;
         } catch (IOException var4) {
            GreenManServer.LOGGER.error("保存禁言记录失败", var4);
            return false;
         }
      }
   }

   private static int getSourcePermissionLevel(ServerCommandSource source) {
      if (source.getEntity() == null) {
         return 4;
      } else {
         return source.getEntity() instanceof ServerPlayerEntity player
            ? source.getServer().getPermissionLevel(new PlayerConfigEntry(player.getUuid(), player.getName().getString())).getLevel().getLevel()
            : 4;
      }
   }

   private static int getPlayerPermissionLevel(MinecraftServer server, UUID playerId) {
      return server != null && playerId != null ? server.getPermissionLevel(new PlayerConfigEntry(playerId, "")).getLevel().getLevel() : 0;
   }

   private static String sanitize(String text) {
      return text == null
         ? ""
         : text.codePoints()
            .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != 167)
            .limit(512L)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString()
            .trim();
   }

   private static String formatExpiration(Instant instant) {
      return instant == null ? "永久" : TIME_FORMATTER.format(instant);
   }

   private static Instant toInstant(Long epochMillis) {
      return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
   }

   private static final class BanDuration {
      private final long durationSeconds;

      private BanDuration(long durationSeconds) {
         this.durationSeconds = durationSeconds;
      }

      private static GreenManMuteService.BanDuration parse(String rawDuration) {
         if (rawDuration != null && !rawDuration.isBlank()) {
            String normalized = rawDuration.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals("0") && !normalized.equals("permanent") && !normalized.equals("永久")) {
               try {
                  char unit = normalized.charAt(normalized.length() - 1);
                  long amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
                  if (amount <= 0L) {
                     return null;
                  } else {
                     long multiplier = switch (unit) {
                        case 'd' -> 86400L;
                        case 'h' -> 3600L;
                        case 'm' -> 60L;
                        case 's' -> 1L;
                        default -> -1L;
                     };
                     return multiplier > 0L && amount <= Long.MAX_VALUE / multiplier
                        ? new GreenManMuteService.BanDuration(Math.min(amount * multiplier, 3153600000L))
                        : null;
                  }
               } catch (RuntimeException var7) {
                  return null;
               }
            } else {
               return new GreenManMuteService.BanDuration(0L);
            }
         } else {
            return null;
         }
      }

      private Instant toExpirationInstant(Instant createdAt) {
         return this.durationSeconds > 0L && createdAt != null ? createdAt.plusSeconds(this.durationSeconds) : null;
      }

      private String displayText() {
         if (this.durationSeconds <= 0L) {
            return "永久";
         } else if (this.durationSeconds % 86400L == 0L) {
            return this.durationSeconds / 86400L + "天";
         } else if (this.durationSeconds % 3600L == 0L) {
            return this.durationSeconds / 3600L + "小时";
         } else {
            return this.durationSeconds % 60L == 0L ? this.durationSeconds / 60L + "分钟" : this.durationSeconds + "秒";
         }
      }
   }

   private static final class MuteEntry {
      private UUID playerId;
      private String playerName;
      private long createdAtMillis;
      private Long expiresAtMillis;
      private String reason;
      private String operator;
   }
}
