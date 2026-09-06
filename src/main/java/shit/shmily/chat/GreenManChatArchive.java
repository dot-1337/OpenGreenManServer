package shit.shmily.chat;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;

public final class GreenManChatArchive {
   private static final ZoneId ARCHIVE_ZONE_ID = ZoneId.of("Asia/Shanghai");
   private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ARCHIVE_ZONE_ID);
   private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
   private static final Path ARCHIVE_DIRECTORY = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("greenmanserver-chat");
   private static final int MAX_ARCHIVED_MESSAGE_CODE_POINTS = 1024;
   private static final int MAX_PENDING_TASKS = 2048;
   private static final int MAX_DAILY_FILE_SEGMENTS = 10000;
   private static final long ERROR_LOG_INTERVAL_MILLIS = 60000L;
   private static final AtomicLong LAST_ERROR_LOG_MILLIS = new AtomicLong(0L);
   private static volatile ThreadPoolExecutor writerExecutor;
   private static final AtomicBoolean shutdownRequested = new AtomicBoolean(true);
   private static final AtomicBoolean lifecycleRegistered = new AtomicBoolean(false);
   private static LocalDate lastRetentionCleanupDate;

   private GreenManChatArchive() {
   }

   public static void register() {
      if (!lifecycleRegistered.compareAndSet(false, true)) {
         startWriter();
      } else {
         ServerMessageEvents.CHAT_MESSAGE.register(GreenManChatArchive::archiveChatMessage);
         ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> startWriter());
         ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> shutdownWriter());
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> shutdownWriter());
         startWriter();
      }
   }

   public static String getArchiveDirectoryForDisplay() {
      return ARCHIVE_DIRECTORY.toAbsolutePath().normalize().toString();
   }

   public static int getPendingTaskCount() {
      ThreadPoolExecutor activeWriterExecutor = writerExecutor;
      return activeWriterExecutor == null ? 0 : activeWriterExecutor.getQueue().size();
   }

   public static boolean requestClearArchives() {
      return submitArchiveTask(GreenManChatArchive::clearArchiveFiles);
   }

   private static void archiveChatMessage(SignedMessage playerChatMessage, ServerPlayerEntity sender, Parameters chatType) {
      if (GreenManServerConfig.isChatArchiveEnabled() && playerChatMessage != null && sender != null) {
         String signedContent = playerChatMessage.getSignedContent();
         String sanitizedContent = sanitizeLogText(signedContent, 1024);
         if (!sanitizedContent.isEmpty()) {
            UUID senderUuid = sender.getUuid();
            String senderName = sanitizeLogText(sender.getGameProfile().name(), 64);
            String safeSenderName = senderName.isEmpty() ? senderUuid.toString() : senderName;
            GreenManChatArchive.ChatArchiveRecord archiveRecord = new GreenManChatArchive.ChatArchiveRecord(
               Instant.now(), senderUuid, safeSenderName, sanitizedContent
            );
            submitArchiveTask(() -> writeArchiveRecord(archiveRecord));
         }
      }
   }

   private static ThreadPoolExecutor createWriterExecutor() {
      return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2048), archiveTask -> {
         Thread archiveThread = new Thread(archiveTask, "GreenManServer-ChatArchive");
         archiveThread.setDaemon(true);
         return archiveThread;
      }, new AbortPolicy());
   }

   private static synchronized void startWriter() {
      shutdownRequested.set(false);
      ThreadPoolExecutor activeWriterExecutor = writerExecutor;
      if (activeWriterExecutor == null || activeWriterExecutor.isTerminated()) {
         writerExecutor = createWriterExecutor();
      }
   }

   private static boolean submitArchiveTask(Runnable archiveTask) {
      if (archiveTask == null) {
         return false;
      } else {
         ThreadPoolExecutor activeWriterExecutor = writerExecutor;
         if (!shutdownRequested.get() && activeWriterExecutor != null && !activeWriterExecutor.isShutdown()) {
            try {
               activeWriterExecutor.execute(archiveTask);
               return true;
            } catch (RuntimeException var3) {
               logArchiveFailure("聊天归档任务无法进入写入队列，本条记录未保存", var3);
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private static void writeArchiveRecord(GreenManChatArchive.ChatArchiveRecord archiveRecord) {
      if (archiveRecord != null && !shouldStopCurrentTask()) {
         try {
            Files.createDirectories(ARCHIVE_DIRECTORY);
            if (shouldStopCurrentTask()) {
               return;
            }

            cleanupExpiredArchivesIfNeeded(archiveRecord.recordedAt());
            if (shouldStopCurrentTask()) {
               return;
            }

            String archiveLine = formatArchiveLine(archiveRecord);
            long archiveLineBytes = archiveLine.getBytes(StandardCharsets.UTF_8).length;
            long maximumFileBytes = GreenManServerConfig.getChatArchiveMaxFileSizeMib() * 1048576L;
            Path archiveFilePath = selectArchiveFile(archiveRecord.recordedAt(), archiveLineBytes, maximumFileBytes);
            if (shouldStopCurrentTask()) {
               return;
            }

            Files.writeString(archiveFilePath, archiveLine, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (RuntimeException | IOException var7) {
            logArchiveFailure("聊天记录写入服务器本地失败", var7);
         }
      }
   }

   private static Path selectArchiveFile(Instant recordedAt, long archiveLineBytes, long maximumFileBytes) throws IOException {
      Instant safeRecordedAt = recordedAt == null ? Instant.now() : recordedAt;
      String fileDate = FILE_DATE_FORMATTER.format(safeRecordedAt.atZone(ARCHIVE_ZONE_ID));

      for (int segmentIndex = 1; segmentIndex <= 10000; segmentIndex++) {
         if (shouldStopCurrentTask()) {
            throw new IOException("聊天归档任务因服务器停止而取消");
         }

         String segmentSuffix = segmentIndex == 1 ? "" : "-" + segmentIndex;
         Path candidatePath = ARCHIVE_DIRECTORY.resolve("greenmanserver-chat-" + fileDate + segmentSuffix + ".log");
         if (Files.notExists(candidatePath)) {
            return candidatePath;
         }

         if (Files.isRegularFile(candidatePath)) {
            long existingFileBytes = Files.size(candidatePath);
            if (archiveLineBytes <= maximumFileBytes && existingFileBytes <= maximumFileBytes - archiveLineBytes) {
               return candidatePath;
            }
         }
      }

      throw new IOException("当天聊天归档滚动文件数量已达到安全上限");
   }

   private static void cleanupExpiredArchivesIfNeeded(Instant currentInstant) throws IOException {
      Instant safeCurrentInstant = currentInstant == null ? Instant.now() : currentInstant;
      LocalDate currentDate = safeCurrentInstant.atZone(ARCHIVE_ZONE_ID).toLocalDate();
      if (!currentDate.equals(lastRetentionCleanupDate)) {
         int retentionDays = GreenManServerConfig.getChatArchiveRetentionDays();
         Instant expirationCutoff = safeCurrentInstant.minus(Duration.ofDays(retentionDays));

         try (DirectoryStream<Path> archiveFiles = Files.newDirectoryStream(ARCHIVE_DIRECTORY, "greenmanserver-chat-*.log")) {
            for (Path archiveFile : archiveFiles) {
               if (shouldStopCurrentTask()) {
                  return;
               }

               if (Files.isRegularFile(archiveFile) && Files.getLastModifiedTime(archiveFile).toInstant().isBefore(expirationCutoff)) {
                  Files.deleteIfExists(archiveFile);
               }
            }
         }

         lastRetentionCleanupDate = currentDate;
      }
   }

   private static void clearArchiveFiles() {
      if (!Files.notExists(ARCHIVE_DIRECTORY) && !shouldStopCurrentTask()) {
         try (DirectoryStream<Path> archiveFiles = Files.newDirectoryStream(ARCHIVE_DIRECTORY, "greenmanserver-chat-*.log")) {
            for (Path archiveFile : archiveFiles) {
               if (shouldStopCurrentTask()) {
                  return;
               }

               if (Files.isRegularFile(archiveFile)) {
                  Files.deleteIfExists(archiveFile);
               }
            }

            lastRetentionCleanupDate = null;
         } catch (RuntimeException | IOException var5) {
            logArchiveFailure("清理聊天归档文件失败", var5);
         }
      }
   }

   private static String formatArchiveLine(GreenManChatArchive.ChatArchiveRecord archiveRecord) {
      String formattedTime = LOG_TIME_FORMATTER.format(archiveRecord.recordedAt());
      return formattedTime + " [" + archiveRecord.senderUuid() + "] " + archiveRecord.senderName() + ": " + archiveRecord.content() + System.lineSeparator();
   }

   private static String sanitizeLogText(String rawText, int maximumCodePoints) {
      if (rawText != null && !rawText.isEmpty() && maximumCodePoints > 0) {
         StringBuilder sanitizedTextBuilder = new StringBuilder(Math.min(rawText.length(), maximumCodePoints));
         rawText.codePoints()
            .limit(maximumCodePoints)
            .forEach(codePoint -> sanitizedTextBuilder.appendCodePoint(Character.isISOControl(codePoint) ? 32 : codePoint));
         return sanitizedTextBuilder.toString().trim();
      } else {
         return "";
      }
   }

   private static void logArchiveFailure(String failureMessage, Throwable failureCause) {
      if (!shutdownRequested.get() && !Thread.currentThread().isInterrupted()) {
         long currentTimeMillis = System.currentTimeMillis();
         long previousErrorLogMillis = LAST_ERROR_LOG_MILLIS.get();
         if (currentTimeMillis - previousErrorLogMillis >= 60000L) {
            if (LAST_ERROR_LOG_MILLIS.compareAndSet(previousErrorLogMillis, currentTimeMillis)) {
               GreenManServer.LOGGER.error(failureMessage, failureCause);
            }
         }
      }
   }

   private static boolean shouldStopCurrentTask() {
      return shutdownRequested.get() || Thread.currentThread().isInterrupted();
   }

   private static synchronized void shutdownWriter() {
      if (!shutdownRequested.compareAndSet(false, true)) {
         forceTerminateWriter(writerExecutor);
      } else {
         ThreadPoolExecutor activeWriterExecutor = writerExecutor;
         writerExecutor = null;
         forceTerminateWriter(activeWriterExecutor);
      }
   }

   private static void forceTerminateWriter(ThreadPoolExecutor activeWriterExecutor) {
      if (activeWriterExecutor != null) {
         activeWriterExecutor.shutdownNow();

         try {
            if (!activeWriterExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
               activeWriterExecutor.shutdownNow();
            }
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            activeWriterExecutor.shutdownNow();
         }
      }
   }

   private record ChatArchiveRecord(Instant recordedAt, UUID senderUuid, String senderName, String content) {
   }
}
