package shit.shmily.music;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.join.GreenManJoinSoundScheduler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GreenManMusicService {
   private static final int RESOURCE_PACK_SOUND_DELAY_TICKS = 10;
   private static final Path MUSIC_DIRECTORY = FabricLoader.getInstance().getGameDir().resolve("GreenManMusic");
   private static final Map<String, GreenManMusicService.GeneratedMusicPack> PACK_CACHE = new ConcurrentHashMap<>();
   private static final Map<UUID, GreenManMusicService.PendingPlayback> PENDING_PLAYBACKS = new ConcurrentHashMap<>();
   private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
   private static volatile HttpServer resourcePackHttpServer;
   private static volatile ExecutorService resourcePackHttpExecutor;

   private GreenManMusicService() {
   }

   public static void initialize() {
      if (REGISTERED.compareAndSet(false, true)) {
         ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> stopHttpServer());
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> resetRuntimeState());
      }

      try {
         Files.createDirectories(MUSIC_DIRECTORY);
      } catch (IOException var1) {
         GreenManServer.LOGGER.warn("无法创建 GreenManMusic 音频目录，内部进服音效将不可用", var1);
      }
   }

   public static void handlePlayerJoin(ServerPlayerEntity joinedPlayer) {
      if (joinedPlayer != null && joinedPlayer.getEntityWorld().getServer() != null) {
         List<ServerPlayerEntity> recipients = GreenManServerConfig.isJoinSoundBroadcastEnabled()
            ? joinedPlayer.getEntityWorld().getServer().getPlayerManager().getPlayerList()
            : List.of(joinedPlayer);
         playInternalAudio(recipients, GreenManServerConfig.getJoinInternalMusicFile());
      }
   }

   public static boolean playVanillaSounds(Collection<ServerPlayerEntity> recipients, Collection<String> configuredSoundIds, String logContext) {
      if (recipients != null && !recipients.isEmpty() && configuredSoundIds != null && !configuredSoundIds.isEmpty()) {
         boolean sentAnySound = false;

         for (ServerPlayerEntity soundRecipient : recipients) {
            if (soundRecipient != null && soundRecipient.networkHandler != null) {
               for (String configuredSoundId : configuredSoundIds) {
                  if (configuredSoundId != null && !configuredSoundId.isBlank()) {
                     Identifier soundIdentifier = Identifier.tryParse(configuredSoundId.trim());
                     if (soundIdentifier == null) {
                        GreenManServer.LOGGER.warn("{}原版音效ID格式无效：{}", logContext == null ? "服务器" : logContext, configuredSoundId);
                     } else {
                        Optional<Reference<SoundEvent>> soundHolder = Registries.SOUND_EVENT.getEntry(soundIdentifier);
                        if (soundHolder.isEmpty()) {
                           GreenManServer.LOGGER.warn("{}原版音效不存在：{}", logContext == null ? "服务器" : logContext, soundIdentifier);
                        } else {
                           PlaySoundS2CPacket soundPacket = new PlaySoundS2CPacket(
                              (RegistryEntry)soundHolder.get(),
                              SoundCategory.MASTER,
                              soundRecipient.getX(),
                              soundRecipient.getY(),
                              soundRecipient.getZ(),
                              1.0F,
                              1.0F,
                              soundRecipient.getRandom().nextLong()
                           );
                           soundRecipient.networkHandler.sendPacket(soundPacket);
                           sentAnySound = true;
                        }
                     }
                  }
               }
            }
         }

         return sentAnySound;
      } else {
         GreenManServer.LOGGER.warn("{}原版音效播放失败：接收者或声音列表为空", logContext == null ? "服务器" : logContext);
         return false;
      }
   }

   public static boolean playInternalAudio(Collection<ServerPlayerEntity> recipients, String configuredFileName) {
      if (recipients == null || recipients.isEmpty() || configuredFileName == null || configuredFileName.isBlank()) {
         GreenManServer.LOGGER.warn("内部音频播放失败：接收者为空或文件名为空");
         return false;
      } else if (GreenManServerConfig.getResolvedJoinMusicResourcePackUrl().isEmpty()) {
         GreenManServer.LOGGER.warn("内部音频播放失败：未配置可访问的资源包地址，请填写joinMusicResourcePackUrl或joinMusicPublicHost");
         return false;
      } else {
         initialize();
         String safeFileName = configuredFileName.trim();
         if (!safeFileName.contains("/") && !safeFileName.contains("\\") && !safeFileName.contains("..")) {
            Path sourceAudioPath = MUSIC_DIRECTORY.resolve(safeFileName).normalize();
            if (sourceAudioPath.startsWith(MUSIC_DIRECTORY.normalize())
               && !Files.notExists(sourceAudioPath)
               && Files.isRegularFile(sourceAudioPath)
               && Files.isReadable(sourceAudioPath)) {
               Path compatibleAudioPath = resolveCompatibleAudioPath(sourceAudioPath);
               if (compatibleAudioPath == null) {
                  return false;
               } else {
                  long sourceModifiedTime = readLastModifiedTime(compatibleAudioPath);
                  String cacheKey = Integer.toUnsignedString(safeFileName.toLowerCase(Locale.ROOT).hashCode());
                  GreenManMusicService.GeneratedMusicPack musicPack = PACK_CACHE.get(cacheKey);
                  if (musicPack == null || !compatibleAudioPath.equals(musicPack.sourcePath()) || sourceModifiedTime != musicPack.sourceModifiedTime()) {
                     synchronized (PACK_CACHE) {
                        musicPack = PACK_CACHE.get(cacheKey);
                        if (musicPack == null || !compatibleAudioPath.equals(musicPack.sourcePath()) || sourceModifiedTime != musicPack.sourceModifiedTime()) {
                           musicPack = buildResourcePack(compatibleAudioPath, safeFileName, cacheKey);
                           if (musicPack == null) {
                              return false;
                           }

                           PACK_CACHE.put(cacheKey, musicPack);
                        }
                     }
                  }

                  if (!startHttpServerIfNeeded()) {
                     return false;
                  } else {
                     for (ServerPlayerEntity recipient : recipients) {
                        if (recipient != null && recipient.networkHandler != null) {
                           UUID resourcePackId = UUID.randomUUID();
                           PENDING_PLAYBACKS.put(resourcePackId, new GreenManMusicService.PendingPlayback(recipient, musicPack.soundIdentifier()));
                           ResourcePackSendS2CPacket resourcePackPacket = new ResourcePackSendS2CPacket(
                              resourcePackId,
                              buildResourcePackUrl(musicPack),
                              musicPack.sha1(),
                              GreenManServerConfig.isJoinMusicResourcePackRequired(),
                              Optional.of(Text.literal("GreenManServer 内部音效资源包"))
                           );
                           recipient.networkHandler.sendPacket(resourcePackPacket);
                        }
                     }

                     return true;
                  }
               }
            } else {
               GreenManServer.LOGGER.warn("内部音频播放失败：文件不存在、不是普通文件或无读取权限：{}", safeFileName);
               return false;
            }
         } else {
            GreenManServer.LOGGER.warn("内部音频播放失败：文件名包含非法路径：{}", configuredFileName);
            return false;
         }
      }
   }

   private static synchronized boolean startHttpServerIfNeeded() {
      if (resourcePackHttpServer != null) {
         return true;
      } else {
         try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(GreenManServerConfig.getJoinMusicHttpPort()), 8);
            httpServer.createContext("/greenmanserver-music.zip", GreenManMusicService::handleResourcePackDownload);
            resourcePackHttpExecutor = Executors.newSingleThreadExecutor(task -> {
               Thread httpThread = new Thread(task, "GreenManServer-MusicHttp");
               httpThread.setPriority(1);
               return httpThread;
            });
            httpServer.setExecutor(resourcePackHttpExecutor);
            httpServer.start();
            resourcePackHttpServer = httpServer;
            GreenManServer.LOGGER.info("GreenManMusic资源包下载服务已启动，端口{}，支持按音频查询参数下载", GreenManServerConfig.getJoinMusicHttpPort());
            return true;
         } catch (RuntimeException | IOException var1) {
            GreenManServer.LOGGER.warn("GreenManMusic资源包HTTP服务启动失败，请检查端口{}", GreenManServerConfig.getJoinMusicHttpPort(), var1);
            stopHttpServer();
            return false;
         }
      }
   }

   private static void handleResourcePackDownload(HttpExchange httpExchange) throws IOException {
      if (httpExchange != null) {
         HttpExchange safeExchange = httpExchange;

         label89: {
            label90: {
               try {
                  if (!"GET".equalsIgnoreCase(safeExchange.getRequestMethod())) {
                     safeExchange.sendResponseHeaders(404, -1L);
                     break label89;
                  }

                  String cacheKey = readPackQueryKey(safeExchange.getRequestURI().getRawQuery());
                  GreenManMusicService.GeneratedMusicPack requestedPack = cacheKey.isEmpty() ? getFirstCachedPack() : PACK_CACHE.get(cacheKey);
                  if (requestedPack != null && !Files.notExists(requestedPack.packPath()) && Files.isReadable(requestedPack.packPath())) {
                     long packFileSize = Files.size(requestedPack.packPath());
                     safeExchange.getResponseHeaders().set("Content-Type", "application/zip");
                     safeExchange.getResponseHeaders().set("Cache-Control", "no-cache");
                     safeExchange.sendResponseHeaders(200, packFileSize);

                     try (OutputStream responseBody = safeExchange.getResponseBody()) {
                        Files.copy(requestedPack.packPath(), responseBody);
                        break label90;
                     }
                  }

                  safeExchange.sendResponseHeaders(404, -1L);
               } catch (Throwable var12) {
                  if (httpExchange != null) {
                     try {
                        safeExchange.close();
                     } catch (Throwable var9) {
                        var12.addSuppressed(var9);
                     }
                  }

                  throw var12;
               }

               if (httpExchange != null) {
                  httpExchange.close();
               }

               return;
            }

            if (httpExchange != null) {
               httpExchange.close();
            }

            return;
         }

         if (httpExchange != null) {
            httpExchange.close();
         }
      }
   }

   private static String readPackQueryKey(String rawQuery) {
      if (rawQuery != null && !rawQuery.isBlank()) {
         for (String queryPart : rawQuery.split("&")) {
            if (queryPart != null && !queryPart.isBlank() && queryPart.startsWith("pack=")) {
               return queryPart.substring("pack=".length()).replaceAll("[^A-Za-z0-9_-]", "");
            }
         }

         return "";
      } else {
         return "";
      }
   }

   private static GreenManMusicService.GeneratedMusicPack getFirstCachedPack() {
      return PACK_CACHE.isEmpty() ? null : PACK_CACHE.values().stream().findFirst().orElse(null);
   }

   private static String buildResourcePackUrl(GreenManMusicService.GeneratedMusicPack musicPack) {
      String configuredUrl = GreenManServerConfig.getResolvedJoinMusicResourcePackUrl();
      if (musicPack != null && configuredUrl != null && !configuredUrl.isBlank()) {
         String separator = configuredUrl.contains("?") ? "&" : "?";
         return configuredUrl + separator + "pack=" + musicPack.cacheKey();
      } else {
         return "";
      }
   }

   private static synchronized void stopHttpServer() {
      if (resourcePackHttpServer != null) {
         resourcePackHttpServer.stop(0);
         resourcePackHttpServer = null;
      }

      if (resourcePackHttpExecutor != null) {
         resourcePackHttpExecutor.shutdownNow();
         resourcePackHttpExecutor = null;
      }
   }

   private static void resetRuntimeState() {
      stopHttpServer();
      PENDING_PLAYBACKS.clear();
      PACK_CACHE.clear();
   }

   public static void handleResourcePackResponse(UUID resourcePackId, boolean loadedSuccessfully) {
      if (resourcePackId != null) {
         GreenManMusicService.PendingPlayback pendingPlayback = PENDING_PLAYBACKS.remove(resourcePackId);
         if (pendingPlayback != null) {
            ServerPlayerEntity pendingPlayer = pendingPlayback.player();
            if (loadedSuccessfully && pendingPlayer.networkHandler != null && pendingPlayer.getEntityWorld().getServer() != null) {
               GreenManJoinSoundScheduler.schedule(
                  pendingPlayer.getEntityWorld().getServer(), 10, () -> playLoadedInternalSound(pendingPlayer, pendingPlayback.soundIdentifier())
               );
            } else {
               GreenManServer.LOGGER.warn("内部进服音效未播放：玩家 {} 未成功加载资源包或已经离线", pendingPlayer.getName().getString());
            }
         }
      }
   }

   private static void playLoadedInternalSound(ServerPlayerEntity pendingPlayer, Identifier soundIdentifier) {
      if (pendingPlayer != null && pendingPlayer.networkHandler != null) {
         if (soundIdentifier != null) {
            PlaySoundS2CPacket soundPacket = new PlaySoundS2CPacket(
               RegistryEntry.of(SoundEvent.of(soundIdentifier)),
               SoundCategory.MASTER,
               pendingPlayer.getX(),
               pendingPlayer.getY(),
               pendingPlayer.getZ(),
               1.0F,
               1.0F,
               pendingPlayer.getRandom().nextLong()
            );
            pendingPlayer.networkHandler.sendPacket(soundPacket);
         }
      }
   }

   private static GreenManMusicService.GeneratedMusicPack buildResourcePack(Path sourceAudioPath, String configuredFileName, String cacheKey) {
      if (sourceAudioPath != null && !Files.notExists(sourceAudioPath) && Files.isReadable(sourceAudioPath)) {
         String safeKey = Integer.toHexString(configuredFileName.hashCode()) + "-" + Long.toUnsignedString(System.nanoTime());
         Path generatedPackPath = MUSIC_DIRECTORY.resolve("greenmanserver-music-" + safeKey + ".zip");
         Path temporaryPackPath = generatedPackPath.resolveSibling(generatedPackPath.getFileName() + ".tmp");
         Identifier soundIdentifier = Identifier.of("greenmanserver", "music." + safeKey.replace('-', '_'));

         try {
            Files.createDirectories(MUSIC_DIRECTORY);

            try (
               OutputStream fileOutputStream = Files.newOutputStream(temporaryPackPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
               ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream, StandardCharsets.UTF_8);
            ) {
               writeZipText(zipOutputStream, "pack.mcmeta", "{\"pack\":{\"pack_format\":75,\"description\":\"GreenManServer 内部进服音效\"}}");
               writeZipText(
                  zipOutputStream,
                  "assets/greenmanserver/sounds.json",
                  "{\""
                     + soundIdentifier.getPath()
                     + "\":{\"sounds\":[{\"name\":\""
                     + soundIdentifier.getNamespace()
                     + ":music/"
                     + safeKey
                     + "\",\"stream\":true}]}}"
               );
               ZipEntry audioEntry = new ZipEntry("assets/greenmanserver/sounds/music/" + safeKey + ".ogg");
               zipOutputStream.putNextEntry(audioEntry);
               Files.copy(sourceAudioPath, zipOutputStream);
               zipOutputStream.closeEntry();
            }

            Files.move(temporaryPackPath, generatedPackPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            String generatedPackSha1 = calculateSha1(generatedPackPath);
            return generatedPackSha1.isEmpty()
               ? null
               : new GreenManMusicService.GeneratedMusicPack(
                  sourceAudioPath, readLastModifiedTime(sourceAudioPath), generatedPackPath, generatedPackSha1, soundIdentifier, cacheKey
               );
         } catch (RuntimeException | IOException var15) {
            GreenManServer.LOGGER.warn("生成 GreenManMusic 资源包失败，内部音效已跳过", var15);
            return null;
         }
      } else {
         GreenManServer.LOGGER.warn("无法读取内部音频文件，资源包生成已跳过：{}", sourceAudioPath);
         return null;
      }
   }

   private static Path resolveCompatibleAudioPath(Path sourceAudioPath) {
      if (sourceAudioPath != null && !Files.notExists(sourceAudioPath)) {
         String lowerCaseFileName = sourceAudioPath.getFileName().toString().toLowerCase(Locale.ROOT);
         if (lowerCaseFileName.endsWith(".ogg")) {
            return sourceAudioPath;
         } else if (!lowerCaseFileName.endsWith(".mp3")
            && !lowerCaseFileName.endsWith(".wav")
            && !lowerCaseFileName.endsWith(".flac")
            && !lowerCaseFileName.endsWith(".m4a")
            && !lowerCaseFileName.endsWith(".aac")) {
            GreenManServer.LOGGER.warn("GreenManMusic 不支持该音频格式：{}", sourceAudioPath.getFileName());
            return null;
         } else {
            String conversionCacheKey = Integer.toHexString(sourceAudioPath.getFileName().toString().toLowerCase(Locale.ROOT).hashCode());
            Path convertedAudioPath = MUSIC_DIRECTORY.resolve("converted-" + conversionCacheKey + ".ogg");
            if (Files.exists(convertedAudioPath) && readLastModifiedTime(convertedAudioPath) >= readLastModifiedTime(sourceAudioPath)) {
               return convertedAudioPath;
            } else {
               ProcessBuilder conversionProcessBuilder = new ProcessBuilder(
                  "ffmpeg", "-y", "-i", sourceAudioPath.toString(), "-vn", "-c:a", "libvorbis", convertedAudioPath.toString()
               );
               conversionProcessBuilder.redirectErrorStream(true);
               conversionProcessBuilder.redirectOutput(Redirect.DISCARD);

               try {
                  Process conversionProcess = conversionProcessBuilder.start();
                  boolean conversionFinished = conversionProcess.waitFor(30L, TimeUnit.SECONDS);
                  if (conversionFinished && conversionProcess.exitValue() == 0 && !Files.notExists(convertedAudioPath)) {
                     return convertedAudioPath;
                  } else {
                     if (!conversionFinished) {
                        conversionProcess.destroyForcibly();
                     }

                     GreenManServer.LOGGER.warn("FFmpeg 转换内部进服音频失败：{}", sourceAudioPath.getFileName());
                     return null;
                  }
               } catch (IOException var7) {
                  GreenManServer.LOGGER.warn("未找到可用 FFmpeg，无法把 {} 转换为 OGG；请安装 FFmpeg 或直接提供 OGG 文件", sourceAudioPath.getFileName());
                  return null;
               } catch (InterruptedException var8) {
                  Thread.currentThread().interrupt();
                  GreenManServer.LOGGER.warn("服务器关闭或线程中断，内部音频转换已取消");
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   private static long readLastModifiedTime(Path filePath) {
      try {
         return filePath == null ? -1L : Files.getLastModifiedTime(filePath).toMillis();
      } catch (IOException var2) {
         return -1L;
      }
   }

   private static void writeZipText(ZipOutputStream zipOutputStream, String entryName, String text) throws IOException {
      zipOutputStream.putNextEntry(new ZipEntry(entryName));
      zipOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
      zipOutputStream.closeEntry();
   }

   private static String calculateSha1(Path filePath) throws IOException {
      try {
         MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");

         try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] readBuffer = new byte[8192];

            int readLength;
            while ((readLength = inputStream.read(readBuffer)) >= 0) {
               if (readLength != 0) {
                  messageDigest.update(readBuffer, 0, readLength);
               }
            }
         }

         return HexFormat.of().formatHex(messageDigest.digest());
      } catch (NoSuchAlgorithmException var7) {
         GreenManServer.LOGGER.error("JVM缺少SHA-1算法，无法校验内部音乐资源包", var7);
         return "";
      }
   }

   private record GeneratedMusicPack(Path sourcePath, long sourceModifiedTime, Path packPath, String sha1, Identifier soundIdentifier, String cacheKey) {
   }

   private record PendingPlayback(ServerPlayerEntity player, Identifier soundIdentifier) {
   }
}
