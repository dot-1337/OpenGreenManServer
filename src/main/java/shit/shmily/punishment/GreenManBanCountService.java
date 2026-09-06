package shit.shmily.punishment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import shit.shmily.GreenManServer;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

public final class GreenManBanCountService {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path HISTORY_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-ban-counts.json");
   private static final Path TEMP_HISTORY_PATH = HISTORY_PATH.resolveSibling("greenmanserver-ban-counts.json.tmp");
   private static final Object HISTORY_LOCK = new Object();
   private static final Map<String, Integer> BAN_COUNTS = new LinkedHashMap<>();
   private static boolean historyLoaded;

   private GreenManBanCountService() {
   }

   public static int incrementBanCount(UUID playerId) {
      if (playerId == null) {
         return 1;
      } else {
         synchronized (HISTORY_LOCK) {
            ensureHistoryLoadedLocked();
            String playerKey = playerId.toString();
            int previousBanCount = Math.max(0, BAN_COUNTS.getOrDefault(playerKey, 0));
            int nextBanCount = previousBanCount == Integer.MAX_VALUE ? Integer.MAX_VALUE : previousBanCount + 1;
            BAN_COUNTS.put(playerKey, nextBanCount);
            if (!saveHistoryLocked()) {
               GreenManServer.LOGGER.warn("保存玩家 {} 的累计封禁次数失败，本次运行仍保留内存记录", playerId);
            }

            return nextBanCount;
         }
      }
   }

   public static int getBanCount(UUID playerId) {
      if (playerId == null) {
         return 0;
      } else {
         synchronized (HISTORY_LOCK) {
            ensureHistoryLoadedLocked();
            return Math.max(0, BAN_COUNTS.getOrDefault(playerId.toString(), 0));
         }
      }
   }

   private static void ensureHistoryLoadedLocked() {
      if (!historyLoaded) {
         BAN_COUNTS.clear();
         if (Files.notExists(HISTORY_PATH)) {
            historyLoaded = true;
         } else {
            try (Reader historyReader = Files.newBufferedReader(HISTORY_PATH, StandardCharsets.UTF_8)) {
               GreenManBanCountService.BanCountData historyData = (GreenManBanCountService.BanCountData)GSON.fromJson(
                  historyReader, GreenManBanCountService.BanCountData.class
               );
               if (historyData != null && historyData.banCounts != null) {
                  for (Entry<String, Integer> historyEntry : historyData.banCounts.entrySet()) {
                     if (historyEntry.getKey() != null && !historyEntry.getKey().isBlank() && historyEntry.getValue() != null && historyEntry.getValue() > 0) {
                        try {
                           BAN_COUNTS.put(UUID.fromString(historyEntry.getKey()).toString(), historyEntry.getValue());
                        } catch (IllegalArgumentException var6) {
                           GreenManServer.LOGGER.warn("忽略累计封禁次数文件中的非法UUID：{}", historyEntry.getKey());
                        }
                     }
                  }
               }
            } catch (RuntimeException | IOException var8) {
               GreenManServer.LOGGER.error("读取玩家累计封禁次数失败，将从空历史继续", var8);
               BAN_COUNTS.clear();
            }

            historyLoaded = true;
         }
      }
   }

   private static boolean saveHistoryLocked() {
      GreenManBanCountService.BanCountData historyData = new GreenManBanCountService.BanCountData();
      historyData.banCounts = new LinkedHashMap<>(BAN_COUNTS);

      try {
         Files.createDirectories(HISTORY_PATH.getParent());

         try (Writer historyWriter = Files.newBufferedWriter(TEMP_HISTORY_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(historyData, historyWriter);
         }

         try {
            Files.move(TEMP_HISTORY_PATH, HISTORY_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException var5) {
            Files.move(TEMP_HISTORY_PATH, HISTORY_PATH, StandardCopyOption.REPLACE_EXISTING);
         }

         return true;
      } catch (RuntimeException | IOException var7) {
         GreenManServer.LOGGER.error("保存玩家累计封禁次数失败", var7);
         return false;
      }
   }

   private static final class BanCountData {
      private Map<String, Integer> banCounts = new LinkedHashMap<>();
   }
}
