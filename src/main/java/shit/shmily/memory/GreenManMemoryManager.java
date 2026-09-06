package shit.shmily.memory;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;

public final class GreenManMemoryManager {
   private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
   private static final long MAX_CHECK_INTERVAL_NANOS = 30000000000L;
   private static final long MIN_CHECK_INTERVAL_NANOS = 500000000L;
   private static final long COLLECTION_LOG_INTERVAL_NANOS = 60000000000L;
   private static final double MAX_SAFE_MILLISECONDS_PER_TICK = 40.0;
   private static long lastCheckNanos;
   private static long lastGcRequestNanos;
   private static long lastCollectionLogNanos;
   private static int consecutiveHighPressureSamples;

   private GreenManMemoryManager() {
   }

   public static void tick(MinecraftServer server) {
      if (server != null && GreenManServerConfig.isMemoryOptimizationEnabled()) {
         long currentTimeNanos = System.nanoTime();
         long configuredCooldownNanos = GreenManServerConfig.getMemoryGcCooldownSeconds() * 1000000000L;
         long checkIntervalNanos = Math.min(30000000000L, Math.max(500000000L, configuredCooldownNanos / 2L));
         if (hasElapsedSince(lastCheckNanos, currentTimeNanos, checkIntervalNanos)) {
            lastCheckNanos = currentTimeNanos;
            GreenManMemoryManager.MemorySnapshot snapshot = getSnapshot();
            if (snapshot.maximumBytes > 0L && !(snapshot.maximumUsagePercent < GreenManServerConfig.getMemoryPressureThresholdPercent())) {
               consecutiveHighPressureSamples++;
               if (consecutiveHighPressureSamples >= 2) {
                  if (hasElapsedSince(lastGcRequestNanos, currentTimeNanos, configuredCooldownNanos)) {
                     double millisecondsPerTick = server.getAverageNanosPerTick() / 1000000.0;
                     if (!(millisecondsPerTick > 40.0)) {
                        requestCollection(snapshot, currentTimeNanos, "自动高内存压力回收");
                     }
                  }
               }
            } else {
               consecutiveHighPressureSamples = 0;
            }
         }
      } else {
         consecutiveHighPressureSamples = 0;
      }
   }

   public static boolean requestManualCollection() {
      long currentTimeNanos = System.nanoTime();
      long configuredCooldownNanos = getConfiguredCooldownNanos();
      if (!hasElapsedSince(lastGcRequestNanos, currentTimeNanos, configuredCooldownNanos)) {
         return false;
      } else {
         requestCollection(getSnapshot(), currentTimeNanos, "管理员手动回收");
         return true;
      }
   }

   public static GreenManMemoryManager.MemorySnapshot getSnapshot() {
      MemoryUsage heapUsage = MEMORY_BEAN.getHeapMemoryUsage();
      long usedBytes = Math.max(0L, heapUsage.getUsed());
      long committedBytes = Math.max(0L, heapUsage.getCommitted());
      long maximumBytes = heapUsage.getMax();
      double maximumUsagePercent = maximumBytes <= 0L ? 0.0 : Math.min(100.0, usedBytes * 100.0 / maximumBytes);
      return new GreenManMemoryManager.MemorySnapshot(usedBytes, committedBytes, maximumBytes, maximumUsagePercent);
   }

   private static void requestCollection(GreenManMemoryManager.MemorySnapshot snapshot, long currentTimeNanos, String reason) {
      lastGcRequestNanos = currentTimeNanos;
      consecutiveHighPressureSamples = 0;
      if (lastCollectionLogNanos != 0L && currentTimeNanos - lastCollectionLogNanos < 60000000000L) {
         GreenManServer.LOGGER.debug("{}：已按冷却执行，当前堆内存使用率 {}%", reason, String.format(Locale.ROOT, "%.1f", snapshot.maximumUsagePercent));
      } else {
         lastCollectionLogNanos = currentTimeNanos;
         GreenManServer.LOGGER
            .info(
               "{}：当前堆内存已用 {} MiB，最大 {} MiB，使用率 {}%",
               new Object[]{
                  reason, toMebibytes(snapshot.usedBytes), toMebibytes(snapshot.maximumBytes), String.format(Locale.ROOT, "%.1f", snapshot.maximumUsagePercent)
               }
            );
      }

      MEMORY_BEAN.gc();
   }

   private static long getConfiguredCooldownNanos() {
      long configuredSeconds = Math.max(1L, (long)GreenManServerConfig.getMemoryGcCooldownSeconds());
      return configuredSeconds * 1000000000L;
   }

   private static boolean hasElapsedSince(long previousTimeNanos, long currentTimeNanos, long requiredIntervalNanos) {
      if (previousTimeNanos == 0L) {
         return true;
      } else {
         long elapsedNanos = currentTimeNanos - previousTimeNanos;
         return elapsedNanos < 0L ? true : elapsedNanos >= Math.max(1L, requiredIntervalNanos);
      }
   }

   private static long toMebibytes(long bytes) {
      return bytes <= 0L ? 0L : bytes / 1048576L;
   }

   public record MemorySnapshot(long usedBytes, long committedBytes, long maximumBytes, double maximumUsagePercent) {
   }
}
