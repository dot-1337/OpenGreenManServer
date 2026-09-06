package shit.shmily.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import shit.shmily.GreenManServer;
import shit.shmily.text.GreenManTextFormatter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManServerConfig {
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver.json");
   private static final Path TEMP_CONFIG_PATH = CONFIG_PATH.resolveSibling("greenmanserver.json.tmp");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int MAX_SERVER_NAME_CODE_POINTS = 48;
   private static final int MAX_TITLE_CODE_POINTS = 24;
   private static final int MIN_CHAT_MAX_LENGTH = 1;
   private static final int MAX_CHAT_MAX_LENGTH = 256;
   private static final int MAX_CHAT_COOLDOWN_SECONDS = 60;
   private static final int MAX_CHAT_REPEAT_WINDOW_SECONDS = 300;
   private static final int MIN_CHAT_ARCHIVE_RETENTION_DAYS = 1;
   private static final int MAX_CHAT_ARCHIVE_RETENTION_DAYS = 3650;
   private static final int MIN_CHAT_ARCHIVE_MAX_FILE_SIZE_MIB = 1;
   private static final int MAX_CHAT_ARCHIVE_MAX_FILE_SIZE_MIB = 1024;
   private static final int MIN_CHAT_HISTORY_CACHE_SIZE = 0;
   private static final int MAX_CHAT_HISTORY_CACHE_SIZE = 500;
   private static final int MIN_GLOBAL_MENTION_WINDOW_SECONDS = 1;
   private static final int MAX_GLOBAL_MENTION_WINDOW_SECONDS = 3600;
   private static final int MIN_GLOBAL_MENTION_MAX_COUNT = 1;
   private static final int MAX_GLOBAL_MENTION_MAX_COUNT = 20;
   private static final int MAX_ANNOUNCEMENT_CODE_POINTS = 2048;
   private static final int MAX_ANNOUNCEMENT_TITLE_CODE_POINTS = 128;
   private static final int MAX_PUNISHMENT_LABEL_CODE_POINTS = 128;
   private static final int MAX_JOIN_MESSAGE_CODE_POINTS = 512;
   private static final int MAX_JOIN_MUSIC_FILE_CODE_POINTS = 128;
   private static final int MAX_RESOURCE_PACK_URL_CODE_POINTS = 2048;
   private static final int MAX_RESOURCE_PACK_PUBLIC_HOST_CODE_POINTS = 253;
   private static final int MAX_JOIN_VANILLA_SOUND_COUNT = 8;
   private static final int MIN_JOIN_SOUND_DELAY_SECONDS = 0;
   private static final int MAX_JOIN_SOUND_DELAY_SECONDS = 60;
   private static final int MIN_VOTE_DURATION_SECONDS = 1;
   private static final int MAX_VOTE_DURATION_SECONDS = 86400;
   private static final int MIN_VOTE_OPTION_COUNT = 2;
   private static final int MAX_VOTE_OPTION_COUNT = 10;
   private static final int MIN_MEMORY_THRESHOLD_PERCENT = 60;
   private static final int MAX_MEMORY_THRESHOLD_PERCENT = 95;
   private static final int MIN_MEMORY_GC_COOLDOWN_SECONDS = 1;
   private static final int MAX_MEMORY_GC_COOLDOWN_SECONDS = 3600;
   private static final int MIN_ITEM_CLEAR_INTERVAL_MINUTES = 1;
   private static final int MAX_ITEM_CLEAR_INTERVAL_MINUTES = 1000000;
   private static final int MIN_ITEM_CLEAR_COUNTDOWN_SECONDS = 1;
   private static final int MAX_ITEM_CLEAR_COUNTDOWN_SECONDS = 60;
   public static final String FEATURE_TAB_STATUS = "tabStatus";
   public static final String FEATURE_TITLE = "title";
   public static final String FEATURE_LATENCY_DISPLAY = "latencyDisplay";
   public static final String FEATURE_COMPOSTER = "composter";
   public static final String FEATURE_MEMORY = "memory";
   public static final String FEATURE_CHAT_LIMIT = "chatLimit";
   public static final String FEATURE_CHAT_ARCHIVE = "chatArchive";
   public static final String FEATURE_MENTIONS = "mentions";
   public static final String FEATURE_MENTION_ACTION_BAR = "mentionActionBar";
   public static final String FEATURE_DISPENSER_RECIPE = "dispenserRecipe";
   public static final String FEATURE_ENTITY_UNLOAD_PROTECTION = "entityUnloadProtection";
   public static final String FEATURE_VANILLA_REDSTONE = "vanillaRedstone";
   public static final String FEATURE_ANVIL_NAME_COLOR = "anvilNameColor";
   public static final String FEATURE_DEBUG_LOGGING = "debugLogging";
   public static final String FEATURE_PUNISHMENT_TEMPLATES = "punishmentTemplates";
   public static final String FEATURE_ANNOUNCEMENT = "announcement";
   public static final String FEATURE_CHAT_HISTORY = "chatHistory";
   public static final String FEATURE_MEMBER_GLOBAL_MENTION = "memberGlobalMention";
   public static final String FEATURE_MOD_NETWORK_CHECKS = "modNetworkChecks";
   public static final String FEATURE_ASYNC_CHUNK_IO = "asyncChunkIo";
   public static final String FEATURE_JOIN_MESSAGE = "joinMessage";
   public static final String FEATURE_JOIN_SOUND = "joinSound";
   public static final String FEATURE_BAN_CHAT_ANNOUNCEMENT = "banChatAnnouncement";
   public static final String FEATURE_GRIM_ANTICHEAT = "grimAnticheat";
   public static final String FEATURE_GRIM_SAFEWALK_EXEMPT = "grimSafeWalkExempt";
   public static final String FEATURE_VOTING = "voting";
   public static final String FEATURE_MUTE = "mute";
   public static final String FEATURE_ITEM_CLEAR = "itemClear";
   public static final String FEATURE_ELYTRA_MOVEMENT_BYPASS = "elytraMovementBypass";
   private static GreenManServerConfig.ConfigData currentConfig = createDefaultConfig();

   private GreenManServerConfig() {
   }

   public static void load() {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());
      } catch (IOException var4) {
         GreenManServer.LOGGER.error("无法创建 GreenManServer 配置目录，将使用默认配置", var4);
         currentConfig = createDefaultConfig();
         return;
      }

      if (Files.notExists(CONFIG_PATH)) {
         currentConfig = createDefaultConfig();
         save();
      } else {
         try (Reader configReader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonObject loadedJson = (JsonObject)GSON.fromJson(configReader, JsonObject.class);
            currentConfig = sanitizeConfig(loadedJson);
            if (requiresConfigMigration(loadedJson) || !hasLineComments(CONFIG_PATH)) {
               save();
            }
         } catch (RuntimeException | IOException var6) {
            GreenManServer.LOGGER.error("无法读取 GreenManServer 配置，将使用默认配置", var6);
            currentConfig = createDefaultConfig();
         }
      }
   }

   private static boolean hasLineComments(Path configPath) {
      if (configPath != null && !Files.notExists(configPath)) {
         try {
            return Files.readAllLines(configPath, StandardCharsets.UTF_8)
               .stream()
               .anyMatch(configLine -> configLine != null && configLine.trim().startsWith("//"));
         } catch (IOException var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean requiresConfigMigration(JsonObject loadedJson) {
      return loadedJson == null
         ? true
         : loadedJson.has("_中文说明_全部配置项")
            || loadedJson.has("_中文说明_功能开关")
            || loadedJson.has("_中文说明_自动GC冷却")
            || loadedJson.has("_中文说明_处罚模板")
            || loadedJson.has("_中文说明_公告与聊天历史")
            || loadedJson.has("_中文说明_成员全服提醒")
            || loadedJson.has("_中文说明_模组网络检查")
            || !loadedJson.has("tabServerName")
            || !loadedJson.has("announcementTitle")
            || !loadedJson.has("announcementShowEveryJoin")
            || !loadedJson.has("punishmentServerTitle")
            || !loadedJson.has("banActionText")
            || !loadedJson.has("kickActionText")
            || !loadedJson.has("mentionActionBarEnabled")
            || !loadedJson.has("joinMessageEnabled")
            || !loadedJson.has("joinSoundEnabled")
            || !loadedJson.has("joinMusicResourcePackUrl")
            || !loadedJson.has("joinMusicPublicHost")
            || !loadedJson.has("joinMusicPublicPort")
            || !loadedJson.has("joinMusicPublicHttps")
            || !loadedJson.has("joinMusicHttpPort")
            || isLegacyJoinBroadcastTemplate(loadedJson)
            || !loadedJson.has("punishmentChatBanAnnouncementEnabled")
            || !loadedJson.has("punishmentChatBanAnnouncementTemplate")
            || GreenManBanWhitelistConfig.canRemoveLegacyFields() && (loadedJson.has("banWhitelistPlayers") || loadedJson.has("banWhitelistIpAddresses"))
            || !loadedJson.has("grimAnticheatEnabled")
            || !loadedJson.has("grimFirstBanDuration")
            || !loadedJson.has("grimSecondBanDuration")
            || !loadedJson.has("grimThirdBanDuration")
            || !loadedJson.has("grimPermanentIpBanAfterCount")
            || !loadedJson.has("grimStreakKickThreshold")
            || !loadedJson.has("grimAlertLogCooldownSeconds")
            || !loadedJson.has("grimSafeWalkExemptEnabled")
            || !loadedJson.has("punishmentBanSoundEnabled")
            || !loadedJson.has("punishmentBanSoundMode")
            || !loadedJson.has("punishmentBanVanillaSoundIds")
            || !loadedJson.has("punishmentBanInternalMusicFile")
            || !loadedJson.has("votingEnabled")
            || !loadedJson.has("voteResultsDetailEnabled")
            || !loadedJson.has("voteMaxDurationSeconds")
            || !loadedJson.has("voteMaxOptionCount")
            || !loadedJson.has("muteEnabled")
            || !loadedJson.has("muteChatAnnouncementTemplate")
            || !loadedJson.has("itemClearEnabled")
            || !loadedJson.has("itemClearIntervalMinutes")
            || !loadedJson.has("itemClearCountdownSeconds")
            || !loadedJson.has("elytraMovementCheckBypassEnabled")
            || !loadedJson.has("asyncChunkIoEnabled")
            || !loadedJson.has("scheduledAnnouncementEnabled")
            || !loadedJson.has("scheduledAnnouncementMode")
            || !loadedJson.has("scheduledAnnouncementIntervalMinutes")
            || !loadedJson.has("scheduledAnnouncementTime")
            || !loadedJson.has("scheduledAnnouncementWeekday")
            || !loadedJson.has("scheduledAnnouncementMonthDay")
            || !loadedJson.has("scheduledAnnouncementTitle")
            || !loadedJson.has("scheduledAnnouncementText")
            || !loadedJson.has("joinMessageAllowedPlayerUuids")
            || !loadedJson.has("joinSoundBroadcastEnabled")
            || !loadedJson.has("joinVanillaSoundIds")
            || !loadedJson.has("joinSoundDelaySeconds");
   }

   private static boolean isLegacyJoinBroadcastTemplate(JsonObject loadedJson) {
      return loadedJson != null && loadedJson.has("joinMessageBroadcastTemplate") && loadedJson.get("joinMessageBroadcastTemplate").isJsonPrimitive()
         ? "&e{player} &a进入了服务器，当前在线 {online_count} 人。".equals(loadedJson.get("joinMessageBroadcastTemplate").getAsString())
         : false;
   }

   public static boolean save() {
      try (Writer configWriter = Files.newBufferedWriter(TEMP_CONFIG_PATH, StandardCharsets.UTF_8)) {
         configWriter.write(createCommentedConfigJson());
      } catch (IOException var7) {
         GreenManServer.LOGGER.error("无法保存 GreenManServer 配置", var7);
         return false;
      }

      try {
         Files.move(TEMP_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         return true;
      } catch (IOException var5) {
         try {
            Files.move(TEMP_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            return true;
         } catch (IOException var3) {
            GreenManServer.LOGGER.error("无法替换 GreenManServer 配置文件", var3);
            return false;
         }
      }
   }

   private static String createCommentedConfigJson() {
      JsonObject configObject = JsonParser.parseString(GSON.toJson(currentConfig)).getAsJsonObject();
      configObject.remove("_中文说明_全部配置项");
      configObject.remove("_中文说明_功能开关");
      configObject.remove("_中文说明_自动GC冷却");
      configObject.remove("_中文说明_处罚模板");
      configObject.remove("_中文说明_公告与聊天历史");
      configObject.remove("_中文说明_成员全服提醒");
      configObject.remove("_中文说明_模组网络检查");
      String prettyJson = GSON.toJson(configObject);
      Map<String, String> configComments = createAllConfigChineseComments();
      Pattern topLevelPropertyPattern = Pattern.compile("^  \\\"([^\\\"]+)\\\":.*$");
      StringBuilder commentedJson = new StringBuilder(prettyJson.length() + configComments.size() * 80);
      commentedJson.append("// GreenManServer配置文件：每个配置项上方的中文说明仅供阅读，请勿删除配置项名称。\n");

      for (String jsonLine : prettyJson.split("\\n", -1)) {
         Matcher propertyMatcher = topLevelPropertyPattern.matcher(jsonLine);
         if (propertyMatcher.matches()) {
            String propertyName = propertyMatcher.group(1);
            String propertyComment = configComments.get(propertyName);
            if (propertyComment != null && !propertyComment.isBlank()) {
               commentedJson.append("  // ").append(propertyComment).append('\n');
            }
         }

         commentedJson.append(jsonLine).append('\n');
      }

      return commentedJson.toString().replaceFirst("\\n$", "") + System.lineSeparator();
   }

   public static String getTabServerName() {
      return currentConfig.tabServerName;
   }

   public static boolean setTabServerName(String rawServerName) {
      String sanitizedServerName = sanitizeText(rawServerName, 48);
      if (sanitizedServerName.isEmpty()) {
         return false;
      } else {
         currentConfig.tabServerName = sanitizedServerName;
         return save();
      }
   }

   public static boolean isChatLimitEnabled() {
      return currentConfig.chatLimitEnabled;
   }

   public static boolean isFeatureEnabled(String featureName) {
      if (featureName != null && !featureName.isEmpty()) {
         return switch (featureName) {
            case "tabStatus" -> currentConfig.tabStatusEnabled;
            case "title" -> currentConfig.titleEnabled;
            case "latencyDisplay" -> currentConfig.latencyDisplayEnabled;
            case "composter" -> currentConfig.composterEnabled;
            case "memory" -> currentConfig.memoryOptimizationEnabled;
            case "chatLimit" -> currentConfig.chatLimitEnabled;
            case "chatArchive" -> currentConfig.chatArchiveEnabled;
            case "mentions" -> currentConfig.mentionsEnabled;
            case "mentionActionBar" -> currentConfig.mentionActionBarEnabled;
            case "dispenserRecipe" -> currentConfig.dispenserRecipeEnabled;
            case "entityUnloadProtection" -> currentConfig.entityUnloadProtectionEnabled;
            case "vanillaRedstone" -> currentConfig.vanillaRedstoneProtectionEnabled;
            case "anvilNameColor" -> currentConfig.anvilNameColorEnabled;
            case "debugLogging" -> currentConfig.debugLoggingEnabled;
            case "punishmentTemplates" -> currentConfig.punishmentTemplatesEnabled;
            case "announcement" -> currentConfig.announcementEnabled;
            case "chatHistory" -> currentConfig.chatHistoryEnabled;
            case "memberGlobalMention" -> currentConfig.memberGlobalMentionEnabled;
            case "modNetworkChecks" -> currentConfig.disableModNetworkChecks;
            case "asyncChunkIo" -> currentConfig.asyncChunkIoEnabled;
            case "joinMessage" -> currentConfig.joinMessageEnabled;
            case "joinSound" -> currentConfig.joinSoundEnabled;
            case "banChatAnnouncement" -> currentConfig.punishmentChatBanAnnouncementEnabled;
            case "grimAnticheat" -> currentConfig.grimAnticheatEnabled;
            case "grimSafeWalkExempt" -> currentConfig.grimSafeWalkExemptEnabled;
            case "voting" -> currentConfig.votingEnabled;
            case "mute" -> currentConfig.muteEnabled;
            case "itemClear" -> currentConfig.itemClearEnabled;
            case "elytraMovementBypass" -> currentConfig.elytraMovementCheckBypassEnabled;
            default -> false;
         };
      } else {
         return false;
      }
   }

   public static boolean setFeatureEnabled(String featureName, boolean enabled) {
      if (featureName != null && !featureName.isEmpty()) {
         switch (featureName) {
            case "tabStatus":
               currentConfig.tabStatusEnabled = enabled;
               break;
            case "title":
               currentConfig.titleEnabled = enabled;
               break;
            case "latencyDisplay":
               currentConfig.latencyDisplayEnabled = enabled;
               break;
            case "composter":
               currentConfig.composterEnabled = enabled;
               break;
            case "memory":
               currentConfig.memoryOptimizationEnabled = enabled;
               break;
            case "chatLimit":
               currentConfig.chatLimitEnabled = enabled;
               break;
            case "chatArchive":
               currentConfig.chatArchiveEnabled = enabled;
               break;
            case "mentions":
               currentConfig.mentionsEnabled = enabled;
               break;
            case "mentionActionBar":
               currentConfig.mentionActionBarEnabled = enabled;
               break;
            case "dispenserRecipe":
               currentConfig.dispenserRecipeEnabled = enabled;
               break;
            case "entityUnloadProtection":
               currentConfig.entityUnloadProtectionEnabled = enabled;
               break;
            case "vanillaRedstone":
               currentConfig.vanillaRedstoneProtectionEnabled = enabled;
               break;
            case "anvilNameColor":
               currentConfig.anvilNameColorEnabled = enabled;
               break;
            case "debugLogging":
               currentConfig.debugLoggingEnabled = enabled;
               break;
            case "punishmentTemplates":
               currentConfig.punishmentTemplatesEnabled = enabled;
               break;
            case "announcement":
               currentConfig.announcementEnabled = enabled;
               break;
            case "chatHistory":
               currentConfig.chatHistoryEnabled = enabled;
               break;
            case "memberGlobalMention":
               currentConfig.memberGlobalMentionEnabled = enabled;
               break;
            case "modNetworkChecks":
               currentConfig.disableModNetworkChecks = enabled;
               break;
            case "asyncChunkIo":
               currentConfig.asyncChunkIoEnabled = enabled;
               break;
            case "joinMessage":
               currentConfig.joinMessageEnabled = enabled;
               break;
            case "joinSound":
               currentConfig.joinSoundEnabled = enabled;
               break;
            case "banChatAnnouncement":
               currentConfig.punishmentChatBanAnnouncementEnabled = enabled;
               break;
            case "grimAnticheat":
               currentConfig.grimAnticheatEnabled = enabled;
               break;
            case "grimSafeWalkExempt":
               currentConfig.grimSafeWalkExemptEnabled = enabled;
               break;
            case "voting":
               currentConfig.votingEnabled = enabled;
               break;
            case "mute":
               currentConfig.muteEnabled = enabled;
               break;
            case "itemClear":
               currentConfig.itemClearEnabled = enabled;
               break;
            case "elytraMovementBypass":
               currentConfig.elytraMovementCheckBypassEnabled = enabled;
               break;
            default:
               return false;
         }

         return save();
      } else {
         return false;
      }
   }

   public static boolean resetTemplates(String templateScope) {
      if (templateScope != null && !templateScope.isBlank()) {
         GreenManServerConfig.ConfigData defaultConfig = createDefaultConfig();
         String normalizedScope = templateScope.trim().toLowerCase(Locale.ROOT);

         normalizedScope = switch (normalizedScope) {
            case "全部", "所有", "all" -> "all";
            case "处罚", "封禁", "ban", "punishment" -> "punishment";
            case "欢迎", "进服", "join" -> "join";
            case "公告", "scheduled", "announcement" -> "announcement";
            case "禁言", "mute" -> "mute";
            default -> normalizedScope;
         };
         switch (normalizedScope) {
            case "all":
               applyDefaultPunishmentTemplates(defaultConfig);
               applyDefaultJoinTemplates(defaultConfig);
               currentConfig.announcementTitle = defaultConfig.announcementTitle;
               currentConfig.announcementText = defaultConfig.announcementText;
               currentConfig.scheduledAnnouncementTitle = defaultConfig.scheduledAnnouncementTitle;
               currentConfig.scheduledAnnouncementText = defaultConfig.scheduledAnnouncementText;
               currentConfig.muteChatAnnouncementTemplate = defaultConfig.muteChatAnnouncementTemplate;
               break;
            case "punishment":
               applyDefaultPunishmentTemplates(defaultConfig);
               break;
            case "join":
               applyDefaultJoinTemplates(defaultConfig);
               break;
            case "announcement":
               currentConfig.announcementTitle = defaultConfig.announcementTitle;
               currentConfig.announcementText = defaultConfig.announcementText;
               currentConfig.scheduledAnnouncementTitle = defaultConfig.scheduledAnnouncementTitle;
               currentConfig.scheduledAnnouncementText = defaultConfig.scheduledAnnouncementText;
               break;
            case "mute":
               currentConfig.muteChatAnnouncementTemplate = defaultConfig.muteChatAnnouncementTemplate;
               break;
            default:
               return false;
         }

         if (save()) {
            return true;
         } else {
            load();
            return false;
         }
      } else {
         return false;
      }
   }

   private static void applyDefaultPunishmentTemplates(GreenManServerConfig.ConfigData defaultConfig) {
      if (defaultConfig != null) {
         currentConfig.punishmentServerTitle = defaultConfig.punishmentServerTitle;
         currentConfig.banActionText = defaultConfig.banActionText;
         currentConfig.kickActionText = defaultConfig.kickActionText;
         currentConfig.banScreenTemplate = defaultConfig.banScreenTemplate;
         currentConfig.kickScreenTemplate = defaultConfig.kickScreenTemplate;
         currentConfig.punishmentSolutionText = defaultConfig.punishmentSolutionText;
         currentConfig.punishmentChatBanAnnouncementTemplate = defaultConfig.punishmentChatBanAnnouncementTemplate;
      }
   }

   private static void applyDefaultJoinTemplates(GreenManServerConfig.ConfigData defaultConfig) {
      if (defaultConfig != null) {
         currentConfig.joinMessagePersonalTemplate = defaultConfig.joinMessagePersonalTemplate;
         currentConfig.joinMessageBroadcastTemplate = defaultConfig.joinMessageBroadcastTemplate;
         currentConfig.joinMessagePlayerNameStyle = defaultConfig.joinMessagePlayerNameStyle;
      }
   }

   public static String getFeatureNamesForDisplay() {
      return String.join(
         ", ",
         "tabStatus",
         "title",
         "latencyDisplay",
         "composter",
         "memory",
         "chatLimit",
         "chatArchive",
         "mentions",
         "mentionActionBar",
         "dispenserRecipe",
         "entityUnloadProtection",
         "vanillaRedstone",
         "anvilNameColor",
         "debugLogging",
         "punishmentTemplates",
         "announcement",
         "chatHistory",
         "memberGlobalMention",
         "modNetworkChecks",
         "asyncChunkIo",
         "joinMessage",
         "joinSound",
         "banChatAnnouncement",
         "grimAnticheat",
         "grimSafeWalkExempt",
         "voting",
         "mute",
         "itemClear",
         "elytraMovementBypass"
      );
   }

   public static String getFeatureDescription(String featureName) {
      return switch (featureName) {
         case "tabStatus" -> "TAB服务器状态";
         case "title" -> "玩家称号";
         case "latencyDisplay" -> "玩家延迟显示";
         case "composter" -> "堆肥桶高频填充";
         case "memory" -> "内存压力管理";
         case "chatLimit" -> "聊天限制";
         case "chatArchive" -> "聊天记录归档";
         case "mentions" -> "聊天提及";
         case "mentionActionBar" -> "被提及时的屏幕提醒";
         case "dispenserRecipe" -> "损耗弓发射器配方";
         case "entityUnloadProtection" -> "实体区块卸载保护";
         case "vanillaRedstone" -> "原版红石与TNT保护";
         case "anvilNameColor" -> "铁砧名称颜色";
         case "debugLogging" -> "保护拦截调试日志";
         case "punishmentTemplates" -> "封禁与踢出界面模板";
         case "announcement" -> "首次上线公告";
         case "chatHistory" -> "重新上线聊天回放";
         case "memberGlobalMention" -> "普通成员全服提醒";
         case "modNetworkChecks" -> "模组更新检查与遥测拦截";
         case "asyncChunkIo" -> "启动与关服区块异步IO协调";
         case "joinMessage" -> "玩家进服提示";
         case "joinSound" -> "玩家进服音效";
         case "banChatAnnouncement" -> "封禁聊天公告";
         case "grimAnticheat" -> "Grim反作弊联动";
         case "grimSafeWalkExempt" -> "SafeWalk免处罚";
         case "voting" -> "服务器投票";
         case "mute" -> "玩家禁言";
         case "itemClear" -> "掉落物自动清除";
         case "elytraMovementBypass" -> "原版鞘翅高速防拉回";
         default -> featureName == null ? "未知功能" : featureName;
      };
   }

   public static int getChatCooldownSeconds() {
      return currentConfig.chatCooldownSeconds;
   }

   public static int getChatMaxLength() {
      return currentConfig.chatMaxLength;
   }

   public static int getChatRepeatWindowSeconds() {
      return currentConfig.chatRepeatWindowSeconds;
   }

   public static boolean isChatArchiveEnabled() {
      return currentConfig.chatArchiveEnabled;
   }

   public static int getChatArchiveRetentionDays() {
      return currentConfig.chatArchiveRetentionDays;
   }

   public static int getChatArchiveMaxFileSizeMib() {
      return currentConfig.chatArchiveMaxFileSizeMib;
   }

   public static int getChatHistoryCacheSize() {
      return currentConfig.chatHistoryCacheSize;
   }

   public static boolean isChatHistoryEnabled() {
      return currentConfig.chatHistoryEnabled;
   }

   public static boolean setChatHistoryCacheSize(int cacheSize) {
      if (cacheSize >= 0 && cacheSize <= 500) {
         currentConfig.chatHistoryCacheSize = cacheSize;
         return save();
      } else {
         return false;
      }
   }

   public static boolean isAnnouncementEnabled() {
      return currentConfig.announcementEnabled;
   }

   public static String getAnnouncementText() {
      return currentConfig.announcementText;
   }

   public static String getAnnouncementTitle() {
      return currentConfig.announcementTitle;
   }

   public static boolean isScheduledAnnouncementEnabled() {
      return currentConfig.scheduledAnnouncementEnabled;
   }

   public static String getScheduledAnnouncementMode() {
      return currentConfig.scheduledAnnouncementMode;
   }

   public static int getScheduledAnnouncementIntervalMinutes() {
      return currentConfig.scheduledAnnouncementIntervalMinutes;
   }

   public static String getScheduledAnnouncementTime() {
      return currentConfig.scheduledAnnouncementTime;
   }

   public static int getScheduledAnnouncementWeekday() {
      return currentConfig.scheduledAnnouncementWeekday;
   }

   public static int getScheduledAnnouncementMonthDay() {
      return currentConfig.scheduledAnnouncementMonthDay;
   }

   public static String getScheduledAnnouncementTitle() {
      return currentConfig.scheduledAnnouncementTitle;
   }

   public static String getScheduledAnnouncementText() {
      return currentConfig.scheduledAnnouncementText;
   }

   public static boolean setScheduledAnnouncementEnabled(boolean enabled) {
      boolean previousValue = currentConfig.scheduledAnnouncementEnabled;
      currentConfig.scheduledAnnouncementEnabled = enabled;
      if (save()) {
         return true;
      } else {
         currentConfig.scheduledAnnouncementEnabled = previousValue;
         return false;
      }
   }

   public static boolean setScheduledAnnouncementMode(String mode) {
      String sanitizedMode = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
      if (!List.of("interval", "daily", "weekly", "monthly").contains(sanitizedMode)) {
         return false;
      } else {
         String previousValue = currentConfig.scheduledAnnouncementMode;
         currentConfig.scheduledAnnouncementMode = sanitizedMode;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementMode = previousValue;
            return false;
         }
      }
   }

   public static boolean setScheduledAnnouncementIntervalMinutes(int intervalMinutes) {
      if (intervalMinutes >= 1 && intervalMinutes <= 1000000) {
         int previousValue = currentConfig.scheduledAnnouncementIntervalMinutes;
         currentConfig.scheduledAnnouncementIntervalMinutes = intervalMinutes;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementIntervalMinutes = previousValue;
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean setScheduledAnnouncementTime(String timeText) {
      String sanitizedTime = timeText == null ? "" : timeText.trim();

      try {
         LocalTime.parse(sanitizedTime, DateTimeFormatter.ofPattern("HH:mm"));
      } catch (RuntimeException var3) {
         return false;
      }

      String previousValue = currentConfig.scheduledAnnouncementTime;
      currentConfig.scheduledAnnouncementTime = sanitizedTime;
      if (save()) {
         return true;
      } else {
         currentConfig.scheduledAnnouncementTime = previousValue;
         return false;
      }
   }

   public static boolean setScheduledAnnouncementWeekday(int weekday) {
      if (weekday >= 1 && weekday <= 7) {
         int previousValue = currentConfig.scheduledAnnouncementWeekday;
         currentConfig.scheduledAnnouncementWeekday = weekday;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementWeekday = previousValue;
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean setScheduledAnnouncementMonthDay(int monthDay) {
      if (monthDay >= 1 && monthDay <= 31) {
         int previousValue = currentConfig.scheduledAnnouncementMonthDay;
         currentConfig.scheduledAnnouncementMonthDay = monthDay;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementMonthDay = previousValue;
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean setScheduledAnnouncementContent(String titleText, String bodyText) {
      String sanitizedTitle = sanitizeText(titleText, 128);
      String sanitizedBody = sanitizeMultilineText(bodyText, 2048);
      if (!sanitizedTitle.isEmpty() && !sanitizedBody.isEmpty()) {
         String previousTitle = currentConfig.scheduledAnnouncementTitle;
         String previousBody = currentConfig.scheduledAnnouncementText;
         currentConfig.scheduledAnnouncementTitle = sanitizedTitle;
         currentConfig.scheduledAnnouncementText = sanitizedBody;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementTitle = previousTitle;
            currentConfig.scheduledAnnouncementText = previousBody;
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean configureScheduledAnnouncementInterval(int intervalMinutes) {
      if (intervalMinutes >= 1 && intervalMinutes <= 1000000) {
         String previousMode = currentConfig.scheduledAnnouncementMode;
         int previousInterval = currentConfig.scheduledAnnouncementIntervalMinutes;
         currentConfig.scheduledAnnouncementMode = "interval";
         currentConfig.scheduledAnnouncementIntervalMinutes = intervalMinutes;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementMode = previousMode;
            currentConfig.scheduledAnnouncementIntervalMinutes = previousInterval;
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean configureScheduledAnnouncementDaily(String timeText) {
      if (!isValidScheduledTime(timeText)) {
         return false;
      } else {
         String previousMode = currentConfig.scheduledAnnouncementMode;
         String previousTime = currentConfig.scheduledAnnouncementTime;
         currentConfig.scheduledAnnouncementMode = "daily";
         currentConfig.scheduledAnnouncementTime = timeText.trim();
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementMode = previousMode;
            currentConfig.scheduledAnnouncementTime = previousTime;
            return false;
         }
      }
   }

   public static boolean configureScheduledAnnouncementWeekly(int weekday, String timeText) {
      if (weekday >= 1 && weekday <= 7 && isValidScheduledTime(timeText)) {
         String previousMode = currentConfig.scheduledAnnouncementMode;
         String previousTime = currentConfig.scheduledAnnouncementTime;
         int previousWeekday = currentConfig.scheduledAnnouncementWeekday;
         currentConfig.scheduledAnnouncementMode = "weekly";
         currentConfig.scheduledAnnouncementTime = timeText.trim();
         currentConfig.scheduledAnnouncementWeekday = weekday;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementMode = previousMode;
            currentConfig.scheduledAnnouncementTime = previousTime;
            currentConfig.scheduledAnnouncementWeekday = previousWeekday;
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean configureScheduledAnnouncementMonthly(int monthDay, String timeText) {
      if (monthDay >= 1 && monthDay <= 31 && isValidScheduledTime(timeText)) {
         String previousMode = currentConfig.scheduledAnnouncementMode;
         String previousTime = currentConfig.scheduledAnnouncementTime;
         int previousMonthDay = currentConfig.scheduledAnnouncementMonthDay;
         currentConfig.scheduledAnnouncementMode = "monthly";
         currentConfig.scheduledAnnouncementTime = timeText.trim();
         currentConfig.scheduledAnnouncementMonthDay = monthDay;
         if (save()) {
            return true;
         } else {
            currentConfig.scheduledAnnouncementMode = previousMode;
            currentConfig.scheduledAnnouncementTime = previousTime;
            currentConfig.scheduledAnnouncementMonthDay = previousMonthDay;
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isValidScheduledTime(String timeText) {
      if (timeText != null && !timeText.isBlank()) {
         try {
            LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            return true;
         } catch (RuntimeException var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isJoinMessageBroadcastEnabled() {
      return currentConfig.joinMessageBroadcastEnabled;
   }

   public static boolean isJoinMessagePersonalEnabled() {
      return currentConfig.joinMessagePersonalEnabled;
   }

   public static boolean isJoinMessageAllowed(UUID playerUuid) {
      return playerUuid == null ? false : currentConfig.joinMessageAllowedPlayerUuids.contains(playerUuid.toString());
   }

   public static boolean addJoinMessageAllowedPlayer(UUID playerUuid) {
      if (playerUuid == null) {
         return false;
      } else {
         if (!currentConfig.joinMessageAllowedPlayerUuids.contains(playerUuid.toString())) {
            currentConfig.joinMessageAllowedPlayerUuids.add(playerUuid.toString());
         }

         return save();
      }
   }

   public static boolean removeJoinMessageAllowedPlayer(UUID playerUuid) {
      if (playerUuid == null) {
         return false;
      } else {
         currentConfig.joinMessageAllowedPlayerUuids.remove(playerUuid.toString());
         return save();
      }
   }

   public static boolean updateJoinMessageAllowedPlayers(Collection<UUID> playerUuids, boolean allowed) {
      if (playerUuids != null && !playerUuids.isEmpty()) {
         for (UUID playerUuid : playerUuids) {
            if (playerUuid != null) {
               String playerUuidText = playerUuid.toString();
               if (allowed && !currentConfig.joinMessageAllowedPlayerUuids.contains(playerUuidText)) {
                  currentConfig.joinMessageAllowedPlayerUuids.add(playerUuidText);
               }

               if (!allowed) {
                  currentConfig.joinMessageAllowedPlayerUuids.remove(playerUuidText);
               }
            }
         }

         return save();
      } else {
         return false;
      }
   }

   public static List<String> getJoinMessageAllowedPlayerUuids() {
      return List.copyOf(currentConfig.joinMessageAllowedPlayerUuids);
   }

   public static String getJoinMessageBroadcastTemplate() {
      return currentConfig.joinMessageBroadcastTemplate;
   }

   public static String getJoinMessagePersonalTemplate() {
      return currentConfig.joinMessagePersonalTemplate;
   }

   public static String getJoinMessagePlayerNameStyle() {
      return currentConfig.joinMessagePlayerNameStyle;
   }

   public static boolean isJoinSoundBroadcastEnabled() {
      return currentConfig.joinSoundBroadcastEnabled;
   }

   public static boolean setJoinSoundBroadcastEnabled(boolean enabled) {
      boolean previousEnabled = currentConfig.joinSoundBroadcastEnabled;
      currentConfig.joinSoundBroadcastEnabled = enabled;
      if (save()) {
         return true;
      } else {
         currentConfig.joinSoundBroadcastEnabled = previousEnabled;
         return false;
      }
   }

   public static String getJoinVanillaSoundId() {
      return getJoinVanillaSoundIds().stream().findFirst().orElse(currentConfig.joinVanillaSoundId);
   }

   public static List<String> getJoinVanillaSoundIds() {
      if (currentConfig.joinVanillaSoundIds != null && !currentConfig.joinVanillaSoundIds.isEmpty()) {
         return List.copyOf(currentConfig.joinVanillaSoundIds);
      } else {
         return currentConfig.joinVanillaSoundId != null && !currentConfig.joinVanillaSoundId.isBlank() ? List.of(currentConfig.joinVanillaSoundId) : List.of();
      }
   }

   public static int getJoinSoundDelaySeconds() {
      return currentConfig.joinSoundDelaySeconds;
   }

   public static boolean setJoinSoundDelaySeconds(int delaySeconds) {
      if (delaySeconds >= 0 && delaySeconds <= 60) {
         int previousDelaySeconds = currentConfig.joinSoundDelaySeconds;
         currentConfig.joinSoundDelaySeconds = delaySeconds;
         if (save()) {
            return true;
         } else {
            currentConfig.joinSoundDelaySeconds = previousDelaySeconds;
            return false;
         }
      } else {
         return false;
      }
   }

   public static String getJoinSoundMode() {
      return currentConfig.joinSoundMode;
   }

   public static String getJoinInternalMusicFile() {
      return currentConfig.joinInternalMusicFile;
   }

   public static String getJoinMusicResourcePackUrl() {
      return currentConfig.joinMusicResourcePackUrl;
   }

   public static String getResolvedJoinMusicResourcePackUrl() {
      if (currentConfig.joinMusicResourcePackUrl != null && !currentConfig.joinMusicResourcePackUrl.isEmpty()) {
         return currentConfig.joinMusicResourcePackUrl;
      } else if (currentConfig.joinMusicPublicHost != null && !currentConfig.joinMusicPublicHost.isEmpty()) {
         String formattedPublicHost = currentConfig.joinMusicPublicHost.contains(":") && !currentConfig.joinMusicPublicHost.startsWith("[")
            ? "[" + currentConfig.joinMusicPublicHost + "]"
            : currentConfig.joinMusicPublicHost;
         String publicScheme = currentConfig.joinMusicPublicHttps ? "https" : "http";
         return publicScheme + "://" + formattedPublicHost + ":" + currentConfig.joinMusicPublicPort + "/greenmanserver-music.zip";
      } else {
         return "";
      }
   }

   public static String getJoinMusicPublicHost() {
      return currentConfig.joinMusicPublicHost;
   }

   public static int getJoinMusicPublicPort() {
      return currentConfig.joinMusicPublicPort;
   }

   public static boolean isJoinMusicPublicHttps() {
      return currentConfig.joinMusicPublicHttps;
   }

   public static boolean isJoinMusicResourcePackRequired() {
      return currentConfig.joinMusicResourcePackRequired;
   }

   public static int getJoinMusicHttpPort() {
      return currentConfig.joinMusicHttpPort;
   }

   public static boolean setJoinSound(String soundMode, String soundValue) {
      if (soundMode == null) {
         return false;
      } else {
         String normalizedMode = soundMode.toUpperCase(Locale.ROOT);
         if (!normalizedMode.equals("VANILLA") && !normalizedMode.equals("INTERNAL") && !normalizedMode.equals("BOTH") && !normalizedMode.equals("OFF")) {
            return false;
         } else {
            String previousMode = currentConfig.joinSoundMode;
            String previousVanillaSoundId = currentConfig.joinVanillaSoundId;
            List<String> previousVanillaSoundIds = currentConfig.joinVanillaSoundIds == null ? List.of() : List.copyOf(currentConfig.joinVanillaSoundIds);
            String previousInternalMusicFile = currentConfig.joinInternalMusicFile;
            currentConfig.joinSoundMode = normalizedMode;
            if (normalizedMode.equals("VANILLA")) {
               List<String> sanitizedSoundIds = sanitizeJoinSoundIds(soundValue);
               if (sanitizedSoundIds.isEmpty()) {
                  currentConfig.joinSoundMode = previousMode;
                  return false;
               }

               currentConfig.joinVanillaSoundIds = new ArrayList<>(sanitizedSoundIds);
               currentConfig.joinVanillaSoundId = sanitizedSoundIds.get(0);
            }

            if (normalizedMode.equals("INTERNAL")) {
               String sanitizedFileName = sanitizeFileName(soundValue, 128);
               if (sanitizedFileName.isEmpty()) {
                  currentConfig.joinSoundMode = previousMode;
                  return false;
               }

               currentConfig.joinInternalMusicFile = sanitizedFileName;
            }

            if (normalizedMode.equals("BOTH")) {
               List<String> sanitizedSoundIds = sanitizeJoinSoundIds(String.join("|", getJoinVanillaSoundIds()));
               String sanitizedFileName = sanitizeFileName(soundValue, 128);
               if (sanitizedSoundIds.isEmpty() || sanitizedFileName.isEmpty()) {
                  currentConfig.joinSoundMode = previousMode;
                  return false;
               }

               currentConfig.joinVanillaSoundIds = new ArrayList<>(sanitizedSoundIds);
               currentConfig.joinVanillaSoundId = sanitizedSoundIds.get(0);
               currentConfig.joinInternalMusicFile = sanitizedFileName;
            }

            if (save()) {
               return true;
            } else {
               currentConfig.joinSoundMode = previousMode;
               currentConfig.joinVanillaSoundId = previousVanillaSoundId;
               currentConfig.joinVanillaSoundIds = new ArrayList<>(previousVanillaSoundIds);
               currentConfig.joinInternalMusicFile = previousInternalMusicFile;
               return false;
            }
         }
      }
   }

   private static List<String> sanitizeJoinSoundIds(String rawSoundIds) {
      if (rawSoundIds != null && !rawSoundIds.isBlank()) {
         List<String> sanitizedSoundIds = new ArrayList<>();
         String[] rawSoundIdParts = rawSoundIds.split("[|｜,，\\s]+", -1);

         for (String rawSoundIdPart : rawSoundIdParts) {
            String sanitizedSoundId = sanitizeText(rawSoundIdPart, 128);
            if (!sanitizedSoundId.isEmpty() && !sanitizedSoundIds.stream().anyMatch(existingSoundId -> existingSoundId.equalsIgnoreCase(sanitizedSoundId))) {
               sanitizedSoundIds.add(sanitizedSoundId);
               if (sanitizedSoundIds.size() >= 8) {
                  break;
               }
            }
         }

         return List.copyOf(sanitizedSoundIds);
      } else {
         return List.of();
      }
   }

   public static boolean setJoinMessageChannels(boolean personalEnabled, boolean broadcastEnabled) {
      boolean previousPersonalEnabled = currentConfig.joinMessagePersonalEnabled;
      boolean previousBroadcastEnabled = currentConfig.joinMessageBroadcastEnabled;
      currentConfig.joinMessagePersonalEnabled = personalEnabled;
      currentConfig.joinMessageBroadcastEnabled = broadcastEnabled;
      if (save()) {
         return true;
      } else {
         currentConfig.joinMessagePersonalEnabled = previousPersonalEnabled;
         currentConfig.joinMessageBroadcastEnabled = previousBroadcastEnabled;
         return false;
      }
   }

   public static boolean isAnnouncementShowEveryJoin() {
      return currentConfig.announcementShowEveryJoin;
   }

   public static boolean setAnnouncementShowEveryJoin(boolean showEveryJoin) {
      boolean previousShowEveryJoin = currentConfig.announcementShowEveryJoin;
      currentConfig.announcementShowEveryJoin = showEveryJoin;
      if (save()) {
         return true;
      } else {
         currentConfig.announcementShowEveryJoin = previousShowEveryJoin;
         return false;
      }
   }

   public static long getAnnouncementVersion() {
      return Math.max(0L, currentConfig.announcementVersion);
   }

   public static boolean setAnnouncementText(String rawAnnouncementText) {
      String sanitizedAnnouncementText = sanitizeMultilineText(rawAnnouncementText, 2048);
      if (sanitizedAnnouncementText.isEmpty()) {
         return false;
      } else {
         String previousAnnouncementText = currentConfig.announcementText;
         long previousAnnouncementVersion = currentConfig.announcementVersion;
         currentConfig.announcementText = sanitizedAnnouncementText;
         currentConfig.announcementVersion = previousAnnouncementVersion >= Long.MAX_VALUE ? 1L : Math.max(1L, previousAnnouncementVersion + 1L);
         if (save()) {
            return true;
         } else {
            currentConfig.announcementText = previousAnnouncementText;
            currentConfig.announcementVersion = previousAnnouncementVersion;
            return false;
         }
      }
   }

   public static boolean clearAnnouncementText() {
      String previousAnnouncementText = currentConfig.announcementText;
      currentConfig.announcementText = "";
      if (save()) {
         return true;
      } else {
         currentConfig.announcementText = previousAnnouncementText;
         return false;
      }
   }

   public static boolean isMemberGlobalMentionEnabled() {
      return currentConfig.memberGlobalMentionEnabled;
   }

   public static int getMemberGlobalMentionWindowSeconds() {
      return currentConfig.memberGlobalMentionWindowSeconds;
   }

   public static int getMemberGlobalMentionMaxCount() {
      return currentConfig.memberGlobalMentionMaxCount;
   }

   public static boolean setMemberGlobalMentionRateLimit(int windowSeconds, int maximumCount) {
      if (windowSeconds >= 1 && windowSeconds <= 3600 && maximumCount >= 1 && maximumCount <= 20) {
         currentConfig.memberGlobalMentionWindowSeconds = windowSeconds;
         currentConfig.memberGlobalMentionMaxCount = maximumCount;
         return save();
      } else {
         return false;
      }
   }

   public static Boolean getMemberGlobalMentionOverride(UUID playerUuid) {
      return playerUuid != null && currentConfig.memberGlobalMentionOverrides != null
         ? currentConfig.memberGlobalMentionOverrides.get(playerUuid.toString())
         : null;
   }

   public static boolean setMemberGlobalMentionOverride(UUID playerUuid, boolean allowed) {
      if (playerUuid == null) {
         return false;
      } else {
         if (currentConfig.memberGlobalMentionOverrides == null) {
            currentConfig.memberGlobalMentionOverrides = new LinkedHashMap<>();
         }

         currentConfig.memberGlobalMentionOverrides.put(playerUuid.toString(), allowed);
         return save();
      }
   }

   public static boolean clearMemberGlobalMentionOverride(UUID playerUuid) {
      if (playerUuid != null && currentConfig.memberGlobalMentionOverrides != null) {
         Boolean removedOverride = currentConfig.memberGlobalMentionOverrides.remove(playerUuid.toString());
         return removedOverride == null ? false : save();
      } else {
         return false;
      }
   }

   public static boolean setChatLimitEnabled(boolean enabled) {
      currentConfig.chatLimitEnabled = enabled;
      return save();
   }

   public static boolean setChatCooldownSeconds(int seconds) {
      if (seconds >= 0 && seconds <= 60) {
         currentConfig.chatCooldownSeconds = seconds;
         return save();
      } else {
         return false;
      }
   }

   public static boolean setChatMaxLength(int maximumLength) {
      if (maximumLength >= 1 && maximumLength <= 256) {
         currentConfig.chatMaxLength = maximumLength;
         return save();
      } else {
         return false;
      }
   }

   public static boolean setChatRepeatWindowSeconds(int seconds) {
      if (seconds >= 0 && seconds <= 300) {
         currentConfig.chatRepeatWindowSeconds = seconds;
         return save();
      } else {
         return false;
      }
   }

   public static boolean isMemoryOptimizationEnabled() {
      return currentConfig.memoryOptimizationEnabled;
   }

   public static int getMemoryPressureThresholdPercent() {
      return currentConfig.memoryPressureThresholdPercent;
   }

   public static int getMemoryGcCooldownSeconds() {
      return currentConfig.memoryGcCooldownSeconds;
   }

   public static boolean setMemoryOptimizationEnabled(boolean enabled) {
      currentConfig.memoryOptimizationEnabled = enabled;
      return save();
   }

   public static boolean setMemoryPressureThresholdPercent(int thresholdPercent) {
      if (thresholdPercent >= 60 && thresholdPercent <= 95) {
         currentConfig.memoryPressureThresholdPercent = thresholdPercent;
         return save();
      } else {
         return false;
      }
   }

   public static boolean setMemoryGcCooldownSeconds(int seconds) {
      if (seconds >= 1 && seconds <= 3600) {
         currentConfig.memoryGcCooldownSeconds = seconds;
         return save();
      } else {
         return false;
      }
   }

   public static int getItemClearIntervalMinutes() {
      return currentConfig.itemClearIntervalMinutes;
   }

   public static int getItemClearCountdownSeconds() {
      return currentConfig.itemClearCountdownSeconds;
   }

   public static boolean setItemClearIntervalMinutes(int intervalMinutes) {
      if (intervalMinutes >= 1 && intervalMinutes <= 1000000) {
         currentConfig.itemClearIntervalMinutes = intervalMinutes;
         return save();
      } else {
         return false;
      }
   }

   public static boolean setItemClearCountdownSeconds(int countdownSeconds) {
      if (countdownSeconds >= 1 && countdownSeconds <= 60) {
         currentConfig.itemClearCountdownSeconds = countdownSeconds;
         return save();
      } else {
         return false;
      }
   }

   public static boolean setTitle(ServerPlayerEntity targetPlayer, String colorName, String rawTitle) {
      String sanitizedTitle = sanitizeText(rawTitle, 24);
      Formatting titleColor = parseTitleColor(colorName);
      if (targetPlayer != null && !sanitizedTitle.isEmpty() && titleColor != null) {
         Text parsedTitle = GreenManTextFormatter.parseRainbow(sanitizedTitle, Style.EMPTY.withColor(titleColor));
         if (parsedTitle.getString().isEmpty()) {
            return false;
         } else {
            GreenManServerConfig.TitleData titleData = new GreenManServerConfig.TitleData();
            titleData.text = mergeTitleFormatSymbols(colorName, sanitizedTitle);
            titleData.color = titleColor.getName();
            currentConfig.titles.computeIfAbsent(targetPlayer.getUuid().toString(), ignoredUuid -> new ArrayList<>()).add(titleData);
            return save();
         }
      } else {
         return false;
      }
   }

   public static boolean clearTitle(ServerPlayerEntity targetPlayer) {
      if (targetPlayer == null) {
         return false;
      } else {
         currentConfig.titles.remove(targetPlayer.getUuid().toString());
         return save();
      }
   }

   public static MutableText getTitleComponent(ServerPlayerEntity player) {
      if (player != null && isFeatureEnabled("title")) {
         List<GreenManServerConfig.TitleData> titleDataList = currentConfig.titles.get(player.getUuid().toString());
         if (titleDataList != null && !titleDataList.isEmpty()) {
            MutableText titleComponent = Text.empty();

            for (GreenManServerConfig.TitleData titleData : titleDataList) {
               Formatting titleColor = titleData == null ? null : parseColor(titleData.color);
               if (titleData != null && titleColor != null && titleData.text != null && !titleData.text.isEmpty()) {
                  titleComponent.append(Text.literal("【").formatted(titleColor));
                  titleComponent.append(GreenManTextFormatter.parseRainbow(titleData.text, Style.EMPTY.withColor(titleColor)));
                  titleComponent.append(Text.literal("】").formatted(titleColor));
               }
            }

            return titleComponent;
         } else {
            return Text.empty();
         }
      } else {
         return Text.empty();
      }
   }

   public static boolean hasTitle(ServerPlayerEntity player) {
      return !getTitleComponent(player).getString().isEmpty();
   }

   public static Formatting parseColor(String colorName) {
      if (colorName == null) {
         return null;
      } else {
         String var1 = colorName.toLowerCase(Locale.ROOT);

         return switch (var1) {
            case "gold" -> Formatting.GOLD;
            case "aqua" -> Formatting.AQUA;
            case "red" -> Formatting.RED;
            case "green" -> Formatting.GREEN;
            case "blue" -> Formatting.BLUE;
            case "light_purple" -> Formatting.LIGHT_PURPLE;
            case "yellow" -> Formatting.YELLOW;
            case "gray" -> Formatting.GRAY;
            case "white" -> Formatting.WHITE;
            case "dark_gray" -> Formatting.DARK_GRAY;
            case "dark_red" -> Formatting.DARK_RED;
            case "dark_green" -> Formatting.DARK_GREEN;
            case "dark_blue" -> Formatting.DARK_BLUE;
            case "dark_aqua" -> Formatting.DARK_AQUA;
            case "dark_purple" -> Formatting.DARK_PURPLE;
            case "black" -> Formatting.BLACK;
            default -> null;
         };
      }
   }

   public static Formatting parseTitleColor(String colorName) {
      Formatting namedColor = parseColor(colorName);
      if (namedColor != null) {
         return namedColor;
      } else if (colorName != null && !colorName.isEmpty()) {
         for (int characterIndex = 0; characterIndex + 1 < colorName.length(); characterIndex++) {
            if (colorName.charAt(characterIndex) == '&') {
               Formatting formatting = Formatting.byCode(Character.toLowerCase(colorName.charAt(characterIndex + 1)));
               if (formatting != null && formatting.isColor()) {
                  return formatting;
               }
            }
         }

         return isSupportedTitleStyle(colorName) ? Formatting.WHITE : null;
      } else {
         return null;
      }
   }

   public static boolean isSupportedTitleStyle(String colorName) {
      if (parseColor(colorName) != null) {
         return true;
      } else if (colorName != null && !colorName.isEmpty()) {
         for (int characterIndex = 0; characterIndex + 1 < colorName.length(); characterIndex++) {
            if (colorName.charAt(characterIndex) == '&') {
               char styleCode = Character.toLowerCase(colorName.charAt(characterIndex + 1));
               if (styleCode == 'u' || Formatting.byCode(styleCode) != null) {
                  return true;
               }

               if (styleCode == '#' && characterIndex + 7 < colorName.length()) {
                  String hexadecimalColor = colorName.substring(characterIndex + 2, characterIndex + 8);
                  if (hexadecimalColor.matches("[0-9a-fA-F]{6}")) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static String mergeTitleFormatSymbols(String colorName, String sanitizedTitle) {
      if (sanitizedTitle == null || sanitizedTitle.isEmpty() || colorName == null || colorName.isEmpty()) {
         return sanitizedTitle == null ? "" : sanitizedTitle;
      } else {
         return parseColor(colorName) != null ? sanitizedTitle : colorName + sanitizedTitle;
      }
   }

   public static boolean isPunishmentTemplatesEnabled() {
      return currentConfig.punishmentTemplatesEnabled;
   }

   public static String getBanScreenTemplate() {
      return currentConfig.banScreenTemplate;
   }

   public static String getKickScreenTemplate() {
      return currentConfig.kickScreenTemplate;
   }

   public static String getPunishmentSolutionText() {
      return currentConfig.punishmentSolutionText;
   }

   public static String getPunishmentServerTitle() {
      return currentConfig.punishmentServerTitle;
   }

   public static String getBanActionText() {
      return currentConfig.banActionText;
   }

   public static String getKickActionText() {
      return currentConfig.kickActionText;
   }

   public static boolean isPunishmentChatBanAnnouncementEnabled() {
      return currentConfig.punishmentChatBanAnnouncementEnabled;
   }

   public static String getPunishmentChatBanAnnouncementTemplate() {
      return currentConfig.punishmentChatBanAnnouncementTemplate;
   }

   public static boolean isGrimAnticheatEnabled() {
      return currentConfig.grimAnticheatEnabled;
   }

   public static String getGrimBanDuration(int punishmentCount) {
      if (punishmentCount <= 1) {
         return currentConfig.grimFirstBanDuration;
      } else {
         return punishmentCount == 2 ? currentConfig.grimSecondBanDuration : currentConfig.grimThirdBanDuration;
      }
   }

   public static int getGrimPermanentIpBanAfterCount() {
      return currentConfig.grimPermanentIpBanAfterCount;
   }

   public static int getGrimStreakKickThreshold() {
      return currentConfig.grimStreakKickThreshold;
   }

   public static int getGrimAlertLogCooldownSeconds() {
      return currentConfig.grimAlertLogCooldownSeconds;
   }

   public static boolean isGrimSafeWalkExemptEnabled() {
      return currentConfig.grimSafeWalkExemptEnabled;
   }

   public static boolean isElytraMovementCheckBypassEnabled() {
      return currentConfig.elytraMovementCheckBypassEnabled;
   }

   public static boolean isPunishmentBanSoundEnabled() {
      return currentConfig.punishmentBanSoundEnabled;
   }

   public static String getPunishmentBanSoundMode() {
      return currentConfig.punishmentBanSoundMode;
   }

   public static List<String> getPunishmentBanVanillaSoundIds() {
      return currentConfig.punishmentBanVanillaSoundIds == null ? List.of() : List.copyOf(currentConfig.punishmentBanVanillaSoundIds);
   }

   public static String getPunishmentBanInternalMusicFile() {
      return currentConfig.punishmentBanInternalMusicFile;
   }

   public static boolean setPunishmentChatBanAnnouncementTemplate(String rawTemplate) {
      String sanitizedTemplate = sanitizeTemplate(rawTemplate);
      if (sanitizedTemplate.isEmpty()) {
         return false;
      } else {
         String previousTemplate = currentConfig.punishmentChatBanAnnouncementTemplate;
         currentConfig.punishmentChatBanAnnouncementTemplate = sanitizedTemplate;
         if (save()) {
            return true;
         } else {
            currentConfig.punishmentChatBanAnnouncementTemplate = previousTemplate;
            return false;
         }
      }
   }

   public static boolean isVoteResultsDetailEnabled() {
      return currentConfig.voteResultsDetailEnabled;
   }

   public static String getMuteChatAnnouncementTemplate() {
      return currentConfig.muteChatAnnouncementTemplate != null && !currentConfig.muteChatAnnouncementTemplate.isBlank()
         ? currentConfig.muteChatAnnouncementTemplate
         : "&c[{server_title}] &e{player_name} &c被禁言&f，禁言时长：&e{duration}&f，原因：&e{reason}&f，处理人：&b{operator}";
   }

   public static int getVoteMaxDurationSeconds() {
      return currentConfig.voteMaxDurationSeconds;
   }

   public static int getVoteMaxOptionCount() {
      return currentConfig.voteMaxOptionCount;
   }

   private static GreenManServerConfig.ConfigData sanitizeConfig(JsonObject untrustedConfig) {
      GreenManServerConfig.ConfigData sanitizedConfig = createDefaultConfig();
      if (untrustedConfig == null) {
         return sanitizedConfig;
      } else {
         String rawServerName = untrustedConfig.has("tabServerName") ? untrustedConfig.get("tabServerName").getAsString() : "";
         String sanitizedServerName = sanitizeText(rawServerName, 48);
         if (!sanitizedServerName.isEmpty()) {
            sanitizedConfig.tabServerName = sanitizedServerName;
         }

         sanitizedConfig.chatLimitEnabled = readBoolean(untrustedConfig, "chatLimitEnabled", sanitizedConfig.chatLimitEnabled);
         sanitizedConfig.chatCooldownSeconds = readInteger(untrustedConfig, "chatCooldownSeconds", 0, 60, sanitizedConfig.chatCooldownSeconds);
         sanitizedConfig.chatMaxLength = readInteger(untrustedConfig, "chatMaxLength", 1, 256, sanitizedConfig.chatMaxLength);
         sanitizedConfig.chatRepeatWindowSeconds = readInteger(untrustedConfig, "chatRepeatWindowSeconds", 0, 300, sanitizedConfig.chatRepeatWindowSeconds);
         sanitizedConfig.chatArchiveEnabled = readBoolean(untrustedConfig, "chatArchiveEnabled", sanitizedConfig.chatArchiveEnabled);
         sanitizedConfig.chatArchiveRetentionDays = readInteger(untrustedConfig, "chatArchiveRetentionDays", 1, 3650, sanitizedConfig.chatArchiveRetentionDays);
         sanitizedConfig.chatArchiveMaxFileSizeMib = readInteger(
            untrustedConfig, "chatArchiveMaxFileSizeMib", 1, 1024, sanitizedConfig.chatArchiveMaxFileSizeMib
         );
         sanitizedConfig.chatHistoryEnabled = readBoolean(untrustedConfig, "chatHistoryEnabled", sanitizedConfig.chatHistoryEnabled);
         sanitizedConfig.chatHistoryCacheSize = readInteger(untrustedConfig, "chatHistoryCacheSize", 0, 500, sanitizedConfig.chatHistoryCacheSize);
         sanitizedConfig.announcementEnabled = readBoolean(untrustedConfig, "announcementEnabled", sanitizedConfig.announcementEnabled);
         String sanitizedAnnouncementTitle = sanitizeText(readString(untrustedConfig, "announcementTitle", sanitizedConfig.announcementTitle), 128);
         if (!sanitizedAnnouncementTitle.isEmpty()) {
            sanitizedConfig.announcementTitle = sanitizedAnnouncementTitle;
         }

         sanitizedConfig.announcementText = sanitizeMultilineText(readString(untrustedConfig, "announcementText", sanitizedConfig.announcementText), 2048);
         sanitizedConfig.announcementVersion = readLong(untrustedConfig, "announcementVersion", 0L, Long.MAX_VALUE, sanitizedConfig.announcementVersion);
         sanitizedConfig.announcementShowEveryJoin = readBoolean(untrustedConfig, "announcementShowEveryJoin", sanitizedConfig.announcementShowEveryJoin);
         sanitizedConfig.memberGlobalMentionEnabled = readBoolean(untrustedConfig, "memberGlobalMentionEnabled", sanitizedConfig.memberGlobalMentionEnabled);
         sanitizedConfig.memberGlobalMentionWindowSeconds = readInteger(
            untrustedConfig, "memberGlobalMentionWindowSeconds", 1, 3600, sanitizedConfig.memberGlobalMentionWindowSeconds
         );
         sanitizedConfig.memberGlobalMentionMaxCount = readInteger(
            untrustedConfig, "memberGlobalMentionMaxCount", 1, 20, sanitizedConfig.memberGlobalMentionMaxCount
         );
         sanitizedConfig.memberGlobalMentionOverrides = new LinkedHashMap<>();
         if (untrustedConfig.has("memberGlobalMentionOverrides") && untrustedConfig.get("memberGlobalMentionOverrides").isJsonObject()) {
            for (Entry<String, JsonElement> permissionEntry : untrustedConfig.getAsJsonObject("memberGlobalMentionOverrides").entrySet()) {
               if (permissionEntry.getValue() != null && permissionEntry.getValue().isJsonPrimitive()) {
                  try {
                     UUID.fromString(permissionEntry.getKey());
                     sanitizedConfig.memberGlobalMentionOverrides.put(permissionEntry.getKey(), permissionEntry.getValue().getAsBoolean());
                  } catch (IllegalArgumentException var24) {
                  }
               }
            }
         }

         sanitizedConfig.tabStatusEnabled = readBoolean(untrustedConfig, "tabStatusEnabled", sanitizedConfig.tabStatusEnabled);
         sanitizedConfig.titleEnabled = readBoolean(untrustedConfig, "titleEnabled", sanitizedConfig.titleEnabled);
         sanitizedConfig.latencyDisplayEnabled = readBoolean(untrustedConfig, "latencyDisplayEnabled", sanitizedConfig.latencyDisplayEnabled);
         sanitizedConfig.scheduledAnnouncementEnabled = readBoolean(
            untrustedConfig, "scheduledAnnouncementEnabled", sanitizedConfig.scheduledAnnouncementEnabled
         );
         String scheduledMode = sanitizeText(readString(untrustedConfig, "scheduledAnnouncementMode", sanitizedConfig.scheduledAnnouncementMode), 16)
            .toLowerCase(Locale.ROOT);
         sanitizedConfig.scheduledAnnouncementMode = List.of("interval", "daily", "weekly", "monthly").contains(scheduledMode) ? scheduledMode : "interval";
         sanitizedConfig.scheduledAnnouncementIntervalMinutes = readInteger(
            untrustedConfig, "scheduledAnnouncementIntervalMinutes", 1, 1000000, sanitizedConfig.scheduledAnnouncementIntervalMinutes
         );
         sanitizedConfig.scheduledAnnouncementWeekday = readInteger(
            untrustedConfig, "scheduledAnnouncementWeekday", 1, 7, sanitizedConfig.scheduledAnnouncementWeekday
         );
         sanitizedConfig.scheduledAnnouncementMonthDay = readInteger(
            untrustedConfig, "scheduledAnnouncementMonthDay", 1, 31, sanitizedConfig.scheduledAnnouncementMonthDay
         );
         String scheduledTime = sanitizeText(readString(untrustedConfig, "scheduledAnnouncementTime", sanitizedConfig.scheduledAnnouncementTime), 5);

         try {
            LocalTime.parse(scheduledTime, DateTimeFormatter.ofPattern("HH:mm"));
            sanitizedConfig.scheduledAnnouncementTime = scheduledTime;
         } catch (RuntimeException var23) {
            sanitizedConfig.scheduledAnnouncementTime = "20:00";
         }

         sanitizedConfig.scheduledAnnouncementTitle = sanitizeText(
            readString(untrustedConfig, "scheduledAnnouncementTitle", sanitizedConfig.scheduledAnnouncementTitle), 128
         );
         sanitizedConfig.scheduledAnnouncementText = sanitizeMultilineText(
            readString(untrustedConfig, "scheduledAnnouncementText", sanitizedConfig.scheduledAnnouncementText), 2048
         );
         sanitizedConfig.composterEnabled = readBoolean(untrustedConfig, "composterEnabled", sanitizedConfig.composterEnabled);
         sanitizedConfig.memoryOptimizationEnabled = readBoolean(untrustedConfig, "memoryOptimizationEnabled", sanitizedConfig.memoryOptimizationEnabled);
         sanitizedConfig.mentionsEnabled = readBoolean(untrustedConfig, "mentionsEnabled", sanitizedConfig.mentionsEnabled);
         sanitizedConfig.mentionActionBarEnabled = readBoolean(untrustedConfig, "mentionActionBarEnabled", sanitizedConfig.mentionActionBarEnabled);
         sanitizedConfig.dispenserRecipeEnabled = readBoolean(untrustedConfig, "dispenserRecipeEnabled", sanitizedConfig.dispenserRecipeEnabled);
         sanitizedConfig.entityUnloadProtectionEnabled = readBoolean(
            untrustedConfig, "entityUnloadProtectionEnabled", sanitizedConfig.entityUnloadProtectionEnabled
         );
         sanitizedConfig.vanillaRedstoneProtectionEnabled = readBoolean(
            untrustedConfig, "vanillaRedstoneProtectionEnabled", sanitizedConfig.vanillaRedstoneProtectionEnabled
         );
         sanitizedConfig.anvilNameColorEnabled = readBoolean(untrustedConfig, "anvilNameColorEnabled", sanitizedConfig.anvilNameColorEnabled);
         sanitizedConfig.debugLoggingEnabled = readBoolean(untrustedConfig, "debugLoggingEnabled", sanitizedConfig.debugLoggingEnabled);
         sanitizedConfig.punishmentTemplatesEnabled = readBoolean(untrustedConfig, "punishmentTemplatesEnabled", sanitizedConfig.punishmentTemplatesEnabled);
         String sanitizedPunishmentServerTitle = sanitizeText(readString(untrustedConfig, "punishmentServerTitle", sanitizedConfig.punishmentServerTitle), 128);
         if (!sanitizedPunishmentServerTitle.isEmpty()) {
            sanitizedConfig.punishmentServerTitle = sanitizedPunishmentServerTitle;
         }

         String sanitizedBanActionText = sanitizeText(readString(untrustedConfig, "banActionText", sanitizedConfig.banActionText), 128);
         if (!sanitizedBanActionText.isEmpty()) {
            sanitizedConfig.banActionText = sanitizedBanActionText;
         }

         String sanitizedKickActionText = sanitizeText(readString(untrustedConfig, "kickActionText", sanitizedConfig.kickActionText), 128);
         if (!sanitizedKickActionText.isEmpty()) {
            sanitizedConfig.kickActionText = sanitizedKickActionText;
         }

         String sanitizedBanScreenTemplate = sanitizeTemplate(readString(untrustedConfig, "banScreenTemplate", sanitizedConfig.banScreenTemplate));
         if (!sanitizedBanScreenTemplate.isEmpty()) {
            sanitizedConfig.banScreenTemplate = sanitizedBanScreenTemplate;
         }

         if (!sanitizedConfig.banScreenTemplate.contains("{ban_count}")) {
            sanitizedConfig.banScreenTemplate = sanitizedConfig.banScreenTemplate + "\n&f封禁次数：&e{ban_count}";
         }

         sanitizedConfig.kickScreenTemplate = sanitizeTemplate(readString(untrustedConfig, "kickScreenTemplate", sanitizedConfig.kickScreenTemplate));
         String sanitizedPunishmentSolutionText = sanitizeTemplate(
            readString(untrustedConfig, "punishmentSolutionText", sanitizedConfig.punishmentSolutionText)
         );
         if ("请联系服务器管理员申诉或等待封禁到期。".equals(sanitizedPunishmentSolutionText)) {
            sanitizedConfig.punishmentSolutionText = "请勿立即关闭此界面，带边框截图此界面并找管理员申诉，手机用户直接截屏即可";
         } else if (!sanitizedPunishmentSolutionText.isEmpty()) {
            sanitizedConfig.punishmentSolutionText = sanitizedPunishmentSolutionText;
         }

         sanitizedConfig.punishmentChatBanAnnouncementEnabled = readBoolean(
            untrustedConfig, "punishmentChatBanAnnouncementEnabled", sanitizedConfig.punishmentChatBanAnnouncementEnabled
         );
         sanitizedConfig.punishmentChatBanAnnouncementTemplate = sanitizeTemplate(
            readString(untrustedConfig, "punishmentChatBanAnnouncementTemplate", sanitizedConfig.punishmentChatBanAnnouncementTemplate)
         );
         sanitizedConfig.grimAnticheatEnabled = readBoolean(untrustedConfig, "grimAnticheatEnabled", sanitizedConfig.grimAnticheatEnabled);
         sanitizedConfig.grimFirstBanDuration = sanitizeAnticheatDuration(
            readString(untrustedConfig, "grimFirstBanDuration", sanitizedConfig.grimFirstBanDuration), sanitizedConfig.grimFirstBanDuration
         );
         sanitizedConfig.grimSecondBanDuration = sanitizeAnticheatDuration(
            readString(untrustedConfig, "grimSecondBanDuration", sanitizedConfig.grimSecondBanDuration), sanitizedConfig.grimSecondBanDuration
         );
         sanitizedConfig.grimThirdBanDuration = sanitizeAnticheatDuration(
            readString(untrustedConfig, "grimThirdBanDuration", sanitizedConfig.grimThirdBanDuration), sanitizedConfig.grimThirdBanDuration
         );
         sanitizedConfig.grimPermanentIpBanAfterCount = readInteger(
            untrustedConfig, "grimPermanentIpBanAfterCount", 4, 1000, sanitizedConfig.grimPermanentIpBanAfterCount
         );
         sanitizedConfig.grimStreakKickThreshold = readInteger(untrustedConfig, "grimStreakKickThreshold", 1, 100, sanitizedConfig.grimStreakKickThreshold);
         if (!untrustedConfig.has("grimSafeWalkExemptEnabled")) {
            sanitizedConfig.grimStreakKickThreshold = 3;
         }

         sanitizedConfig.grimAlertLogCooldownSeconds = readInteger(
            untrustedConfig, "grimAlertLogCooldownSeconds", 0, 300, sanitizedConfig.grimAlertLogCooldownSeconds
         );
         sanitizedConfig.grimSafeWalkExemptEnabled = readBoolean(untrustedConfig, "grimSafeWalkExemptEnabled", sanitizedConfig.grimSafeWalkExemptEnabled);
         sanitizedConfig.punishmentBanSoundEnabled = readBoolean(untrustedConfig, "punishmentBanSoundEnabled", sanitizedConfig.punishmentBanSoundEnabled);
         String punishmentSoundMode = sanitizeText(readString(untrustedConfig, "punishmentBanSoundMode", sanitizedConfig.punishmentBanSoundMode), 16)
            .toUpperCase(Locale.ROOT);
         sanitizedConfig.punishmentBanSoundMode = List.of("VANILLA", "INTERNAL", "BOTH", "OFF").contains(punishmentSoundMode) ? punishmentSoundMode : "VANILLA";
         sanitizedConfig.punishmentBanVanillaSoundIds = readSoundIdList(
            untrustedConfig, "punishmentBanVanillaSoundIds", "minecraft:entity.lightning_bolt.thunder"
         );
         sanitizedConfig.punishmentBanInternalMusicFile = sanitizeFileName(
            readString(untrustedConfig, "punishmentBanInternalMusicFile", sanitizedConfig.punishmentBanInternalMusicFile), 128
         );
         sanitizedConfig.votingEnabled = readBoolean(untrustedConfig, "votingEnabled", sanitizedConfig.votingEnabled);
         sanitizedConfig.voteResultsDetailEnabled = readBoolean(untrustedConfig, "voteResultsDetailEnabled", sanitizedConfig.voteResultsDetailEnabled);
         sanitizedConfig.voteMaxDurationSeconds = readInteger(untrustedConfig, "voteMaxDurationSeconds", 1, 86400, sanitizedConfig.voteMaxDurationSeconds);
         sanitizedConfig.voteMaxOptionCount = readInteger(untrustedConfig, "voteMaxOptionCount", 2, 10, sanitizedConfig.voteMaxOptionCount);
         sanitizedConfig.muteEnabled = readBoolean(untrustedConfig, "muteEnabled", sanitizedConfig.muteEnabled);
         sanitizedConfig.muteChatAnnouncementTemplate = sanitizeTemplate(
            readString(untrustedConfig, "muteChatAnnouncementTemplate", sanitizedConfig.muteChatAnnouncementTemplate)
         );
         sanitizedConfig.itemClearEnabled = readBoolean(untrustedConfig, "itemClearEnabled", sanitizedConfig.itemClearEnabled);
         sanitizedConfig.itemClearIntervalMinutes = readInteger(
            untrustedConfig, "itemClearIntervalMinutes", 1, 1000000, sanitizedConfig.itemClearIntervalMinutes
         );
         sanitizedConfig.itemClearCountdownSeconds = readInteger(untrustedConfig, "itemClearCountdownSeconds", 1, 60, sanitizedConfig.itemClearCountdownSeconds);
         sanitizedConfig.elytraMovementCheckBypassEnabled = readBoolean(
            untrustedConfig, "elytraMovementCheckBypassEnabled", sanitizedConfig.elytraMovementCheckBypassEnabled
         );
         sanitizedConfig.memoryPressureThresholdPercent = readInteger(
            untrustedConfig, "memoryPressureThresholdPercent", 60, 95, sanitizedConfig.memoryPressureThresholdPercent
         );
         sanitizedConfig.memoryGcCooldownSeconds = readInteger(untrustedConfig, "memoryGcCooldownSeconds", 1, 3600, sanitizedConfig.memoryGcCooldownSeconds);
         sanitizedConfig.disableModNetworkChecks = readBoolean(untrustedConfig, "disableModNetworkChecks", sanitizedConfig.disableModNetworkChecks);
         sanitizedConfig.asyncChunkIoEnabled = readBoolean(untrustedConfig, "asyncChunkIoEnabled", sanitizedConfig.asyncChunkIoEnabled);
         sanitizedConfig.joinMessageAllowedPlayerUuids = readUuidList(untrustedConfig, "joinMessageAllowedPlayerUuids");
         sanitizedConfig.joinMessageEnabled = readBoolean(untrustedConfig, "joinMessageEnabled", sanitizedConfig.joinMessageEnabled);
         sanitizedConfig.joinMessagePersonalEnabled = readBoolean(untrustedConfig, "joinMessagePersonalEnabled", sanitizedConfig.joinMessagePersonalEnabled);
         sanitizedConfig.joinMessageBroadcastEnabled = readBoolean(untrustedConfig, "joinMessageBroadcastEnabled", sanitizedConfig.joinMessageBroadcastEnabled);
         sanitizedConfig.joinMessagePersonalTemplate = sanitizeMultilineText(
            readString(untrustedConfig, "joinMessagePersonalTemplate", sanitizedConfig.joinMessagePersonalTemplate), 512
         );
         String rawBroadcastTemplate = readString(untrustedConfig, "joinMessageBroadcastTemplate", sanitizedConfig.joinMessageBroadcastTemplate);
         String migratedBroadcastTemplate = "&e{player} &a进入了服务器，当前在线 {online_count} 人。".equals(rawBroadcastTemplate)
            ? "&e{player} &a进入了服务器。"
            : rawBroadcastTemplate;
         sanitizedConfig.joinMessageBroadcastTemplate = sanitizeMultilineText(migratedBroadcastTemplate, 512);
         String sanitizedPlayerNameStyle = sanitizeText(
            readString(untrustedConfig, "joinMessagePlayerNameStyle", sanitizedConfig.joinMessagePlayerNameStyle), 32
         );
         sanitizedConfig.joinMessagePlayerNameStyle = sanitizedPlayerNameStyle.isEmpty() ? "&#55FF55&l" : sanitizedPlayerNameStyle;
         sanitizedConfig.joinSoundEnabled = readBoolean(untrustedConfig, "joinSoundEnabled", sanitizedConfig.joinSoundEnabled);
         sanitizedConfig.joinSoundBroadcastEnabled = readBoolean(untrustedConfig, "joinSoundBroadcastEnabled", sanitizedConfig.joinSoundBroadcastEnabled);
         String sanitizedJoinSoundMode = sanitizeText(readString(untrustedConfig, "joinSoundMode", sanitizedConfig.joinSoundMode), 16).toUpperCase(Locale.ROOT);
         sanitizedConfig.joinSoundMode = !sanitizedJoinSoundMode.equals("INTERNAL")
               && !sanitizedJoinSoundMode.equals("BOTH")
               && !sanitizedJoinSoundMode.equals("OFF")
            ? "VANILLA"
            : sanitizedJoinSoundMode;
         sanitizedConfig.joinVanillaSoundId = sanitizeText(readString(untrustedConfig, "joinVanillaSoundId", sanitizedConfig.joinVanillaSoundId), 128);
         sanitizedConfig.joinVanillaSoundIds = readSoundIdList(untrustedConfig, "joinVanillaSoundIds", sanitizedConfig.joinVanillaSoundId);
         sanitizedConfig.joinInternalMusicFile = sanitizeFileName(
            readString(untrustedConfig, "joinInternalMusicFile", sanitizedConfig.joinInternalMusicFile), 128
         );
         sanitizedConfig.joinSoundDelaySeconds = readInteger(untrustedConfig, "joinSoundDelaySeconds", 0, 60, sanitizedConfig.joinSoundDelaySeconds);
         sanitizedConfig.joinMusicResourcePackUrl = sanitizeHttpUrl(
            readString(untrustedConfig, "joinMusicResourcePackUrl", sanitizedConfig.joinMusicResourcePackUrl), 2048
         );
         sanitizedConfig.joinMusicPublicHost = sanitizePublicHost(readString(untrustedConfig, "joinMusicPublicHost", sanitizedConfig.joinMusicPublicHost));
         sanitizedConfig.joinMusicPublicPort = readInteger(untrustedConfig, "joinMusicPublicPort", 1, 65535, sanitizedConfig.joinMusicPublicPort);
         sanitizedConfig.joinMusicPublicHttps = readBoolean(untrustedConfig, "joinMusicPublicHttps", sanitizedConfig.joinMusicPublicHttps);
         sanitizedConfig.joinMusicResourcePackRequired = readBoolean(
            untrustedConfig, "joinMusicResourcePackRequired", sanitizedConfig.joinMusicResourcePackRequired
         );
         sanitizedConfig.joinMusicHttpPort = readInteger(untrustedConfig, "joinMusicHttpPort", 1024, 65535, sanitizedConfig.joinMusicHttpPort);
         if (untrustedConfig.has("titles") && untrustedConfig.get("titles").isJsonObject()) {
            for (Entry<String, JsonElement> titleEntry : untrustedConfig.getAsJsonObject("titles").entrySet()) {
               try {
                  UUID.fromString(titleEntry.getKey());
                  JsonElement rawTitles = titleEntry.getValue();
                  List<GreenManServerConfig.TitleData> sanitizedTitles = new ArrayList<>();
                  if (rawTitles.isJsonArray()) {
                     for (JsonElement rawTitle : rawTitles.getAsJsonArray()) {
                        addSanitizedTitle(rawTitle, sanitizedTitles);
                     }
                  } else {
                     addSanitizedTitle(rawTitles, sanitizedTitles);
                  }

                  if (!sanitizedTitles.isEmpty()) {
                     sanitizedConfig.titles.put(titleEntry.getKey(), sanitizedTitles);
                  }
               } catch (IllegalArgumentException var25) {
               }
            }

            return sanitizedConfig;
         } else {
            return sanitizedConfig;
         }
      }
   }

   private static boolean readBoolean(JsonObject config, String key, boolean defaultValue) {
      try {
         return config.has(key) && config.get(key).isJsonPrimitive() ? config.get(key).getAsBoolean() : defaultValue;
      } catch (RuntimeException var4) {
         return defaultValue;
      }
   }

   private static String readString(JsonObject config, String key, String defaultValue) {
      try {
         return config.has(key) && config.get(key).isJsonPrimitive() ? config.get(key).getAsString() : defaultValue;
      } catch (RuntimeException var4) {
         return defaultValue;
      }
   }

   private static List<String> readUuidList(JsonObject config, String key) {
      List<String> validUuidStrings = new ArrayList<>();
      if (config != null && key != null && config.has(key) && config.get(key).isJsonArray()) {
         for (JsonElement uuidElement : config.getAsJsonArray(key)) {
            if (uuidElement != null && uuidElement.isJsonPrimitive()) {
               try {
                  validUuidStrings.add(UUID.fromString(uuidElement.getAsString()).toString());
               } catch (IllegalArgumentException var6) {
               }
            }
         }

         return validUuidStrings;
      } else {
         return validUuidStrings;
      }
   }

   private static List<String> readSoundIdList(JsonObject config, String key, String legacySoundId) {
      if (config != null && key != null && config.has(key) && config.get(key).isJsonArray()) {
         List<String> soundIds = new ArrayList<>();

         for (JsonElement soundElement : config.getAsJsonArray(key)) {
            if (soundElement != null && soundElement.isJsonPrimitive()) {
               String soundId = sanitizeText(soundElement.getAsString(), 128);
               if (!soundId.isEmpty() && !soundIds.stream().anyMatch(existingSoundId -> existingSoundId.equalsIgnoreCase(soundId))) {
                  soundIds.add(soundId);
                  if (soundIds.size() >= 8) {
                     break;
                  }
               }
            }
         }

         if (soundIds.isEmpty() && legacySoundId != null && !legacySoundId.isBlank()) {
            soundIds.add(legacySoundId);
         }

         return soundIds;
      } else {
         return legacySoundId != null && !legacySoundId.isBlank() ? new ArrayList<>(List.of(legacySoundId)) : new ArrayList<>();
      }
   }

   private static String sanitizeTemplate(String rawTemplate) {
      if (rawTemplate == null) {
         return "";
      } else {
         StringBuilder sanitizedTemplateBuilder = new StringBuilder();
         int acceptedCodePointCount = 0;
         int characterOffset = 0;

         while (characterOffset < rawTemplate.length() && acceptedCodePointCount < 2048) {
            int currentCodePoint = rawTemplate.codePointAt(characterOffset);
            characterOffset += Character.charCount(currentCodePoint);
            if (currentCodePoint != 13 && (currentCodePoint == 10 || !Character.isISOControl(currentCodePoint) && currentCodePoint != 167)) {
               sanitizedTemplateBuilder.appendCodePoint(currentCodePoint);
               acceptedCodePointCount++;
            }
         }

         return sanitizedTemplateBuilder.toString().trim();
      }
   }

   private static String sanitizeAnticheatDuration(String rawDuration, String fallbackDuration) {
      String sanitizedDuration = sanitizeText(rawDuration, 16).toLowerCase(Locale.ROOT);
      return !sanitizedDuration.matches("[1-9][0-9]{0,8}[smhd]") ? fallbackDuration : sanitizedDuration;
   }

   private static int readInteger(JsonObject config, String key, int minimum, int maximum, int defaultValue) {
      try {
         if (config.has(key) && config.get(key).isJsonPrimitive()) {
            int value = config.get(key).getAsInt();
            return Math.max(minimum, Math.min(maximum, value));
         } else {
            return defaultValue;
         }
      } catch (RuntimeException var6) {
         return defaultValue;
      }
   }

   private static double readDouble(JsonObject config, String key, double minimum, double maximum, double defaultValue) {
      try {
         if (config != null && config.has(key) && config.get(key).isJsonPrimitive()) {
            double value = config.get(key).getAsDouble();
            return !Double.isFinite(value) ? defaultValue : Math.max(minimum, Math.min(maximum, value));
         } else {
            return defaultValue;
         }
      } catch (RuntimeException var10) {
         return defaultValue;
      }
   }

   private static long readLong(JsonObject config, String key, long minimum, long maximum, long defaultValue) {
      try {
         if (config.has(key) && config.get(key).isJsonPrimitive()) {
            long value = config.get(key).getAsLong();
            return Math.max(minimum, Math.min(maximum, value));
         } else {
            return defaultValue;
         }
      } catch (RuntimeException var10) {
         return defaultValue;
      }
   }

   private static void addSanitizedTitle(JsonElement rawTitle, List<GreenManServerConfig.TitleData> sanitizedTitles) {
      if (rawTitle != null && rawTitle.isJsonObject()) {
         JsonObject rawTitleObject = rawTitle.getAsJsonObject();
         String rawText = rawTitleObject.has("text") ? rawTitleObject.get("text").getAsString() : "";
         String rawColor = rawTitleObject.has("color") ? rawTitleObject.get("color").getAsString() : "";
         String sanitizedText = sanitizeText(rawText, 24);
         Formatting sanitizedColor = parseTitleColor(rawColor);
         if (!sanitizedText.isEmpty() && sanitizedColor != null) {
            GreenManServerConfig.TitleData sanitizedTitle = new GreenManServerConfig.TitleData();
            sanitizedTitle.text = mergeTitleFormatSymbols(rawColor, sanitizedText);
            sanitizedTitle.color = sanitizedColor.getName();
            sanitizedTitles.add(sanitizedTitle);
         }
      }
   }

   private static String sanitizeText(String rawText, int maximumCodePoints) {
      if (rawText == null) {
         return "";
      } else {
         StringBuilder sanitizedTextBuilder = new StringBuilder();
         rawText.codePoints()
            .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != 167)
            .limit(maximumCodePoints)
            .forEach(sanitizedTextBuilder::appendCodePoint);
         return sanitizedTextBuilder.toString().trim();
      }
   }

   private static String sanitizeFileName(String rawFileName, int maximumCodePoints) {
      if (rawFileName != null && !rawFileName.isEmpty()) {
         String fileName = rawFileName.replace('\\', '/');
         int lastSlashIndex = fileName.lastIndexOf(47);
         if (lastSlashIndex >= 0) {
            fileName = fileName.substring(lastSlashIndex + 1);
         }

         String sanitizedFileName = sanitizeText(fileName, maximumCodePoints);
         return !sanitizedFileName.isEmpty() && !sanitizedFileName.equals(".") && !sanitizedFileName.equals("..") ? sanitizedFileName : "";
      } else {
         return "";
      }
   }

   private static String sanitizeHttpUrl(String rawUrl, int maximumCodePoints) {
      String sanitizedUrl = sanitizeText(rawUrl, maximumCodePoints);
      if (sanitizedUrl.isEmpty()) {
         return "";
      } else {
         return !sanitizedUrl.startsWith("http://") && !sanitizedUrl.startsWith("https://") ? "" : sanitizedUrl;
      }
   }

   private static String sanitizePublicHost(String rawHost) {
      if (rawHost != null && !rawHost.isBlank()) {
         String trimmedHost = rawHost.trim();
         if (trimmedHost.contains("://") && !trimmedHost.startsWith("http://") && !trimmedHost.startsWith("https://")) {
            return "";
         } else {
            try {
               URI parsedPublicUri = new URI(!trimmedHost.startsWith("http://") && !trimmedHost.startsWith("https://") ? "http://" + trimmedHost : trimmedHost);
               if (parsedPublicUri.getRawUserInfo() != null) {
                  return "";
               } else {
                  String parsedHost = parsedPublicUri.getHost();
                  return parsedHost != null && !parsedHost.isBlank() && parsedHost.codePointCount(0, parsedHost.length()) <= 253
                     ? parsedHost.toLowerCase(Locale.ROOT)
                     : "";
               }
            } catch (URISyntaxException var4) {
               return "";
            }
         }
      } else {
         return "";
      }
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

   private static GreenManServerConfig.ConfigData createDefaultConfig() {
      GreenManServerConfig.ConfigData defaultConfig = new GreenManServerConfig.ConfigData();
      defaultConfig.tabServerName = "GreenManServer";
      defaultConfig.titles = new LinkedHashMap<>();
      defaultConfig.chatLimitEnabled = true;
      defaultConfig.chatCooldownSeconds = 3;
      defaultConfig.chatMaxLength = 128;
      defaultConfig.chatRepeatWindowSeconds = 30;
      defaultConfig.chatArchiveEnabled = true;
      defaultConfig.chatArchiveRetentionDays = 30;
      defaultConfig.chatArchiveMaxFileSizeMib = 16;
      defaultConfig.chatHistoryEnabled = true;
      defaultConfig.chatHistoryCacheSize = 50;
      defaultConfig.announcementEnabled = true;
      defaultConfig.announcementTitle = "&6&l[服务器公告]";
      defaultConfig.announcementText = "";
      defaultConfig.announcementVersion = 0L;
      defaultConfig.announcementShowEveryJoin = true;
      defaultConfig.memberGlobalMentionEnabled = false;
      defaultConfig.memberGlobalMentionWindowSeconds = 60;
      defaultConfig.memberGlobalMentionMaxCount = 1;
      defaultConfig.memberGlobalMentionOverrides = new LinkedHashMap<>();
      defaultConfig.tabStatusEnabled = true;
      defaultConfig.titleEnabled = true;
      defaultConfig.latencyDisplayEnabled = true;
      defaultConfig.scheduledAnnouncementEnabled = false;
      defaultConfig.scheduledAnnouncementMode = "interval";
      defaultConfig.scheduledAnnouncementIntervalMinutes = 60;
      defaultConfig.scheduledAnnouncementTime = "20:00";
      defaultConfig.scheduledAnnouncementWeekday = 1;
      defaultConfig.scheduledAnnouncementMonthDay = 1;
      defaultConfig.scheduledAnnouncementTitle = "&6&l[定时公告]";
      defaultConfig.scheduledAnnouncementText = "&e请管理员设置定时公告内容。";
      defaultConfig.composterEnabled = true;
      defaultConfig.memoryOptimizationEnabled = true;
      defaultConfig.mentionsEnabled = true;
      defaultConfig.mentionActionBarEnabled = true;
      defaultConfig.dispenserRecipeEnabled = true;
      defaultConfig.entityUnloadProtectionEnabled = true;
      defaultConfig.vanillaRedstoneProtectionEnabled = true;
      defaultConfig.anvilNameColorEnabled = true;
      defaultConfig.debugLoggingEnabled = false;
      defaultConfig.punishmentTemplatesEnabled = true;
      defaultConfig.punishmentServerTitle = "GreenManServer";
      defaultConfig.banActionText = "已被封禁";
      defaultConfig.kickActionText = "已被踢出";
      defaultConfig.banScreenTemplate = "&c{server_title}\n&f操作：&e{action}\n&f原因：&e{reason}\n&f封禁次数：&e{ban_count}\n&f解决方法：&b{solution}\n&f到期时间：&e{expires_at}\n&7处理人：{operator}";
      defaultConfig.kickScreenTemplate = "&c{server_title}\n&f操作：&e{action}\n&f原因：&e{reason}\n&f解决方法：&b{solution}\n&7处理人：{operator}";
      defaultConfig.punishmentSolutionText = "请勿立即关闭此界面，带边框截图此界面并找管理员申诉，手机用户直接截屏即可";
      defaultConfig.punishmentChatBanAnnouncementEnabled = true;
      defaultConfig.punishmentChatBanAnnouncementTemplate = "&c[{server_title}]&e玩家{player_name}&c{action}&f，时长：&e{duration}&f，原因：&e{reason}&f，处理人：&b{operator}";
      defaultConfig.grimAnticheatEnabled = true;
      defaultConfig.grimFirstBanDuration = "12h";
      defaultConfig.grimSecondBanDuration = "7d";
      defaultConfig.grimThirdBanDuration = "30d";
      defaultConfig.grimPermanentIpBanAfterCount = 4;
      defaultConfig.grimStreakKickThreshold = 3;
      defaultConfig.grimAlertLogCooldownSeconds = 5;
      defaultConfig.grimSafeWalkExemptEnabled = true;
      defaultConfig.punishmentBanSoundEnabled = true;
      defaultConfig.punishmentBanSoundMode = "VANILLA";
      defaultConfig.punishmentBanVanillaSoundIds = new ArrayList<>(List.of("minecraft:entity.lightning_bolt.thunder"));
      defaultConfig.punishmentBanInternalMusicFile = "ban.ogg";
      defaultConfig.votingEnabled = true;
      defaultConfig.voteResultsDetailEnabled = true;
      defaultConfig.voteMaxDurationSeconds = 600;
      defaultConfig.voteMaxOptionCount = 6;
      defaultConfig.muteEnabled = true;
      defaultConfig.muteChatAnnouncementTemplate = "&c[{server_title}] &e{player_name} &c被禁言&f，禁言时长：&e{duration}&f，原因：&e{reason}&f，处理人：&b{operator}";
      defaultConfig.itemClearEnabled = true;
      defaultConfig.itemClearIntervalMinutes = 5;
      defaultConfig.itemClearCountdownSeconds = 10;
      defaultConfig.elytraMovementCheckBypassEnabled = true;
      defaultConfig.memoryPressureThresholdPercent = 70;
      defaultConfig.memoryGcCooldownSeconds = 600;
      defaultConfig.disableModNetworkChecks = true;
      defaultConfig.asyncChunkIoEnabled = true;
      defaultConfig.joinMessageAllowedPlayerUuids = new ArrayList<>();
      defaultConfig.joinMessageEnabled = true;
      defaultConfig.joinMessagePersonalEnabled = true;
      defaultConfig.joinMessageBroadcastEnabled = true;
      defaultConfig.joinMessagePersonalTemplate = "&a欢迎 {player} 加入服务器！";
      defaultConfig.joinMessageBroadcastTemplate = "&e{player} &a进入了服务器。";
      defaultConfig.joinMessagePlayerNameStyle = "&#55FF55&l";
      defaultConfig.joinSoundEnabled = true;
      defaultConfig.joinSoundBroadcastEnabled = true;
      defaultConfig.joinSoundMode = "VANILLA";
      defaultConfig.joinVanillaSoundId = "minecraft:entity.lightning_bolt.thunder";
      defaultConfig.joinVanillaSoundIds = new ArrayList<>(List.of("minecraft:entity.lightning_bolt.thunder"));
      defaultConfig.joinInternalMusicFile = "";
      defaultConfig.joinSoundDelaySeconds = 1;
      defaultConfig.joinMusicResourcePackUrl = "";
      defaultConfig.joinMusicPublicHost = "";
      defaultConfig.joinMusicPublicPort = 8765;
      defaultConfig.joinMusicPublicHttps = false;
      defaultConfig.joinMusicResourcePackRequired = true;
      defaultConfig.joinMusicHttpPort = 8765;
      return defaultConfig;
   }

   private static Map<String, String> createAllConfigChineseComments() {
      Map<String, String> configComments = new LinkedHashMap<>();
      configComments.put("tabServerName", "TAB页眉显示的服务器名称，最多48个Unicode字符。");
      configComments.put("titles", "玩家UUID到称号列表的映射，建议使用游戏内title命令维护，不要手工破坏UUID结构。");
      configComments.put("tabStatusEnabled", "是否显示TAB页眉页脚中的时间、TPS、MSPT和内存状态，默认true。");
      configComments.put("titleEnabled", "是否显示玩家称号，默认true。");
      configComments.put("latencyDisplayEnabled", "是否在玩家头顶和TAB名称中显示Ping，默认true。");
      configComments.put("scheduledAnnouncementEnabled", "是否启用独立定时公告，默认false；开启后按scheduledAnnouncementMode执行全服广播。");
      configComments.put("scheduledAnnouncementMode", "定时公告模式：interval按分钟间隔，daily每天指定时间，weekly每周指定星期和时间，monthly每月指定日期和时间。");
      configComments.put("scheduledAnnouncementIntervalMinutes", "interval模式的公告间隔，单位分钟，允许1至1000000，默认60。");
      configComments.put("scheduledAnnouncementTime", "daily、weekly、monthly模式的触发时间，格式HH:mm，默认20:00，使用服务器本地时区。");
      configComments.put("scheduledAnnouncementWeekday", "weekly模式的星期编号，1为周一、7为周日，默认1。");
      configComments.put("scheduledAnnouncementMonthDay", "monthly模式的日期，允许1至31；当月不存在该日期时跳过，默认1。");
      configComments.put("scheduledAnnouncementTitle", "定时公告标题，支持颜色和格式符号，独立于进服公告标题。");
      configComments.put("scheduledAnnouncementText", "定时公告正文，支持换行、颜色和格式符号，独立于进服公告正文。");
      configComments.put("composterEnabled", "是否启用堆肥桶高频连续填充，默认true。");
      configComments.put("chatLimitEnabled", "是否限制普通玩家聊天频率、长度和重复消息，OP不受影响，默认true。");
      configComments.put("chatCooldownSeconds", "普通玩家两次聊天之间的冷却秒数，允许0至60，默认3。");
      configComments.put("chatMaxLength", "单条聊天最大Unicode字符数，允许1至256，默认128。");
      configComments.put("chatRepeatWindowSeconds", "禁止重复发送相同消息的时间窗口秒数，允许0至300，默认30。");
      configComments.put("chatArchiveEnabled", "是否把成功发送的玩家聊天保存到服务器本地，默认true。");
      configComments.put("chatArchiveRetentionDays", "聊天归档文件保留天数，允许1至3650，默认30。");
      configComments.put("chatArchiveMaxFileSizeMib", "单个聊天归档文件大小上限，单位MiB，允许1至1024，默认16。");
      configComments.put("chatHistoryEnabled", "是否在玩家重新进入时回放最近聊天，默认true。");
      configComments.put("chatHistoryCacheSize", "内存缓存和玩家上线回放的最近聊天条数，允许0至500，默认50。");
      configComments.put("announcementEnabled", "是否启用玩家进入服务器时的公告提醒，默认true。");
      configComments.put("announcementTitle", "公告标题及样式，支持&0-&f颜色码、&k-&o格式码和&r重置码，默认金色粗体服务器公告。");
      configComments.put("announcementText", "公告正文，最多2048个Unicode字符，允许换行；空字符串表示不显示公告。");
      configComments.put("announcementVersion", "公告版本号，使用公告设置命令时自动增加；通常不需要手工修改。");
      configComments.put("announcementShowEveryJoin", "是否每次进入都显示公告，默认true；false时同一公告版本每名玩家只显示一次。");
      configComments.put("memberGlobalMentionEnabled", "是否默认允许普通成员使用@所有人，默认false；OP不受限制。");
      configComments.put("memberGlobalMentionWindowSeconds", "普通成员全服提醒次数统计窗口，单位秒，允许1至3600，默认60。");
      configComments.put("memberGlobalMentionMaxCount", "普通成员在统计窗口内允许的全服提醒次数，允许1至20，默认1。");
      configComments.put("memberGlobalMentionOverrides", "玩家UUID到单独权限的映射，true为允许、false为禁止，未记录则跟随全局开关。");
      configComments.put("memoryOptimizationEnabled", "是否启用保守内存压力管理和自动GC请求，默认true。");
      configComments.put("mentionsEnabled", "是否启用聊天@玩家、补全、高亮和提示音，默认true。");
      configComments.put("mentionActionBarEnabled", "是否在被@时于经验条和护甲值上方显示Action Bar提醒，默认true；关闭后仍保留聊天高亮和音效。");
      configComments.put("dispenserRecipeEnabled", "是否允许使用损耗弓合成发射器，默认true。");
      configComments.put("entityUnloadProtectionEnabled", "是否高优先级保护稳定区块中的生物、船和矿车不在实体区块卸载时消失；新区块加载阶段自动使用原版流程以避免生物鬼畜，默认true。");
      configComments.put("vanillaRedstoneProtectionEnabled", "是否高优先级保护原版红石和TNT机制，默认true。");
      configComments.put("anvilNameColorEnabled", "是否允许铁砧名称使用&颜色和格式码，默认true。");
      configComments.put("debugLoggingEnabled", "是否记录实体卸载和红石保护的详细拦截日志，默认false以避免刷屏。");
      configComments.put("punishmentTemplatesEnabled", "是否启用自定义封禁与踢出断开界面，默认true；false时回退原版提示。");
      configComments.put("punishmentServerTitle", "封禁和踢出界面的独立服务器标题，支持&格式码，不再强制使用TAB名称。");
      configComments.put("banActionText", "封禁界面{action}变量显示的动作文字，页面会自动在前面加入玩家名称，默认显示玩家xxx已被封禁。");
      configComments.put("kickActionText", "踢出界面{action}变量显示的动作文字，页面会自动在前面加入玩家名称，默认显示玩家xxx已被踢出。");
      configComments.put(
         "banScreenTemplate", "封禁界面模板，支持换行、&格式码以及{server_title}、{action}、{reason}、{ban_count}、{solution}、{expires_at}、{operator}变量；{ban_count}为玩家累计封禁次数。"
      );
      configComments.put("kickScreenTemplate", "踢出界面模板，支持换行、&格式码以及{server_title}、{action}、{reason}、{solution}、{operator}变量。");
      configComments.put("punishmentSolutionText", "封禁和踢出界面{solution}变量使用的申诉提示，默认提醒电脑用户带边框截图、手机用户直接截屏后联系管理员，支持&格式码。");
      configComments.put("punishmentChatBanAnnouncementEnabled", "是否在服务器聊天栏广播封禁和踢出信息，默认true；普通命令与Grim自动处罚共用此开关。");
      configComments.put(
         "punishmentChatBanAnnouncementTemplate",
         "封禁和踢出共用的聊天公告主模板，支持{server_title}、{action}、{player_name}、{player_id}、{duration}、{reason}、{operator}，以及&颜色码、&#RRGGBB和&u彩虹符号；踢出时{duration}显示仅本次连接。"
      );
      configComments.put("grimAnticheatEnabled", "是否接收Grim反作弊自动封禁，默认true；关闭后不会执行自动处罚。");
      configComments.put("grimFirstBanDuration", "同一IP第一次被Grim处罚的玩家封禁时长，默认12h。");
      configComments.put("grimSecondBanDuration", "同一IP第二次被Grim处罚的玩家封禁时长，默认7d。");
      configComments.put("grimThirdBanDuration", "同一IP第三次被Grim处罚的玩家封禁时长，默认30d。");
      configComments.put("grimPermanentIpBanAfterCount", "同一IP累计到多少次反作弊封禁时永久banip；默认4，表示超过三次后生效。");
      configComments.put("grimStreakKickThreshold", "同一玩家连续累计多少次反作弊异常后才执行当前轮处罚，默认3；第一次只提醒在线OP，达到阈值后才踢出，重进后再次达到阈值才封禁。");
      configComments.put("grimAlertLogCooldownSeconds", "同一玩家同一检测的Grim警报日志节流时间，单位秒，默认5；设置0关闭节流。");
      configComments.put("grimSafeWalkExemptEnabled", "是否对SafeWalk只报告给在线OP而不执行踢出、封禁或banip，默认true；可使用/greenman config set grimSafeWalkExempt enable或disable调整。");
      configComments.put("punishmentBanSoundEnabled", "封禁聊天公告发送时是否向全服播放音效，默认true。");
      configComments.put("punishmentBanSoundMode", "封禁音效模式：VANILLA播放原版音效，INTERNAL播放GreenManMusic内部音频，BOTH两者都播放，OFF关闭。");
      configComments.put("punishmentBanVanillaSoundIds", "封禁公告播放的原版声音ID数组，最多8个，默认雷声音效。");
      configComments.put("punishmentBanInternalMusicFile", "封禁公告使用的GreenManMusic内部OGG文件名，默认ban.ogg。");
      configComments.put("votingEnabled", "是否允许服务器投票，默认true；管理员发起投票和玩家投票均受此开关影响。");
      configComments.put("voteResultsDetailEnabled", "投票结束后是否公开每个玩家的选择和具体时间，默认true；关闭时只公布汇总。");
      configComments.put("voteMaxDurationSeconds", "单个投票允许的最大持续时间，单位秒，范围1至86400，默认600。");
      configComments.put("voteMaxOptionCount", "单个投票允许的最大选项数，范围2至10，默认6。");
      configComments.put("muteEnabled", "是否启用玩家禁言，默认true；禁言记录保留在独立文件，管理员和普通玩家均按记录限制聊天。控制台可处理所有玩家。");
      configComments.put(
         "muteChatAnnouncementTemplate",
         "禁言聊天公告模板，支持{server_title}、{player_name}、{player_id}、{duration}、{reason}、{operator}、{created_at}、{expires_at}和&颜色格式符号。"
      );
      configComments.put("itemClearEnabled", "是否启用掉落物自动清除，默认true；只清除地面ItemEntity，不影响经验球、生物、船、矿车和其他实体。");
      configComments.put("itemClearIntervalMinutes", "掉落物自动清除间隔，单位分钟，允许1至1000000，默认5。");
      configComments.put("itemClearCountdownSeconds", "掉落物清除前的倒计时秒数，允许1至60，默认10；不超过20秒时先提示起始秒数，再提示10、5、3、2、1；超过20秒时从一半开始折半提示，再衔接10、5、3、2、1；倒计时不改变清除间隔。");
      configComments.put("elytraMovementCheckBypassEnabled", "是否仅在玩家使用鞘翅滑翔时跳过原版移动过快拉回，默认true；普通移动、非法坐标、碰撞校验和其他反作弊逻辑保持原版。");
      configComments.put("memoryPressureThresholdPercent", "触发自动GC判定的最大堆内存使用率百分比，允许60至95，默认70。");
      configComments.put("memoryGcCooldownSeconds", "自动和手动GC请求共用的冷却秒数，允许1至3600，默认600。");
      configComments.put("disableModNetworkChecks", "是否拦截已知模组更新检查、版本检查和遥测主机，默认true；Minecraft登录与正版验证始终放行。");
      configComments.put("asyncChunkIoEnabled", "是否使用原版IOWorker异步协调启动存储预热和关服提前保存，默认true；关闭后完全使用原版保存流程。");
      configComments.put("joinMessageAllowedPlayerUuids", "允许触发进服欢迎的玩家UUID列表，默认空列表；只有列表内玩家进入时才触发个人欢迎、全服欢迎文字和音效，其他玩家进入不播报。");
      configComments.put("joinMessageEnabled", "是否启用玩家进服提示，默认true；可使用/greenman join命令控制。");
      configComments.put("joinMessagePersonalEnabled", "是否向刚加入的玩家发送个人欢迎提示，默认true。");
      configComments.put("joinMessageBroadcastEnabled", "白名单玩家进入时是否向全服在线玩家广播欢迎文字，默认true；关闭后仍可按个人提示开关只欢迎加入者本人。");
      configComments.put("joinSoundEnabled", "是否播放进服音效，默认true。");
      configComments.put("joinSoundBroadcastEnabled", "白名单玩家触发欢迎时是否把音效播放给全服在线玩家，默认true；关闭后只播放给触发欢迎的玩家。");
      configComments.put("joinSoundMode", "进服音效模式：VANILLA同时播放原版音效列表，INTERNAL播放GreenManMusic音频，BOTH同时启用两者，OFF关闭。");
      configComments.put("joinVanillaSoundId", "VANILLA模式的原版声音ID，例如minecraft:entity.lightning_bolt.thunder。");
      configComments.put("joinVanillaSoundIds", "同时播放的原版声音ID数组，最多8个；旧版joinVanillaSoundId会自动迁移到此列表。");
      configComments.put("joinInternalMusicFile", "INTERNAL模式使用GreenManMusic目录中的文件名；OGG可直接使用，MP3/WAV需要服务器安装FFmpeg转换。");
      configComments.put("joinSoundDelaySeconds", "玩家进入后延迟多少秒播放音效，允许0至60，默认1；可使用/greenman join sound delay修改。");
      configComments.put("joinMusicResourcePackUrl", "完整HTTP或HTTPS资源包地址，非空时优先级最高；适合自定义路径、反向代理或CDN，留空时使用下面的公网主机自动生成。");
      configComments.put("joinMusicPublicHost", "内网穿透提供的公网域名或IP，只填写主机即可；误填的http://、端口和路径会自动去除。留空且未填写完整URL时不能下发内部音频。");
      configComments.put("joinMusicPublicPort", "玩家客户端访问内网穿透入口所用的公网端口，允许1至65535；可与本地joinMusicHttpPort不同。");
      configComments.put("joinMusicPublicHttps", "自动生成资源包地址时是否使用HTTPS；默认false。仅当穿透平台或反向代理确实提供HTTPS时开启。");
      configComments.put("joinMusicResourcePackRequired", "是否要求客户端接受自定义音乐资源包；默认true，拒绝时客户端会按原版规则断开。");
      configComments.put("joinMusicHttpPort", "GreenManMusic内置资源包下载服务端口，允许1024至65535，默认8765；防火墙和端口映射需由管理员配置。");
      return configComments;
   }

   private static final class ConfigData {
      private String tabServerName;
      private Map<String, List<GreenManServerConfig.TitleData>> titles;
      private boolean tabStatusEnabled;
      private boolean titleEnabled;
      private boolean latencyDisplayEnabled;
      private boolean scheduledAnnouncementEnabled;
      private String scheduledAnnouncementMode;
      private int scheduledAnnouncementIntervalMinutes;
      private String scheduledAnnouncementTime;
      private int scheduledAnnouncementWeekday;
      private int scheduledAnnouncementMonthDay;
      private String scheduledAnnouncementTitle;
      private String scheduledAnnouncementText;
      private boolean composterEnabled;
      private boolean chatLimitEnabled;
      private int chatCooldownSeconds;
      private int chatMaxLength;
      private int chatRepeatWindowSeconds;
      private boolean chatArchiveEnabled;
      private int chatArchiveRetentionDays;
      private int chatArchiveMaxFileSizeMib;
      private boolean chatHistoryEnabled;
      private int chatHistoryCacheSize;
      private boolean announcementEnabled;
      private String announcementTitle;
      private String announcementText;
      private long announcementVersion;
      private boolean announcementShowEveryJoin;
      private boolean memberGlobalMentionEnabled;
      private int memberGlobalMentionWindowSeconds;
      private int memberGlobalMentionMaxCount;
      private Map<String, Boolean> memberGlobalMentionOverrides;
      private boolean memoryOptimizationEnabled;
      private boolean mentionsEnabled;
      private boolean mentionActionBarEnabled;
      private boolean dispenserRecipeEnabled;
      private boolean entityUnloadProtectionEnabled;
      private boolean vanillaRedstoneProtectionEnabled;
      private boolean anvilNameColorEnabled;
      private boolean debugLoggingEnabled;
      private boolean punishmentTemplatesEnabled;
      private String punishmentServerTitle;
      private String banActionText;
      private String kickActionText;
      private String banScreenTemplate;
      private String kickScreenTemplate;
      private String punishmentSolutionText;
      private boolean punishmentChatBanAnnouncementEnabled;
      private String punishmentChatBanAnnouncementTemplate;
      private boolean grimAnticheatEnabled;
      private String grimFirstBanDuration;
      private String grimSecondBanDuration;
      private String grimThirdBanDuration;
      private int grimPermanentIpBanAfterCount;
      private int grimStreakKickThreshold;
      private int grimAlertLogCooldownSeconds;
      private boolean grimSafeWalkExemptEnabled;
      private boolean punishmentBanSoundEnabled;
      private String punishmentBanSoundMode;
      private List<String> punishmentBanVanillaSoundIds;
      private String punishmentBanInternalMusicFile;
      private boolean votingEnabled;
      private boolean voteResultsDetailEnabled;
      private int voteMaxDurationSeconds;
      private int voteMaxOptionCount;
      private boolean muteEnabled;
      private String muteChatAnnouncementTemplate;
      private boolean itemClearEnabled;
      private int itemClearIntervalMinutes;
      private int itemClearCountdownSeconds;
      private boolean elytraMovementCheckBypassEnabled;
      private int memoryPressureThresholdPercent;
      private int memoryGcCooldownSeconds;
      private boolean disableModNetworkChecks;
      private boolean asyncChunkIoEnabled;
      private List<String> joinMessageAllowedPlayerUuids;
      private boolean joinMessageEnabled;
      private boolean joinMessagePersonalEnabled;
      private boolean joinMessageBroadcastEnabled;
      private transient String joinMessagePersonalTemplate;
      private transient String joinMessageBroadcastTemplate;
      private transient String joinMessagePlayerNameStyle;
      private boolean joinSoundEnabled;
      private boolean joinSoundBroadcastEnabled;
      private String joinSoundMode;
      private String joinVanillaSoundId;
      private List<String> joinVanillaSoundIds;
      private String joinInternalMusicFile;
      private int joinSoundDelaySeconds;
      private String joinMusicResourcePackUrl;
      private String joinMusicPublicHost;
      private int joinMusicPublicPort;
      private boolean joinMusicPublicHttps;
      private boolean joinMusicResourcePackRequired;
      private int joinMusicHttpPort;
   }

   private static final class TitleData {
      private String text;
      private String color;
   }
}
