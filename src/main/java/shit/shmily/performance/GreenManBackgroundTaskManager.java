package shit.shmily.performance;

import shit.shmily.GreenManServer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;

public final class GreenManBackgroundTaskManager {
   private static final int MAXIMUM_PENDING_TASKS = 64;
   private static final long THREAD_KEEP_ALIVE_SECONDS = 10L;
   private static final long SHUTDOWN_WAIT_SECONDS = 3L;
   private static final AtomicBoolean LIFECYCLE_REGISTERED = new AtomicBoolean(false);
   private static volatile ThreadPoolExecutor backgroundExecutor;
   private static final AtomicBoolean SHUTDOWN_REQUESTED = new AtomicBoolean(true);

   private GreenManBackgroundTaskManager() {
   }

   public static void register() {
      if (LIFECYCLE_REGISTERED.compareAndSet(false, true)) {
         ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> startExecutor());
         ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> shutdownExecutor());
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> shutdownExecutor());
      }
   }

   public static boolean submit(String taskName, Runnable backgroundTask) {
      if (backgroundTask == null) {
         return false;
      } else {
         ThreadPoolExecutor activeExecutor = backgroundExecutor;
         if (!SHUTDOWN_REQUESTED.get() && activeExecutor != null && !activeExecutor.isShutdown()) {
            String safeTaskName = taskName != null && !taskName.isBlank() ? taskName : "未命名后台任务";

            try {
               activeExecutor.execute(() -> runSafely(safeTaskName, backgroundTask));
               return true;
            } catch (RejectedExecutionException var5) {
               GreenManServer.LOGGER.warn("后台任务 {} 未能进入稳定线程队列", safeTaskName, var5);
               return false;
            }
         } else {
            return false;
         }
      }
   }

   public static int getPendingTaskCount() {
      ThreadPoolExecutor activeExecutor = backgroundExecutor;
      return activeExecutor == null ? 0 : activeExecutor.getQueue().size();
   }

   private static ThreadPoolExecutor createExecutor() {
      return new ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), backgroundTask -> {
         Thread backgroundThread = new Thread(backgroundTask, "GreenManServer-BackgroundResource");
         backgroundThread.setDaemon(true);
         backgroundThread.setPriority(1);
         return backgroundThread;
      }, new AbortPolicy());
   }

   private static synchronized void startExecutor() {
      SHUTDOWN_REQUESTED.set(false);
      ThreadPoolExecutor activeExecutor = backgroundExecutor;
      if (activeExecutor == null || activeExecutor.isShutdown() || activeExecutor.isTerminated()) {
         backgroundExecutor = createExecutor();
      }
   }

   private static void runSafely(String taskName, Runnable backgroundTask) {
      if (!SHUTDOWN_REQUESTED.get() && !Thread.currentThread().isInterrupted()) {
         try {
            backgroundTask.run();
         } catch (RuntimeException var3) {
            GreenManServer.LOGGER.error("后台任务 {} 执行失败", taskName, var3);
         }
      }
   }

   private static synchronized void shutdownExecutor() {
      SHUTDOWN_REQUESTED.set(true);
      ThreadPoolExecutor activeExecutor = backgroundExecutor;
      backgroundExecutor = null;
      if (activeExecutor != null) {
         activeExecutor.shutdown();

         try {
            if (!activeExecutor.awaitTermination(3L, TimeUnit.SECONDS)) {
               activeExecutor.shutdownNow();
            }
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
            activeExecutor.shutdownNow();
         }
      }
   }
}
