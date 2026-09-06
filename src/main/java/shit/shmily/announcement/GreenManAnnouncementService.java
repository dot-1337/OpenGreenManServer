package shit.shmily.announcement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.performance.GreenManBackgroundTaskManager;
import shit.shmily.text.GreenManTextFormatter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManAnnouncementService {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path STATE_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver-announcement-state.json");
   private static final Path TEMP_STATE_PATH = STATE_PATH.resolveSibling(STATE_PATH.getFileName() + ".tmp");
   private static final Map<UUID, Long> SEEN_VERSIONS = new ConcurrentHashMap<>();
   private static final AtomicBoolean STATE_LOADED = new AtomicBoolean(false);
   private static final AtomicBoolean STATE_DIRTY = new AtomicBoolean(false);
   private static final AtomicBoolean SAVE_QUEUED = new AtomicBoolean(false);
   private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

   private GreenManAnnouncementService() {
   }

   public static void register() {
      if (REGISTERED.compareAndSet(false, true)) {
         ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> startLoading(server));
         ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> flushStateSynchronously());
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> resetRuntimeState());
      }
   }

   public static void handlePlayerJoin(ServerPlayerEntity joinedPlayer) {
      if (joinedPlayer != null && GreenManServerConfig.isAnnouncementEnabled() && !GreenManServerConfig.getAnnouncementText().isEmpty()) {
         if (GreenManServerConfig.isAnnouncementShowEveryJoin()) {
            sendAnnouncement(joinedPlayer);
         } else if (STATE_LOADED.get()) {
            long announcementVersion = GreenManServerConfig.getAnnouncementVersion();
            if (announcementVersion > 0L) {
               long seenVersion = SEEN_VERSIONS.getOrDefault(joinedPlayer.getUuid(), 0L);
               if (seenVersion < announcementVersion) {
                  sendAnnouncement(joinedPlayer);
                  SEEN_VERSIONS.put(joinedPlayer.getUuid(), announcementVersion);
                  STATE_DIRTY.set(true);
                  queueStateSave();
               }
            }
         }
      }
   }

   private static void sendAnnouncement(ServerPlayerEntity targetPlayer) {
      if (targetPlayer != null) {
         Text announcementTitle = GreenManTextFormatter.parseRainbow(GreenManServerConfig.getAnnouncementTitle(), Style.EMPTY);
         if (announcementTitle.getString().isEmpty()) {
            announcementTitle = Text.literal("[服务器公告]").formatted(new Formatting[]{Formatting.GOLD, Formatting.BOLD});
         }

         targetPlayer.sendMessage(announcementTitle);
         targetPlayer.sendMessage(
            GreenManTextFormatter.parseRainbow(GreenManServerConfig.getAnnouncementText(), Style.EMPTY.withColor(Formatting.YELLOW))
         );
      }
   }

   public static boolean resetSeenPlayers() {
      SEEN_VERSIONS.clear();
      STATE_DIRTY.set(true);
      return queueStateSave();
   }

   public static int getSeenPlayerCount() {
      return SEEN_VERSIONS.size();
   }

   private static void startLoading(MinecraftServer server) {
      STATE_LOADED.set(false);
      if (GreenManServerConfig.isAnnouncementShowEveryJoin()) {
         SEEN_VERSIONS.clear();
         STATE_LOADED.set(true);
      } else {
         boolean taskAccepted = GreenManBackgroundTaskManager.submit("加载公告已读状态", () -> loadState(server));
         if (!taskAccepted) {
            STATE_LOADED.set(true);
         }
      }
   }

   private static void loadState(MinecraftServer server) {
      GreenManAnnouncementService.AnnouncementState loadedState = new GreenManAnnouncementService.AnnouncementState();
      if (Files.exists(STATE_PATH)) {
         try (Reader stateReader = Files.newBufferedReader(STATE_PATH, StandardCharsets.UTF_8)) {
            GreenManAnnouncementService.AnnouncementState parsedState = (GreenManAnnouncementService.AnnouncementState)GSON.fromJson(
               stateReader, GreenManAnnouncementService.AnnouncementState.class
            );
            if (parsedState != null && parsedState.seenVersions != null) {
               loadedState = parsedState;
            }
         } catch (RuntimeException | IOException var9) {
            GreenManServer.LOGGER.warn("公告已读状态加载失败，将使用空状态", var9);
         }
      }

      Map<UUID, Long> sanitizedSeenVersions = new HashMap<>();

      for (Entry<String, Long> seenEntry : loadedState.seenVersions.entrySet()) {
         if (seenEntry.getKey() != null && seenEntry.getValue() != null && seenEntry.getValue() >= 0L) {
            try {
               sanitizedSeenVersions.put(UUID.fromString(seenEntry.getKey()), seenEntry.getValue());
            } catch (IllegalArgumentException var6) {
            }
         }
      }

      if (!STATE_DIRTY.get()) {
         SEEN_VERSIONS.clear();
         SEEN_VERSIONS.putAll(sanitizedSeenVersions);
      }

      STATE_LOADED.set(true);
      if (server != null && !GreenManServerConfig.isAnnouncementShowEveryJoin()) {
         server.execute(() -> {
            for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
               handlePlayerJoin(onlinePlayer);
            }
         });
      }
   }

   private static boolean queueStateSave() {
      if (!SAVE_QUEUED.compareAndSet(false, true)) {
         return true;
      } else {
         boolean taskAccepted = GreenManBackgroundTaskManager.submit("保存公告已读状态", GreenManAnnouncementService::saveQueuedState);
         if (!taskAccepted) {
            SAVE_QUEUED.set(false);
         }

         return taskAccepted;
      }
   }

   private static void saveQueuedState() {
      boolean shouldSave = STATE_DIRTY.getAndSet(false);
      if (shouldSave) {
         saveStateSnapshot();
      }

      SAVE_QUEUED.set(false);
      if (STATE_DIRTY.get()) {
         queueStateSave();
      }
   }

   private static void saveStateSnapshot() {
      GreenManAnnouncementService.AnnouncementState stateSnapshot = new GreenManAnnouncementService.AnnouncementState();

      for (Entry<UUID, Long> seenEntry : SEEN_VERSIONS.entrySet()) {
         if (seenEntry.getKey() != null && seenEntry.getValue() != null) {
            stateSnapshot.seenVersions.put(seenEntry.getKey().toString(), seenEntry.getValue());
         }
      }

      try {
         Files.createDirectories(STATE_PATH.getParent());

         try (Writer stateWriter = Files.newBufferedWriter(TEMP_STATE_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(stateSnapshot, stateWriter);
         }

         try {
            Files.move(TEMP_STATE_PATH, STATE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException var5) {
            Files.move(TEMP_STATE_PATH, STATE_PATH, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException var7) {
         STATE_DIRTY.set(true);
         GreenManServer.LOGGER.error("公告已读状态保存失败", var7);
      }
   }

   private static void flushStateSynchronously() {
      if (STATE_DIRTY.getAndSet(false)) {
         saveStateSnapshot();
      }
   }

   private static void resetRuntimeState() {
      STATE_LOADED.set(false);
      SAVE_QUEUED.set(false);
      SEEN_VERSIONS.clear();
   }

   private static final class AnnouncementState {
      private Map<String, Long> seenVersions = new LinkedHashMap<>();
   }
}
