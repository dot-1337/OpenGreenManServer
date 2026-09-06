package shit.shmily.performance;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

public final class GreenManChunkIoManager {
   private static final AtomicBoolean LIFECYCLE_REGISTERED = new AtomicBoolean(false);
   private static final AtomicBoolean SHUTDOWN_REQUESTED = new AtomicBoolean(true);
   private static volatile ExecutorService chunkIoExecutor;
   private static final long SHUTDOWN_WAIT_SECONDS = 8L;

   private GreenManChunkIoManager() {
   }

   public static void register() {
      if (LIFECYCLE_REGISTERED.compareAndSet(false, true)) {
         ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> startExecutor());
         ServerLifecycleEvents.SERVER_STARTED.register(GreenManChunkIoManager::warmupChunkStorage);
         ServerLifecycleEvents.SERVER_STOPPING.register(GreenManChunkIoManager::prepareShutdown);
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> closeExecutor());
      }
   }

   private static synchronized void startExecutor() {
      SHUTDOWN_REQUESTED.set(false);
      ExecutorService activeExecutor = chunkIoExecutor;
      if (activeExecutor == null || activeExecutor.isShutdown() || activeExecutor.isTerminated()) {
         chunkIoExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread ioThread = new Thread(task, "GreenManServer-ChunkIO");
            ioThread.setDaemon(true);
            ioThread.setPriority(1);
            return ioThread;
         });
      }
   }

   private static void warmupChunkStorage(MinecraftServer server) {
      if (server != null && !SHUTDOWN_REQUESTED.get() && GreenManServerConfig.isFeatureEnabled("asyncChunkIo")) {
         ExecutorService activeExecutor = chunkIoExecutor;
         if (activeExecutor != null && !activeExecutor.isShutdown()) {
            try {
               activeExecutor.submit(() -> synchronizeLoadedDimensions(server, false));
            } catch (RejectedExecutionException var3) {
               GreenManServer.LOGGER.debug("区块启动 IO 预热任务未提交，继续使用原版流程", var3);
            }
         }
      }
   }

   private static void synchronizeLoadedDimensions(MinecraftServer server, boolean waitForWrites) {
      if (server != null) {
         for (ServerWorld serverLevel : server.getWorlds()) {
            if (serverLevel != null) {
               try {
                  serverLevel.getChunkManager().chunkLoadingManager.completeAll(waitForWrites).join();
               } catch (RuntimeException var5) {
                  GreenManServer.LOGGER.warn("维度 {} 的区块 IO 同步失败，继续使用原版保存流程", serverLevel.getRegistryKey().getValue(), var5);
               }
            }
         }
      }
   }

   private static void prepareShutdown(MinecraftServer server) {
      SHUTDOWN_REQUESTED.set(true);
      if (server != null && GreenManServerConfig.isFeatureEnabled("asyncChunkIo")) {
         for (ServerWorld serverLevel : server.getWorlds()) {
            if (serverLevel != null) {
               try {
                  serverLevel.save(null, false, false);
               } catch (RuntimeException var6) {
                  GreenManServer.LOGGER.warn("维度 {} 的关服提前保存失败，将由原版最终保存重试", serverLevel.getRegistryKey().getValue(), var6);
               }
            }
         }

         ExecutorService activeExecutor = chunkIoExecutor;
         if (activeExecutor != null) {
            Future<?> synchronizationFuture;
            try {
               synchronizationFuture = activeExecutor.submit(() -> synchronizeLoadedDimensions(server, true));
            } catch (RejectedExecutionException var5) {
               GreenManServer.LOGGER.debug("区块关服 IO 整理任务未提交，继续使用原版保存流程", var5);
               return;
            }

            try {
               synchronizationFuture.get(8L, TimeUnit.SECONDS);
            } catch (Exception var4) {
               synchronizationFuture.cancel(true);
               GreenManServer.LOGGER.warn("区块关服 IO 整理未在限定时间内完成，继续使用原版保存流程", var4);
            }
         }
      } else {
         closeExecutor();
      }
   }

   private static synchronized void closeExecutor() {
      SHUTDOWN_REQUESTED.set(true);
      ExecutorService activeExecutor = chunkIoExecutor;
      chunkIoExecutor = null;
      if (activeExecutor != null) {
         activeExecutor.shutdown();

         try {
            if (!activeExecutor.awaitTermination(8L, TimeUnit.SECONDS)) {
               activeExecutor.shutdownNow();
            }
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            activeExecutor.shutdownNow();
         }
      }
   }
}
