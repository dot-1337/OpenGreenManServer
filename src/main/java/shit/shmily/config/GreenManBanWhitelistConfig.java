package shit.shmily.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import shit.shmily.GreenManServer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class GreenManBanWhitelistConfig {
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-ban-whitelist.json");
   private static final Path TEMP_CONFIG_PATH = CONFIG_PATH.resolveSibling("greenmanserver-ban-whitelist.json.tmp");
   private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver.json");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int MAXIMUM_ENTRY_COUNT = 1024;
   private static final int MAXIMUM_ENTRY_CODE_POINTS = 128;
   private static GreenManBanWhitelistConfig.WhitelistData currentConfig = createDefaultConfig();
   private static boolean legacyFieldsCanBeRemoved;

   private GreenManBanWhitelistConfig() {
   }

   public static synchronized void load() {
      legacyFieldsCanBeRemoved = false;

      try {
         Files.createDirectories(CONFIG_PATH.getParent());
      } catch (IOException var7) {
         GreenManServer.LOGGER.error("无法创建封禁白名单配置目录，将使用空白名单", var7);
         currentConfig = createDefaultConfig();
         return;
      }

      boolean configFileExists = Files.exists(CONFIG_PATH);
      GreenManBanWhitelistConfig.WhitelistData loadedConfig = createDefaultConfig();
      boolean configReadSuccessfully = true;
      if (configFileExists) {
         try (Reader configReader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonReader lenientReader = new JsonReader(configReader);
            lenientReader.setStrictness(Strictness.LENIENT);
            JsonElement rootElement = JsonParser.parseReader(lenientReader);
            if (rootElement == null || !rootElement.isJsonObject()) {
               throw new IllegalArgumentException("封禁白名单配置根节点必须是JSON对象");
            }

            loadedConfig = sanitizeConfig(rootElement.getAsJsonObject());
         } catch (RuntimeException | IOException var9) {
            configReadSuccessfully = false;
            GreenManServer.LOGGER.error("读取独立封禁白名单失败，将使用空白名单且不会覆盖原文件", var9);
         }
      }

      boolean legacyEntriesMigrated = configReadSuccessfully && mergeLegacyEntries(loadedConfig);
      currentConfig = loadedConfig;
      boolean shouldSaveConfig = configReadSuccessfully && (!configFileExists || legacyEntriesMigrated || !hasLineComments());
      boolean configSavedSuccessfully = !shouldSaveConfig || save();
      legacyFieldsCanBeRemoved = configReadSuccessfully && (!legacyEntriesMigrated || configSavedSuccessfully);
   }

   public static synchronized List<String> getPlayers() {
      return currentConfig.players == null ? List.of() : List.copyOf(currentConfig.players);
   }

   public static synchronized List<String> getIpAddresses() {
      return currentConfig.ipAddresses == null ? List.of() : List.copyOf(currentConfig.ipAddresses);
   }

   public static synchronized boolean addPlayer(String playerEntry) {
      String sanitizedEntry = sanitizeEntry(playerEntry);
      if (!sanitizedEntry.isEmpty()
         && currentConfig.players != null
         && currentConfig.players.size() < 1024
         && !containsIgnoreCase(currentConfig.players, sanitizedEntry)) {
         List<String> previousEntries = currentConfig.players;
         List<String> updatedEntries = new ArrayList<>(previousEntries);
         updatedEntries.add(sanitizedEntry);
         currentConfig.players = updatedEntries;
         if (save()) {
            return true;
         } else {
            currentConfig.players = previousEntries;
            return false;
         }
      } else {
         return false;
      }
   }

   public static synchronized boolean removePlayer(String playerEntry) {
      String sanitizedEntry = sanitizeEntry(playerEntry);
      if (!sanitizedEntry.isEmpty() && currentConfig.players != null && !currentConfig.players.isEmpty()) {
         List<String> updatedEntries = new ArrayList<>();
         boolean removed = false;

         for (String currentEntry : currentConfig.players) {
            if (currentEntry != null && currentEntry.equalsIgnoreCase(sanitizedEntry)) {
               removed = true;
            } else {
               updatedEntries.add(currentEntry);
            }
         }

         if (!removed) {
            return false;
         } else {
            List<String> previousEntries = currentConfig.players;
            currentConfig.players = updatedEntries;
            if (save()) {
               return true;
            } else {
               currentConfig.players = previousEntries;
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static synchronized boolean addIpAddress(String ipAddress) {
      String normalizedIpAddress = normalizeIpAddress(ipAddress);
      if (normalizedIpAddress != null
         && currentConfig.ipAddresses != null
         && currentConfig.ipAddresses.size() < 1024
         && !containsIgnoreCase(currentConfig.ipAddresses, normalizedIpAddress)) {
         List<String> previousEntries = currentConfig.ipAddresses;
         List<String> updatedEntries = new ArrayList<>(previousEntries);
         updatedEntries.add(normalizedIpAddress);
         currentConfig.ipAddresses = updatedEntries;
         if (save()) {
            return true;
         } else {
            currentConfig.ipAddresses = previousEntries;
            return false;
         }
      } else {
         return false;
      }
   }

   public static synchronized boolean removeIpAddress(String ipAddress) {
      String normalizedIpAddress = normalizeIpAddress(ipAddress);
      if (normalizedIpAddress != null && currentConfig.ipAddresses != null && !currentConfig.ipAddresses.isEmpty()) {
         List<String> updatedEntries = new ArrayList<>();
         boolean removed = false;

         for (String currentEntry : currentConfig.ipAddresses) {
            if (currentEntry != null && currentEntry.equalsIgnoreCase(normalizedIpAddress)) {
               removed = true;
            } else {
               updatedEntries.add(currentEntry);
            }
         }

         if (!removed) {
            return false;
         } else {
            List<String> previousEntries = currentConfig.ipAddresses;
            currentConfig.ipAddresses = updatedEntries;
            if (save()) {
               return true;
            } else {
               currentConfig.ipAddresses = previousEntries;
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static synchronized boolean canRemoveLegacyFields() {
      return legacyFieldsCanBeRemoved;
   }

   private static boolean save() {
      try (Writer configWriter = Files.newBufferedWriter(TEMP_CONFIG_PATH, StandardCharsets.UTF_8)) {
         configWriter.write(createCommentedJson());
      } catch (IOException var7) {
         GreenManServer.LOGGER.error("保存独立封禁白名单临时文件失败", var7);
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
            GreenManServer.LOGGER.error("替换独立封禁白名单配置失败", var3);
            return false;
         }
      }
   }

   private static String normalizeIpAddress(String rawIpAddress) {
      String sanitizedIpAddress = rawIpAddress == null ? "" : rawIpAddress.trim();
      if (!sanitizedIpAddress.isEmpty() && sanitizedIpAddress.matches("[0-9A-Fa-f:.]+")) {
         try {
            String normalizedAddress = InetAddress.getByName(sanitizedIpAddress).getHostAddress();
            int scopeSeparatorIndex = normalizedAddress.indexOf(37);
            return scopeSeparatorIndex >= 0 ? normalizedAddress.substring(0, scopeSeparatorIndex) : normalizedAddress;
         } catch (Exception var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static GreenManBanWhitelistConfig.WhitelistData sanitizeConfig(JsonObject untrustedConfig) {
      GreenManBanWhitelistConfig.WhitelistData sanitizedConfig = createDefaultConfig();
      if (untrustedConfig == null) {
         return sanitizedConfig;
      } else {
         sanitizedConfig.players = readEntries(untrustedConfig, "players");
         sanitizedConfig.ipAddresses = readEntries(untrustedConfig, "ipAddresses");
         return sanitizedConfig;
      }
   }

   private static List<String> readEntries(JsonObject configObject, String propertyName) {
      List<String> sanitizedEntries = new ArrayList<>();
      if (configObject != null && propertyName != null && configObject.has(propertyName) && configObject.get(propertyName).isJsonArray()) {
         for (JsonElement entryElement : configObject.getAsJsonArray(propertyName)) {
            if (entryElement != null && entryElement.isJsonPrimitive()) {
               String sanitizedEntry = sanitizeEntry(entryElement.getAsString());
               if (!sanitizedEntry.isEmpty() && !containsIgnoreCase(sanitizedEntries, sanitizedEntry)) {
                  sanitizedEntries.add(sanitizedEntry);
                  if (sanitizedEntries.size() >= 1024) {
                     break;
                  }
               }
            }
         }

         return sanitizedEntries;
      } else {
         return sanitizedEntries;
      }
   }

   private static boolean mergeLegacyEntries(GreenManBanWhitelistConfig.WhitelistData targetConfig) {
      if (targetConfig != null && !Files.notExists(LEGACY_CONFIG_PATH)) {
         boolean entriesMigrated = false;

         try {
            boolean legacyPlayers;
            try (Reader legacyReader = Files.newBufferedReader(LEGACY_CONFIG_PATH, StandardCharsets.UTF_8)) {
               JsonReader lenientReader = new JsonReader(legacyReader);
               lenientReader.setStrictness(Strictness.LENIENT);
               JsonElement legacyRootElement = JsonParser.parseReader(lenientReader);
               if (legacyRootElement != null && legacyRootElement.isJsonObject()) {
                  List<String> legacyPlayersx = readEntries(legacyRootElement.getAsJsonObject(), "banWhitelistPlayers");
                  List<String> legacyIpAddresses = readEntries(legacyRootElement.getAsJsonObject(), "banWhitelistIpAddresses");
                  entriesMigrated |= mergeEntries(targetConfig.players, legacyPlayersx);
                  return entriesMigrated | mergeEntries(targetConfig.ipAddresses, legacyIpAddresses);
               }

               legacyPlayers = false;
            }

            return legacyPlayers;
         } catch (RuntimeException | IOException var9) {
            GreenManServer.LOGGER.warn("迁移主配置中的旧封禁白名单失败，将保留独立白名单内容", var9);
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean mergeEntries(List<String> targetEntries, List<String> sourceEntries) {
      if (targetEntries != null && sourceEntries != null && !sourceEntries.isEmpty()) {
         boolean changed = false;

         for (String sourceEntry : sourceEntries) {
            if (targetEntries.size() >= 1024) {
               break;
            }

            if (sourceEntry != null && !sourceEntry.isBlank() && !containsIgnoreCase(targetEntries, sourceEntry)) {
               targetEntries.add(sourceEntry);
               changed = true;
            }
         }

         return changed;
      } else {
         return false;
      }
   }

   private static boolean containsIgnoreCase(List<String> entries, String candidateEntry) {
      if (entries != null && candidateEntry != null) {
         for (String existingEntry : entries) {
            if (existingEntry != null && existingEntry.equalsIgnoreCase(candidateEntry)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static String sanitizeEntry(String rawEntry) {
      if (rawEntry == null) {
         return "";
      } else {
         StringBuilder sanitizedBuilder = new StringBuilder();
         int acceptedCodePointCount = 0;
         int characterOffset = 0;

         while (characterOffset < rawEntry.length() && acceptedCodePointCount < 128) {
            int currentCodePoint = rawEntry.codePointAt(characterOffset);
            characterOffset += Character.charCount(currentCodePoint);
            if (!Character.isISOControl(currentCodePoint) && currentCodePoint != 167) {
               sanitizedBuilder.appendCodePoint(currentCodePoint);
               acceptedCodePointCount++;
            }
         }

         return sanitizedBuilder.toString().trim();
      }
   }

   private static boolean hasLineComments() {
      if (Files.notExists(CONFIG_PATH)) {
         return false;
      } else {
         try {
            return Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8)
               .stream()
               .anyMatch(configLine -> configLine != null && configLine.trim().startsWith("//"));
         } catch (IOException var1) {
            return true;
         }
      }
   }

   private static String createCommentedJson() {
      String prettyJson = GSON.toJson(currentConfig);
      StringBuilder commentedBuilder = new StringBuilder(prettyJson.length() + 320);
      commentedBuilder.append("// GreenManServer封禁白名单：游戏内白名单指令修改后会立即保存并生效\n");

      for (String jsonLine : prettyJson.split("\\n", -1)) {
         if (jsonLine.trim().startsWith("\"players\":")) {
            commentedBuilder.append("  // 玩家白名单：可填写玩家名或UUID，名单内玩家不会被手动或反作弊封禁\n");
         }

         if (jsonLine.trim().startsWith("\"ipAddresses\":")) {
            commentedBuilder.append("  // IP白名单：可填写IPv4或IPv6，名单内地址不会被手动或反作弊banip\n");
         }

         commentedBuilder.append(jsonLine).append('\n');
      }

      return commentedBuilder.toString().replaceFirst("\\n$", "") + System.lineSeparator();
   }

   private static GreenManBanWhitelistConfig.WhitelistData createDefaultConfig() {
      GreenManBanWhitelistConfig.WhitelistData defaultConfig = new GreenManBanWhitelistConfig.WhitelistData();
      defaultConfig.players = new ArrayList<>();
      defaultConfig.ipAddresses = new ArrayList<>();
      return defaultConfig;
   }

   private static final class WhitelistData {
      private List<String> players;
      private List<String> ipAddresses;
   }
}
