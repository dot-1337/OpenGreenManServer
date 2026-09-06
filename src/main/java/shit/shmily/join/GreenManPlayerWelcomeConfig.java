package shit.shmily.join;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import shit.shmily.GreenManServer;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

public final class GreenManPlayerWelcomeConfig {
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-player-welcome.json");
   private static final Path TEMP_CONFIG_PATH = CONFIG_PATH.resolveSibling("greenmanserver-player-welcome.json.tmp");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int MAX_MESSAGE_CODE_POINTS = 512;
   private static final int MAX_NAME_STYLE_CODE_POINTS = 64;
   private static final int MAX_MUSIC_FILE_CODE_POINTS = 128;
   private static final int MAX_VANILLA_SOUND_COUNT = 8;
   private static final int MAX_SOUND_DELAY_SECONDS = 60;
   private static volatile Map<UUID, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> currentSettings = Map.of();
   private static volatile boolean lastLoadSucceeded = true;

   private GreenManPlayerWelcomeConfig() {
   }

   public static synchronized void load() {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());
      } catch (IOException var10) {
         GreenManServer.LOGGER.error("无法创建玩家专属欢迎配置目录，将全部回退全局欢迎设置", var10);
         lastLoadSucceeded = false;
         return;
      }

      if (Files.notExists(CONFIG_PATH)) {
         currentSettings = Map.of();
         lastLoadSucceeded = true;
         saveDefaultFile();
      } else {
         try {
            String configText = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            JsonObject rawConfigObject = (JsonObject)GSON.fromJson(configText, JsonObject.class);
            GreenManPlayerWelcomeConfig.ConfigData loadedConfig = (GreenManPlayerWelcomeConfig.ConfigData)GSON.fromJson(
               rawConfigObject, GreenManPlayerWelcomeConfig.ConfigData.class
            );
            if (loadedConfig == null || loadedConfig.players == null || loadedConfig.players.isEmpty()) {
               currentSettings = Map.of();
               lastLoadSucceeded = true;
               return;
            }

            boolean migrateSoundBroadcastDefault = rawConfigObject != null && (!rawConfigObject.has("schemaVersion") || loadedConfig.schemaVersion < 2);
            Map<UUID, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> sanitizedSettings = new LinkedHashMap<>();

            for (Entry<String, GreenManPlayerWelcomeConfig.PlayerWelcomeData> playerEntry : loadedConfig.players.entrySet()) {
               if (playerEntry.getKey() != null && playerEntry.getValue() != null) {
                  try {
                     UUID playerUuid = UUID.fromString(playerEntry.getKey().trim());
                     GreenManPlayerWelcomeConfig.PlayerWelcomeSettings playerSettings = sanitizePlayerSettings(playerEntry.getValue());
                     if (migrateSoundBroadcastDefault) {
                        playerSettings = copyWithSoundBroadcast(playerSettings, true);
                     }

                     sanitizedSettings.put(playerUuid, playerSettings);
                  } catch (IllegalArgumentException var9) {
                     GreenManServer.LOGGER.warn("玩家专属欢迎配置包含无效 UUID，已忽略：{}", playerEntry.getKey());
                  }
               }
            }

            currentSettings = Map.copyOf(sanitizedSettings);
            lastLoadSucceeded = true;
            if (migrateSoundBroadcastDefault) {
               saveCurrentSettings();
            }

            GreenManServer.LOGGER.info("已加载 {} 条玩家专属进服欢迎配置", currentSettings.size());
         } catch (RuntimeException | IOException var11) {
            GreenManServer.LOGGER.error("无法读取玩家专属欢迎配置，将全部回退全局欢迎设置", var11);
            lastLoadSucceeded = false;
         }
      }
   }

   public static Optional<GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> getEnabledSettings(UUID playerUuid) {
      if (playerUuid == null) {
         return Optional.empty();
      } else {
         GreenManPlayerWelcomeConfig.PlayerWelcomeSettings playerSettings = currentSettings.get(playerUuid);
         return playerSettings != null && playerSettings.enabled() ? Optional.of(playerSettings) : Optional.empty();
      }
   }

   public static int getConfiguredPlayerCount() {
      return currentSettings.size();
   }

   public static synchronized boolean ensurePlayers(Collection<UUID> playerUuids) {
      if (playerUuids != null && !playerUuids.isEmpty()) {
         Map<UUID, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> updatedSettings = new LinkedHashMap<>(currentSettings);
         boolean changed = false;

         for (UUID playerUuid : playerUuids) {
            if (playerUuid != null && !updatedSettings.containsKey(playerUuid)) {
               updatedSettings.put(playerUuid, sanitizePlayerSettings(new GreenManPlayerWelcomeConfig.PlayerWelcomeData()));
               changed = true;
            }
         }

         if (!changed) {
            return true;
         } else {
            currentSettings = Map.copyOf(updatedSettings);
            return saveCurrentSettings();
         }
      } else {
         return true;
      }
   }

   public static synchronized boolean enablePlayers(Collection<UUID> playerUuids) {
      if (playerUuids != null && !playerUuids.isEmpty()) {
         Map<UUID, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> updatedSettings = new LinkedHashMap<>(currentSettings);
         boolean changed = false;

         for (UUID playerUuid : playerUuids) {
            if (playerUuid != null) {
               GreenManPlayerWelcomeConfig.PlayerWelcomeSettings existingSettings = updatedSettings.get(playerUuid);
               if (existingSettings == null) {
                  updatedSettings.put(playerUuid, sanitizePlayerSettings(new GreenManPlayerWelcomeConfig.PlayerWelcomeData()));
                  changed = true;
               } else if (!existingSettings.enabled()) {
                  updatedSettings.put(playerUuid, copyWithEnabled(existingSettings, true));
                  changed = true;
               }
            }
         }

         if (!changed) {
            return true;
         } else {
            currentSettings = Map.copyOf(updatedSettings);
            return saveCurrentSettings();
         }
      } else {
         return true;
      }
   }

   public static boolean syncAllowedPlayerTexts(Collection<String> playerUuidTexts) {
      if (!lastLoadSucceeded) {
         GreenManServer.LOGGER.warn("玩家专属欢迎配置读取失败，已跳过名单模板同步以保护原有模板");
         return false;
      } else if (playerUuidTexts != null && !playerUuidTexts.isEmpty()) {
         List<UUID> parsedPlayerUuids = new ArrayList<>();

         for (String playerUuidText : playerUuidTexts) {
            if (playerUuidText != null && !playerUuidText.isBlank()) {
               try {
                  parsedPlayerUuids.add(UUID.fromString(playerUuidText.trim()));
               } catch (IllegalArgumentException var5) {
                  GreenManServer.LOGGER.warn("进服欢迎名单包含无效UUID，未生成专属模板：{}", playerUuidText);
               }
            }
         }

         return enablePlayers(parsedPlayerUuids);
      } else {
         return true;
      }
   }

   public static synchronized boolean disablePlayer(UUID playerUuid) {
      if (playerUuid == null) {
         return false;
      } else {
         GreenManPlayerWelcomeConfig.PlayerWelcomeSettings existingSettings = currentSettings.get(playerUuid);
         if (existingSettings != null && existingSettings.enabled()) {
            GreenManPlayerWelcomeConfig.PlayerWelcomeSettings disabledSettings = copyWithEnabled(existingSettings, false);
            Map<UUID, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> updatedSettings = new LinkedHashMap<>(currentSettings);
            updatedSettings.put(playerUuid, disabledSettings);
            currentSettings = Map.copyOf(updatedSettings);
            return saveCurrentSettings();
         } else {
            return true;
         }
      }
   }

   private static GreenManPlayerWelcomeConfig.PlayerWelcomeSettings copyWithEnabled(
      GreenManPlayerWelcomeConfig.PlayerWelcomeSettings existingSettings, boolean enabled
   ) {
      return existingSettings == null
         ? sanitizePlayerSettings(new GreenManPlayerWelcomeConfig.PlayerWelcomeData())
         : new GreenManPlayerWelcomeConfig.PlayerWelcomeSettings(
            enabled,
            existingSettings.personalMessageEnabled(),
            existingSettings.broadcastMessageEnabled(),
            existingSettings.personalMessageTemplate(),
            existingSettings.playerNameStyle(),
            existingSettings.soundEnabled(),
            existingSettings.soundMode(),
            existingSettings.vanillaSoundIds(),
            existingSettings.internalMusicFile(),
            existingSettings.soundDelaySeconds(),
            existingSettings.soundBroadcastEnabled()
         );
   }

   private static GreenManPlayerWelcomeConfig.PlayerWelcomeSettings copyWithSoundBroadcast(
      GreenManPlayerWelcomeConfig.PlayerWelcomeSettings existingSettings, boolean soundBroadcastEnabled
   ) {
      return existingSettings == null
         ? sanitizePlayerSettings(new GreenManPlayerWelcomeConfig.PlayerWelcomeData())
         : new GreenManPlayerWelcomeConfig.PlayerWelcomeSettings(
            existingSettings.enabled(),
            existingSettings.personalMessageEnabled(),
            existingSettings.broadcastMessageEnabled(),
            existingSettings.personalMessageTemplate(),
            existingSettings.playerNameStyle(),
            existingSettings.soundEnabled(),
            existingSettings.soundMode(),
            existingSettings.vanillaSoundIds(),
            existingSettings.internalMusicFile(),
            existingSettings.soundDelaySeconds(),
            soundBroadcastEnabled
         );
   }

   private static GreenManPlayerWelcomeConfig.PlayerWelcomeSettings sanitizePlayerSettings(GreenManPlayerWelcomeConfig.PlayerWelcomeData rawSettings) {
      GreenManPlayerWelcomeConfig.PlayerWelcomeData safeSettings = rawSettings == null ? new GreenManPlayerWelcomeConfig.PlayerWelcomeData() : rawSettings;
      String personalTemplate = sanitizeText(safeSettings.personalMessageTemplate, "&a欢迎 {player} 进入服务器！", 512);
      String playerNameStyle = sanitizeText(safeSettings.playerNameStyle, "&a", 64);
      String soundMode = sanitizeSoundMode(safeSettings.soundMode);
      List<String> vanillaSoundIds = sanitizeVanillaSoundIds(safeSettings.vanillaSoundIds);
      String internalMusicFile = sanitizeMusicFileName(safeSettings.internalMusicFile);
      int soundDelaySeconds = Math.max(0, Math.min(60, safeSettings.soundDelaySeconds));
      return new GreenManPlayerWelcomeConfig.PlayerWelcomeSettings(
         safeSettings.enabled,
         safeSettings.personalMessageEnabled,
         safeSettings.broadcastMessageEnabled,
         personalTemplate,
         playerNameStyle,
         safeSettings.soundEnabled,
         soundMode,
         vanillaSoundIds,
         internalMusicFile,
         soundDelaySeconds,
         safeSettings.soundBroadcastEnabled
      );
   }

   private static String sanitizeText(String rawText, String fallbackText, int maximumCodePoints) {
      String safeText = rawText == null ? fallbackText : rawText;
      if (maximumCodePoints <= 0) {
         return "";
      } else {
         int codePointCount = safeText.codePointCount(0, safeText.length());
         if (codePointCount <= maximumCodePoints) {
            return safeText;
         } else {
            int endIndex = safeText.offsetByCodePoints(0, maximumCodePoints);
            return safeText.substring(0, endIndex);
         }
      }
   }

   private static String sanitizeSoundMode(String rawSoundMode) {
      String normalizedMode = rawSoundMode == null ? "OFF" : rawSoundMode.trim().toUpperCase(Locale.ROOT);
      return !"VANILLA".equals(normalizedMode) && !"INTERNAL".equals(normalizedMode) && !"BOTH".equals(normalizedMode) && !"OFF".equals(normalizedMode)
         ? "OFF"
         : normalizedMode;
   }

   private static List<String> sanitizeVanillaSoundIds(List<String> rawSoundIds) {
      if (rawSoundIds != null && !rawSoundIds.isEmpty()) {
         LinkedHashSet<String> sanitizedSoundIds = new LinkedHashSet<>();

         for (String rawSoundId : rawSoundIds) {
            if (sanitizedSoundIds.size() >= 8) {
               break;
            }

            if (rawSoundId != null && !rawSoundId.isBlank()) {
               String safeSoundId = sanitizeText(rawSoundId.trim(), "", 256);
               if (!safeSoundId.isEmpty()) {
                  sanitizedSoundIds.add(safeSoundId);
               }
            }
         }

         return List.copyOf(sanitizedSoundIds);
      } else {
         return List.of();
      }
   }

   private static String sanitizeMusicFileName(String rawFileName) {
      if (rawFileName != null && !rawFileName.isBlank()) {
         String safeFileName = sanitizeText(rawFileName.trim(), "", 128);
         return !safeFileName.contains("/") && !safeFileName.contains("\\") && !safeFileName.contains("..") ? safeFileName : "";
      } else {
         return "";
      }
   }

   private static void saveDefaultFile() {
      GreenManPlayerWelcomeConfig.ConfigData defaultConfig = new GreenManPlayerWelcomeConfig.ConfigData();

      try (Writer configWriter = Files.newBufferedWriter(TEMP_CONFIG_PATH, StandardCharsets.UTF_8)) {
         configWriter.write(createCommentedConfigJson(defaultConfig));
      } catch (RuntimeException | IOException var8) {
         GreenManServer.LOGGER.error("无法创建玩家专属欢迎配置文件", var8);
         return;
      }

      try {
         Files.move(TEMP_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var6) {
         try {
            Files.move(TEMP_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException var5) {
            GreenManServer.LOGGER.error("无法保存玩家专属欢迎配置文件", var5);
         }
      }
   }

   private static boolean saveCurrentSettings() {
      GreenManPlayerWelcomeConfig.ConfigData outputConfig = new GreenManPlayerWelcomeConfig.ConfigData();

      for (Entry<UUID, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> entry : currentSettings.entrySet()) {
         if (entry.getKey() != null && entry.getValue() != null) {
            outputConfig.players.put(entry.getKey().toString(), toPlayerData(entry.getValue()));
         }
      }

      try (Writer configWriter = Files.newBufferedWriter(TEMP_CONFIG_PATH, StandardCharsets.UTF_8)) {
         configWriter.write(createCommentedConfigJson(outputConfig));
      } catch (RuntimeException | IOException var8) {
         GreenManServer.LOGGER.error("无法保存玩家专属欢迎配置", var8);
         return false;
      }

      try {
         Files.move(TEMP_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         lastLoadSucceeded = true;
         return true;
      } catch (IOException var6) {
         try {
            Files.move(TEMP_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            lastLoadSucceeded = true;
            return true;
         } catch (IOException var4) {
            GreenManServer.LOGGER.error("无法替换玩家专属欢迎配置文件", var4);
            return false;
         }
      }
   }

   private static String createCommentedConfigJson(GreenManPlayerWelcomeConfig.ConfigData configData) {
      JsonObject configObject = JsonParser.parseString(GSON.toJson(configData)).getAsJsonObject();
      configObject.remove("_中文说明_优先级");
      configObject.remove("_中文说明_文字模板");
      configObject.remove("_中文说明_文字范围");
      configObject.remove("_中文说明_声音");
      configObject.remove("_中文说明_内部音频");
      configObject.remove("_中文说明_配置示例");
      String prettyJson = GSON.toJson(configObject);
      Map<String, String> comments = new LinkedHashMap<>();
      comments.put("schemaVersion", "配置结构版本，版本2默认把欢迎音效播放给全服在线玩家。");
      comments.put("players", "玩家UUID到专属欢迎设置的映射；enabled为false时回退主配置全局欢迎设置。");
      comments.put("enabled", "是否启用该玩家专属欢迎配置，关闭后回退全局配置。");
      comments.put("personalMessageEnabled", "是否向刚加入的玩家发送个人欢迎文字。");
      comments.put("broadcastMessageEnabled", "是否向全服在线玩家广播该玩家的欢迎文字。");
      comments.put("personalMessageTemplate", "个人和全服欢迎使用的文字模板，支持玩家名、服务器名和颜色格式符号。");
      comments.put("playerNameStyle", "欢迎模板中玩家名称使用的颜色和样式符号。");
      comments.put("soundEnabled", "是否播放该玩家的进服音效。");
      comments.put("soundMode", "音效模式：VANILLA、INTERNAL、BOTH或OFF。");
      comments.put("vanillaSoundIds", "同时播放的原版声音ID列表，最多八个。");
      comments.put("internalMusicFile", "GreenManMusic目录中的内部音频文件名，不允许目录穿越。");
      comments.put("soundDelaySeconds", "玩家进入后延迟播放音效的秒数，允许0至60。");
      comments.put("soundBroadcastEnabled", "是否把该玩家触发的欢迎音效播放给全服在线玩家。");
      Pattern propertyPattern = Pattern.compile("^(\\s*)\\\"([^\\\"]+)\\\":.*$");
      StringBuilder commentedJson = new StringBuilder(prettyJson.length() + comments.size() * 60);
      commentedJson.append("// 玩家专属进服欢迎配置：每个配置项上方的中文说明仅供阅读。\n");

      for (String jsonLine : prettyJson.split("\\n", -1)) {
         Matcher propertyMatcher = propertyPattern.matcher(jsonLine);
         if (propertyMatcher.matches()) {
            String propertyComment = comments.get(propertyMatcher.group(2));
            if (propertyComment != null) {
               commentedJson.append(propertyMatcher.group(1)).append("// ").append(propertyComment).append('\n');
            }
         }

         commentedJson.append(jsonLine).append('\n');
      }

      return commentedJson.toString().replaceFirst("\\n$", "") + System.lineSeparator();
   }

   private static GreenManPlayerWelcomeConfig.PlayerWelcomeData toPlayerData(GreenManPlayerWelcomeConfig.PlayerWelcomeSettings settings) {
      GreenManPlayerWelcomeConfig.PlayerWelcomeData playerData = new GreenManPlayerWelcomeConfig.PlayerWelcomeData();
      playerData.enabled = settings.enabled();
      playerData.personalMessageEnabled = settings.personalMessageEnabled();
      playerData.broadcastMessageEnabled = settings.broadcastMessageEnabled();
      playerData.personalMessageTemplate = settings.personalMessageTemplate();
      playerData.playerNameStyle = settings.playerNameStyle();
      playerData.soundEnabled = settings.soundEnabled();
      playerData.soundMode = settings.soundMode();
      playerData.vanillaSoundIds = settings.vanillaSoundIds();
      playerData.internalMusicFile = settings.internalMusicFile();
      playerData.soundDelaySeconds = settings.soundDelaySeconds();
      playerData.soundBroadcastEnabled = settings.soundBroadcastEnabled();
      return playerData;
   }

   private static final class ConfigData {
      private int schemaVersion = 2;
      private Map<String, GreenManPlayerWelcomeConfig.PlayerWelcomeData> players = new LinkedHashMap<>();
   }

   private static final class PlayerWelcomeData {
      private boolean enabled = true;
      private boolean personalMessageEnabled = true;
      private boolean broadcastMessageEnabled = true;
      private String personalMessageTemplate = "&a欢迎 {player} 进入服务器！";
      private String playerNameStyle = "&a";
      private boolean soundEnabled = true;
      private String soundMode = "VANILLA";
      private List<String> vanillaSoundIds = List.of("minecraft:block.note_block.bell");
      private String internalMusicFile = "";
      private int soundDelaySeconds = 1;
      private boolean soundBroadcastEnabled = true;
   }

   public record PlayerWelcomeSettings(
      boolean enabled,
      boolean personalMessageEnabled,
      boolean broadcastMessageEnabled,
      String personalMessageTemplate,
      String playerNameStyle,
      boolean soundEnabled,
      String soundMode,
      List<String> vanillaSoundIds,
      String internalMusicFile,
      int soundDelaySeconds,
      boolean soundBroadcastEnabled
   ) {
      public PlayerWelcomeSettings(
         boolean enabled,
         boolean personalMessageEnabled,
         boolean broadcastMessageEnabled,
         String personalMessageTemplate,
         String playerNameStyle,
         boolean soundEnabled,
         String soundMode,
         List<String> vanillaSoundIds,
         String internalMusicFile,
         int soundDelaySeconds,
         boolean soundBroadcastEnabled
      ) {
         vanillaSoundIds = vanillaSoundIds == null ? List.of() : List.copyOf(vanillaSoundIds);
         this.enabled = enabled;
         this.personalMessageEnabled = personalMessageEnabled;
         this.broadcastMessageEnabled = broadcastMessageEnabled;
         this.personalMessageTemplate = personalMessageTemplate;
         this.playerNameStyle = playerNameStyle;
         this.soundEnabled = soundEnabled;
         this.soundMode = soundMode;
         this.vanillaSoundIds = vanillaSoundIds;
         this.internalMusicFile = internalMusicFile;
         this.soundDelaySeconds = soundDelaySeconds;
         this.soundBroadcastEnabled = soundBroadcastEnabled;
      }
   }
}
