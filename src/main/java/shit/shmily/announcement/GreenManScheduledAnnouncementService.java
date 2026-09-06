package shit.shmily.announcement;

import shit.shmily.config.GreenManServerConfig;
import shit.shmily.text.GreenManTextFormatter;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class GreenManScheduledAnnouncementService {
   private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
   private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();
   private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
   private static volatile String lastTriggerKey = "";
   private static volatile long lastIntervalTriggerMillis = 0L;

   private GreenManScheduledAnnouncementService() {
   }

   public static void register() {
      if (REGISTERED.compareAndSet(false, true)) {
         ServerTickEvents.END_SERVER_TICK.register(GreenManScheduledAnnouncementService::tick);
         ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> resetRuntimeState());
      }
   }

   private static void tick(MinecraftServer server) {
      if (server != null && GreenManServerConfig.isScheduledAnnouncementEnabled() && !GreenManServerConfig.getScheduledAnnouncementText().isBlank()) {
         LocalDateTime currentDateTime = LocalDateTime.now(SERVER_ZONE);
         long currentMillis = System.currentTimeMillis();
         String scheduleMode = GreenManServerConfig.getScheduledAnnouncementMode();

         boolean shouldBroadcast = switch (scheduleMode) {
            case "interval" -> shouldTriggerByInterval(currentMillis);
            case "daily" -> shouldTriggerDaily(currentDateTime);
            case "weekly" -> shouldTriggerWeekly(currentDateTime);
            case "monthly" -> shouldTriggerMonthly(currentDateTime);
            default -> false;
         };
         if (shouldBroadcast) {
            broadcast(server);
         }
      }
   }

   private static boolean shouldTriggerByInterval(long currentMillis) {
      long intervalMillis = Math.max(1L, (long)GreenManServerConfig.getScheduledAnnouncementIntervalMinutes()) * 60000L;
      if (lastIntervalTriggerMillis <= 0L) {
         lastIntervalTriggerMillis = currentMillis;
         return false;
      } else if (currentMillis - lastIntervalTriggerMillis >= intervalMillis) {
         lastIntervalTriggerMillis = currentMillis;
         return true;
      } else {
         return false;
      }
   }

   private static boolean shouldTriggerDaily(LocalDateTime currentDateTime) {
      LocalTime configuredTime = parseConfiguredTime();
      return configuredTime != null && currentDateTime.getHour() == configuredTime.getHour() && currentDateTime.getMinute() == configuredTime.getMinute()
         ? markOnce(currentDateTime.toLocalDate().toString() + "-daily-" + configuredTime)
         : false;
   }

   private static boolean shouldTriggerWeekly(LocalDateTime currentDateTime) {
      LocalTime configuredTime = parseConfiguredTime();
      DayOfWeek configuredDay = DayOfWeek.of(GreenManServerConfig.getScheduledAnnouncementWeekday());
      return configuredTime != null
            && currentDateTime.getDayOfWeek() == configuredDay
            && currentDateTime.getHour() == configuredTime.getHour()
            && currentDateTime.getMinute() == configuredTime.getMinute()
         ? markOnce(currentDateTime.toLocalDate().toString() + "-weekly-" + configuredTime)
         : false;
   }

   private static boolean shouldTriggerMonthly(LocalDateTime currentDateTime) {
      LocalTime configuredTime = parseConfiguredTime();
      return configuredTime != null
            && currentDateTime.getDayOfMonth() == GreenManServerConfig.getScheduledAnnouncementMonthDay()
            && currentDateTime.getHour() == configuredTime.getHour()
            && currentDateTime.getMinute() == configuredTime.getMinute()
         ? markOnce(currentDateTime.getYear() + "-" + currentDateTime.getMonthValue() + "-monthly-" + configuredTime)
         : false;
   }

   private static LocalTime parseConfiguredTime() {
      try {
         return LocalTime.parse(GreenManServerConfig.getScheduledAnnouncementTime(), TIME_FORMATTER);
      } catch (RuntimeException var1) {
         return null;
      }
   }

   private static boolean markOnce(String triggerKey) {
      if (triggerKey != null && !triggerKey.equals(lastTriggerKey)) {
         lastTriggerKey = triggerKey;
         return true;
      } else {
         return false;
      }
   }

   private static void broadcast(MinecraftServer server) {
      Text titleComponent = GreenManTextFormatter.parseRainbow(GreenManServerConfig.getScheduledAnnouncementTitle(), Style.EMPTY);
      Text textComponent = GreenManTextFormatter.parseRainbow(GreenManServerConfig.getScheduledAnnouncementText(), Style.EMPTY);
      if (!titleComponent.getString().isEmpty()) {
         server.getPlayerManager().broadcast(titleComponent, false);
      }

      if (!textComponent.getString().isEmpty()) {
         server.getPlayerManager().broadcast(textComponent, false);
      }
   }

   public static boolean sendNow(MinecraftServer server) {
      if (server != null && GreenManServerConfig.isScheduledAnnouncementEnabled() && !GreenManServerConfig.getScheduledAnnouncementText().isBlank()) {
         broadcast(server);
         lastIntervalTriggerMillis = System.currentTimeMillis();
         return true;
      } else {
         return false;
      }
   }

   public static void refreshSchedule() {
      resetRuntimeState();
   }

   private static void resetRuntimeState() {
      lastTriggerKey = "";
      lastIntervalTriggerMillis = 0L;
   }
}
