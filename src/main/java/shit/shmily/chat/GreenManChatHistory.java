package shit.shmily.chat;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.performance.GreenManBackgroundTaskManager;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManChatHistory {
   private static final Path ARCHIVE_DIRECTORY = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("greenmanserver-chat");
   private static final int MAXIMUM_TAIL_BYTES_PER_FILE = 524288;
   private static final int MAXIMUM_ARCHIVE_FILES_TO_READ = 4;
   private static final int MAXIMUM_MESSAGE_CODE_POINTS = 1024;
   private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA).withZone(ZoneId.of("Asia/Shanghai"));
   private static final Object HISTORY_LOCK = new Object();
   private static final ArrayDeque<GreenManChatHistory.ChatHistoryEntry> RECENT_MESSAGES = new ArrayDeque<>();
   private static final AtomicBoolean WARMUP_COMPLETE = new AtomicBoolean(false);
   private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

   private GreenManChatHistory() {
   }

   public static void register() {
      if (REGISTERED.compareAndSet(false, true)) {
         ServerMessageEvents.CHAT_MESSAGE.register(GreenManChatHistory::cacheChatMessage);
         ServerLifecycleEvents.SERVER_STARTED.register(GreenManChatHistory::startWarmup);
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> clearRuntimeState());
      }
   }

   public static void requestWarmup(MinecraftServer server) {
      if (server != null && GreenManServerConfig.isChatHistoryEnabled() && GreenManServerConfig.getChatHistoryCacheSize() > 0) {
         clearHistory();
         WARMUP_COMPLETE.set(false);
         boolean taskAccepted = GreenManBackgroundTaskManager.submit("重新预热最近聊天缓存", () -> warmupFromArchive(server));
         if (!taskAccepted) {
            WARMUP_COMPLETE.set(true);
         }
      } else {
         clearHistory();
         WARMUP_COMPLETE.set(true);
      }
   }

   public static void handlePlayerJoin(ServerPlayerEntity joinedPlayer) {
      if (joinedPlayer != null && GreenManServerConfig.isChatHistoryEnabled() && GreenManServerConfig.getChatHistoryCacheSize() > 0) {
         if (WARMUP_COMPLETE.get()) {
            List<GreenManChatHistory.ChatHistoryEntry> historySnapshot = getSnapshot();
            if (!historySnapshot.isEmpty()) {
               joinedPlayer.sendMessage(Text.literal("[最近聊天记录]").formatted(Formatting.DARK_GRAY));

               for (GreenManChatHistory.ChatHistoryEntry historyEntry : historySnapshot) {
                  if (historyEntry != null) {
                     joinedPlayer.sendMessage(formatHistoryComponent(historyEntry));
                  }
               }
            }
         }
      }
   }

   public static void clearHistory() {
      synchronized (HISTORY_LOCK) {
         RECENT_MESSAGES.clear();
      }
   }

   public static void trimToConfiguredSize() {
      int maximumCacheSize = GreenManServerConfig.getChatHistoryCacheSize();
      synchronized (HISTORY_LOCK) {
         while (RECENT_MESSAGES.size() > maximumCacheSize) {
            RECENT_MESSAGES.pollFirst();
         }
      }
   }

   public static int getCachedMessageCount() {
      synchronized (HISTORY_LOCK) {
         return RECENT_MESSAGES.size();
      }
   }

   public static void cacheSystemMessage(String messageContent) {
      if (GreenManServerConfig.isChatHistoryEnabled()
         && GreenManServerConfig.getChatHistoryCacheSize() > 0
         && messageContent != null
         && !messageContent.isBlank()) {
         String sanitizedMessage = sanitizeText(messageContent, 1024);
         if (!sanitizedMessage.isEmpty()) {
            addEntry(new GreenManChatHistory.ChatHistoryEntry(Instant.now(), "服务器", sanitizedMessage));
         }
      }
   }

   private static void cacheChatMessage(SignedMessage playerChatMessage, ServerPlayerEntity sender, Parameters chatType) {
      if (GreenManServerConfig.isChatHistoryEnabled() && GreenManServerConfig.getChatHistoryCacheSize() > 0 && playerChatMessage != null && sender != null) {
         String sanitizedContent = sanitizeText(playerChatMessage.getSignedContent(), 1024);
         if (!sanitizedContent.isEmpty()) {
            String sanitizedSenderName = sanitizeText(sender.getGameProfile().name(), 64);
            String effectiveSenderName = sanitizedSenderName.isEmpty() ? sender.getName().getString() : sanitizedSenderName;
            GreenManChatHistory.ChatHistoryEntry historyEntry = new GreenManChatHistory.ChatHistoryEntry(Instant.now(), effectiveSenderName, sanitizedContent);
            addEntry(historyEntry);
         }
      }
   }

   private static void startWarmup(MinecraftServer server) {
      WARMUP_COMPLETE.set(false);
      clearHistory();
      if (GreenManServerConfig.isChatHistoryEnabled() && GreenManServerConfig.getChatHistoryCacheSize() > 0) {
         boolean taskAccepted = GreenManBackgroundTaskManager.submit("预热最近聊天缓存", () -> warmupFromArchive(server));
         if (!taskAccepted) {
            WARMUP_COMPLETE.set(true);
         }
      } else {
         WARMUP_COMPLETE.set(true);
      }
   }

   private static void warmupFromArchive(MinecraftServer server) {
      List<GreenManChatHistory.ChatHistoryEntry> loadedEntries = new ArrayList<>();
      if (Files.isDirectory(ARCHIVE_DIRECTORY)) {
         try (Stream<Path> archivePathStream = Files.list(ARCHIVE_DIRECTORY)) {
            List<Path> latestArchiveFiles = archivePathStream.filter(
                  archivePath -> archivePath.getFileName().toString().startsWith("greenmanserver-chat-")
                     && archivePath.getFileName().toString().endsWith(".log")
               )
               .filter(x$0 -> Files.isRegularFile(x$0))
               .sorted((firstPath, secondPath) -> Long.compare(getLastModifiedMillis(secondPath), getLastModifiedMillis(firstPath)))
               .limit(4L)
               .toList();

            for (int fileIndex = latestArchiveFiles.size() - 1; fileIndex >= 0; fileIndex--) {
               loadedEntries.addAll(readArchiveTail(latestArchiveFiles.get(fileIndex)));
            }
         } catch (RuntimeException | IOException var10) {
            GreenManServer.LOGGER.warn("最近聊天缓存预热失败，将使用本次运行产生的新消息", var10);
         }
      }

      synchronized (HISTORY_LOCK) {
         List<GreenManChatHistory.ChatHistoryEntry> liveEntries = List.copyOf(RECENT_MESSAGES);
         RECENT_MESSAGES.clear();

         for (GreenManChatHistory.ChatHistoryEntry loadedEntry : loadedEntries) {
            addEntryWhileLocked(loadedEntry);
         }

         for (GreenManChatHistory.ChatHistoryEntry liveEntry : liveEntries) {
            addEntryWhileLocked(liveEntry);
         }
      }

      WARMUP_COMPLETE.set(true);
      if (server != null) {
         server.execute(() -> {
            for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
               handlePlayerJoin(onlinePlayer);
            }
         });
      }
   }

   private static List<GreenManChatHistory.ChatHistoryEntry> readArchiveTail(Path archivePath) {
      List<GreenManChatHistory.ChatHistoryEntry> parsedEntries = new ArrayList<>();
      if (archivePath != null && Files.isRegularFile(archivePath)) {
         try {
            Object bytesToRead;
            try (RandomAccessFile archiveFile = new RandomAccessFile(archivePath.toFile(), "r")) {
               long fileLength = archiveFile.length();
               if (fileLength > 0L) {
                  int bytesToReadx = (int)Math.min(fileLength, 524288L);
                  byte[] tailBytes = new byte[bytesToReadx];
                  archiveFile.seek(fileLength - bytesToReadx);
                  archiveFile.readFully(tailBytes);
                  String tailText = new String(tailBytes, StandardCharsets.UTF_8);
                  String[] archiveLines = tailText.split("\\R");
                  int firstCompleteLineIndex = fileLength > bytesToReadx ? 1 : 0;

                  for (int lineIndex = firstCompleteLineIndex; lineIndex < archiveLines.length; lineIndex++) {
                     GreenManChatHistory.ChatHistoryEntry parsedEntry = parseArchiveLine(archiveLines[lineIndex]);
                     if (parsedEntry != null) {
                        parsedEntries.add(parsedEntry);
                     }
                  }

                  return parsedEntries;
               }

               bytesToRead = parsedEntries;
            }

            return (List<GreenManChatHistory.ChatHistoryEntry>)bytesToRead;
         } catch (RuntimeException | IOException var14) {
            GreenManServer.LOGGER.debug("聊天归档文件 {} 尾部读取失败", archivePath, var14);
            return parsedEntries;
         }
      } else {
         return parsedEntries;
      }
   }

   private static GreenManChatHistory.ChatHistoryEntry parseArchiveLine(String archiveLine) {
      if (archiveLine != null && !archiveLine.isBlank()) {
         int uuidStartIndex = archiveLine.indexOf(" [");
         int uuidEndIndex = uuidStartIndex < 0 ? -1 : archiveLine.indexOf("] ", uuidStartIndex + 2);
         int messageSeparatorIndex = uuidEndIndex < 0 ? -1 : archiveLine.indexOf(": ", uuidEndIndex + 2);
         if (uuidEndIndex >= 0 && messageSeparatorIndex >= 0) {
            String recordedTimeText = archiveLine.substring(0, Math.min(19, archiveLine.length()));
            String senderName = sanitizeText(archiveLine.substring(uuidEndIndex + 2, messageSeparatorIndex), 64);
            String messageContent = sanitizeText(archiveLine.substring(messageSeparatorIndex + 2), 1024);
            if (!senderName.isEmpty() && !messageContent.isEmpty()) {
               Instant recordedAt;
               try {
                  recordedAt = LocalDateTime.parse(recordedTimeText, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                     .atZone(ZoneId.of("Asia/Shanghai"))
                     .toInstant();
               } catch (RuntimeException var9) {
                  recordedAt = Instant.now();
               }

               return new GreenManChatHistory.ChatHistoryEntry(recordedAt, senderName, messageContent);
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static long getLastModifiedMillis(Path archivePath) {
      try {
         return Files.getLastModifiedTime(archivePath).toMillis();
      } catch (IOException var2) {
         return Long.MIN_VALUE;
      }
   }

   private static void addEntry(GreenManChatHistory.ChatHistoryEntry historyEntry) {
      if (historyEntry != null) {
         synchronized (HISTORY_LOCK) {
            addEntryWhileLocked(historyEntry);
         }
      }
   }

   private static void addEntryWhileLocked(GreenManChatHistory.ChatHistoryEntry historyEntry) {
      if (historyEntry != null) {
         int maximumCacheSize = GreenManServerConfig.getChatHistoryCacheSize();
         if (maximumCacheSize <= 0) {
            RECENT_MESSAGES.clear();
         } else {
            RECENT_MESSAGES.addLast(historyEntry);

            while (RECENT_MESSAGES.size() > maximumCacheSize) {
               RECENT_MESSAGES.pollFirst();
            }
         }
      }
   }

   private static List<GreenManChatHistory.ChatHistoryEntry> getSnapshot() {
      synchronized (HISTORY_LOCK) {
         return List.copyOf(RECENT_MESSAGES);
      }
   }

   private static Text formatHistoryComponent(GreenManChatHistory.ChatHistoryEntry historyEntry) {
      String displayTime = DISPLAY_TIME_FORMATTER.format(historyEntry.recordedAt());
      return Text.literal("[" + displayTime + "] ")
         .formatted(Formatting.DARK_GRAY)
         .append(Text.literal("<" + historyEntry.senderName() + "> ").formatted(Formatting.GRAY))
         .append(Text.literal(historyEntry.content()).formatted(Formatting.WHITE));
   }

   private static String sanitizeText(String rawText, int maximumCodePoints) {
      if (rawText != null && maximumCodePoints > 0) {
         StringBuilder sanitizedTextBuilder = new StringBuilder();
         rawText.codePoints()
            .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != 167)
            .limit(maximumCodePoints)
            .forEach(sanitizedTextBuilder::appendCodePoint);
         return sanitizedTextBuilder.toString().trim();
      } else {
         return "";
      }
   }

   private static void clearRuntimeState() {
      WARMUP_COMPLETE.set(false);
      clearHistory();
   }

   private record ChatHistoryEntry(Instant recordedAt, String senderName, String content) {
   }
}
