package shit.shmily.join;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.minecraft.server.MinecraftServer;

public final class GreenManJoinSoundScheduler {
   private static final ConcurrentLinkedQueue<GreenManJoinSoundScheduler.PendingSoundTask> PENDING_SOUND_TASKS = new ConcurrentLinkedQueue<>();
   private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

   private GreenManJoinSoundScheduler() {
   }

   public static void register() {
      if (REGISTERED.compareAndSet(false, true)) {
         ServerTickEvents.END_SERVER_TICK.register(GreenManJoinSoundScheduler::tick);
         ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> PENDING_SOUND_TASKS.clear());
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> PENDING_SOUND_TASKS.clear());
      }
   }

   public static boolean schedule(MinecraftServer server, int delayTicks, Runnable soundAction) {
      if (server != null && soundAction != null) {
         int safeDelayTicks = Math.max(0, delayTicks);
         long targetTick = (long)server.getTicks() + safeDelayTicks;
         PENDING_SOUND_TASKS.offer(new GreenManJoinSoundScheduler.PendingSoundTask(server, targetTick, soundAction));
         return true;
      } else {
         return false;
      }
   }

   private static void tick(MinecraftServer server) {
      if (server != null && !PENDING_SOUND_TASKS.isEmpty()) {
         int tasksToInspect = PENDING_SOUND_TASKS.size();

         for (int taskIndex = 0; taskIndex < tasksToInspect; taskIndex++) {
            GreenManJoinSoundScheduler.PendingSoundTask pendingSoundTask = PENDING_SOUND_TASKS.poll();
            if (pendingSoundTask == null) {
               break;
            }

            if (pendingSoundTask.server() == server) {
               if (server.getTicks() < pendingSoundTask.targetTick()) {
                  PENDING_SOUND_TASKS.offer(pendingSoundTask);
               } else {
                  try {
                     pendingSoundTask.soundAction().run();
                  } catch (RuntimeException var5) {
                  }
               }
            }
         }
      }
   }

   private record PendingSoundTask(MinecraftServer server, long targetTick, Runnable soundAction) {
   }
}
