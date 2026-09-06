package shit.shmily.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import shit.shmily.GreenManServer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class GreenManTweakerooWhitelistConfig {
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-tweakeroo-whitelist.json");
   private static final Path TEMP_CONFIG_PATH = CONFIG_PATH.resolveSibling("greenmanserver-tweakeroo-whitelist.json.tmp");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final int MAXIMUM_ENTRY_COUNT = 1024;
   private static final int MAXIMUM_ENTRY_CODE_POINTS = 128;
   private static List<String> playerEntries = List.of();

   private GreenManTweakerooWhitelistConfig() {
   }

   public static synchronized void load() {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());
      } catch (IOException var8) {
         GreenManServer.LOGGER.error("无法创建Tweakeroo兼容名单配置目录，将使用空名单", var8);
         playerEntries = List.of();
         return;
      }

      if (Files.notExists(CONFIG_PATH)) {
         playerEntries = List.of();
         save();
      } else {
         List<String> loadedEntries = new ArrayList<>();

         try (Reader configReader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonReader lenientReader = new JsonReader(configReader);
            lenientReader.setStrictness(Strictness.LENIENT);
            JsonElement rootElement = JsonParser.parseReader(lenientReader);
            if (rootElement == null || !rootElement.isJsonObject() || !rootElement.getAsJsonObject().has("players")) {
               throw new IllegalArgumentException("Tweakeroo兼容名单根节点必须包含players数组");
            }

            if (!rootElement.getAsJsonObject().get("players").isJsonArray()) {
               throw new IllegalArgumentException("Tweakeroo兼容名单players必须是数组");
            }

            for (JsonElement entryElement : rootElement.getAsJsonObject().getAsJsonArray("players")) {
               if (entryElement != null && entryElement.isJsonPrimitive()) {
                  String sanitizedEntry = sanitizeEntry(entryElement.getAsString());
                  if (!sanitizedEntry.isEmpty() && !containsIgnoreCase(loadedEntries, sanitizedEntry)) {
                     loadedEntries.add(sanitizedEntry);
                     if (loadedEntries.size() >= 1024) {
                        break;
                     }
                  }
               }
            }

            playerEntries = List.copyOf(loadedEntries);
         } catch (RuntimeException | IOException var10) {
            GreenManServer.LOGGER.error("读取Tweakeroo兼容名单失败，将使用空名单", var10);
            playerEntries = List.of();
         }
      }
   }

   public static synchronized boolean containsPlayer(String playerName) {
      if (playerName != null && !playerName.isBlank()) {
         for (String entry : playerEntries) {
            if (entry != null && entry.equalsIgnoreCase(playerName.trim())) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static synchronized boolean addPlayer(String playerName) {
      String sanitizedEntry = sanitizeEntry(playerName);
      if (!sanitizedEntry.isEmpty() && !containsPlayer(sanitizedEntry) && playerEntries.size() < 1024) {
         List<String> updatedEntries = new ArrayList<>(playerEntries);
         updatedEntries.add(sanitizedEntry);
         List<String> previousEntries = playerEntries;
         playerEntries = List.copyOf(updatedEntries);
         if (save()) {
            return true;
         } else {
            playerEntries = previousEntries;
            return false;
         }
      } else {
         return false;
      }
   }

   public static synchronized boolean removePlayer(String playerName) {
      String sanitizedEntry = sanitizeEntry(playerName);
      if (sanitizedEntry.isEmpty()) {
         return false;
      } else {
         List<String> updatedEntries = new ArrayList<>();
         boolean removed = false;

         for (String entry : playerEntries) {
            if (entry != null && entry.equalsIgnoreCase(sanitizedEntry)) {
               removed = true;
            } else {
               updatedEntries.add(entry);
            }
         }

         if (!removed) {
            return false;
         } else {
            List<String> previousEntries = playerEntries;
            playerEntries = List.copyOf(updatedEntries);
            if (save()) {
               return true;
            } else {
               playerEntries = previousEntries;
               return false;
            }
         }
      }
   }

   public static synchronized List<String> getPlayers() {
      return List.copyOf(playerEntries);
   }

   private static boolean save() {
      try (Writer configWriter = Files.newBufferedWriter(TEMP_CONFIG_PATH, StandardCharsets.UTF_8)) {
         configWriter.write(createCommentedJson());
      } catch (IOException var7) {
         GreenManServer.LOGGER.error("保存Tweakeroo兼容名单临时文件失败", var7);
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
            GreenManServer.LOGGER.error("替换Tweakeroo兼容名单配置失败", var3);
            return false;
         }
      }
   }

   private static String createCommentedJson() {
      Map<String, Object> rootObject = new LinkedHashMap<>();
      rootObject.put("players", playerEntries);
      String prettyJson = GSON.toJson(rootObject);
      return "// Tweakeroo兼容名单：填写玩家名或UUID后使用/greenman config reload立即生效\n"
         + prettyJson.replace(
            "  \"players\":", "  // 放宽伪潜行放置、持续左键和持续右键，并在名单玩家使用鞘翅时跳过Elytra检测、移动预测和拉回\n  // 非鞘翅飞行、速度、Reach、Hitboxes、Timer和战斗检测仍然生效\n  \"players\":"
         );
   }

   private static String sanitizeEntry(String rawEntry) {
      if (rawEntry != null && !rawEntry.isBlank()) {
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
      } else {
         return "";
      }
   }

   private static boolean containsIgnoreCase(List<String> entries, String candidateEntry) {
      for (String entry : entries) {
         if (entry != null && entry.equalsIgnoreCase(candidateEntry)) {
            return true;
         }
      }

      return false;
   }
}
