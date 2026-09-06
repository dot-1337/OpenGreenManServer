package shit.shmily.vote;

import shit.shmily.chat.GreenManChatHistory;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.text.GreenManTextFormatter;
import com.mojang.brigadier.CommandDispatcher;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent.RunCommand;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManVoteService {
   private static final AtomicInteger NEXT_VOTE_ID = new AtomicInteger(1);
   private static final Map<Integer, GreenManVoteService.ActiveVote> ACTIVE_VOTES = new ConcurrentHashMap<>();
   private static volatile ScheduledThreadPoolExecutor scheduler;
   private static final DateTimeFormatter VOTE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
      .withZone(ZoneId.of("Asia/Shanghai"));
   private static boolean registered;

   private GreenManVoteService() {
   }

   public static synchronized void register() {
      if (!registered) {
         registered = true;
         ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(GreenManVoteService::handleChatVote);
         ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> shutdown());
      }
   }

   public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandBuildContext, RegistrationEnvironment commandSelection) {
      if (dispatcher != null) {
         ;
      }
   }

   public static int createVote(ServerCommandSource source, String title, int durationSeconds, List<String> options) {
      if (source == null || source.getServer() == null || title == null || title.isBlank() || options == null) {
         return -1;
      } else if (!GreenManServerConfig.isFeatureEnabled("voting")) {
         return -1;
      } else {
         String safeTitle = sanitizeText(title, 256);
         List<String> safeOptions = sanitizeOptions(options);
         if (!safeTitle.isEmpty() && safeOptions.size() >= 2 && safeOptions.size() <= GreenManServerConfig.getVoteMaxOptionCount()) {
            int safeDurationSeconds = Math.max(1, Math.min(durationSeconds, GreenManServerConfig.getVoteMaxDurationSeconds()));
            int voteId = allocateVoteId();
            GreenManVoteService.ActiveVote activeVote = new GreenManVoteService.ActiveVote(
               voteId, safeTitle, safeOptions, source.getName(), Instant.now(), Instant.now().plusSeconds(safeDurationSeconds)
            );
            ACTIVE_VOTES.put(voteId, activeVote);
            activeVote.finishTask = getScheduler()
               .schedule(() -> source.getServer().execute(() -> finishVote(voteId, source.getServer(), false)), (long)safeDurationSeconds, TimeUnit.SECONDS);
            broadcastVotePrompt(source.getServer(), activeVote);
            return voteId;
         } else {
            return -1;
         }
      }
   }

   public static boolean finishVoteById(MinecraftServer server, int voteId) {
      if (server != null && ACTIVE_VOTES.containsKey(voteId)) {
         finishVote(voteId, server, true);
         return true;
      } else {
         return false;
      }
   }

   public static boolean cancelVote(int voteId) {
      GreenManVoteService.ActiveVote activeVote = ACTIVE_VOTES.remove(voteId);
      if (activeVote == null) {
         return false;
      } else {
         if (activeVote.finishTask != null) {
            activeVote.finishTask.cancel(false);
         }

         return true;
      }
   }

   public static String getStatus() {
      if (ACTIVE_VOTES.isEmpty()) {
         return "当前没有进行中的投票";
      } else {
         StringBuilder statusBuilder = new StringBuilder("进行中的投票：");

         for (GreenManVoteService.ActiveVote activeVote : ACTIVE_VOTES.values()) {
            long remainingSeconds = Math.max(0L, activeVote.endTime.getEpochSecond() - Instant.now().getEpochSecond());
            statusBuilder.append("#").append(activeVote.voteId).append(" ").append(activeVote.title).append("（剩余").append(remainingSeconds).append("秒） ");
         }

         return statusBuilder.toString().trim();
      }
   }

   private static boolean handleChatVote(SignedMessage message, ServerPlayerEntity sender, Parameters chatType) {
      if (message != null && sender != null && GreenManServerConfig.isFeatureEnabled("voting")) {
         String inputText = message.getSignedContent() == null ? "" : message.getSignedContent().trim();
         if (inputText.isEmpty()) {
            return true;
         } else {
            List<GreenManVoteService.MatchedVote> matchedVotes = new ArrayList<>();

            for (GreenManVoteService.ActiveVote activeVote : ACTIVE_VOTES.values()) {
               int optionIndex = activeVote.findOption(inputText);
               if (optionIndex >= 0) {
                  matchedVotes.add(new GreenManVoteService.MatchedVote(activeVote, optionIndex));
               }
            }

            if (matchedVotes.isEmpty()) {
               return true;
            } else if (matchedVotes.size() > 1) {
               sender.sendMessage(Text.literal("检测到多个投票匹配，请点击选项或使用 /vote choose <投票编号> <选项编号>"));
               return false;
            } else {
               GreenManVoteService.VoteResult voteResult = matchedVotes.get(0).activeVote.recordVote(sender, matchedVotes.get(0).optionIndex);
               sendVoteResultMessage(sender, voteResult);
               return false;
            }
         }
      } else {
         return true;
      }
   }

   public static boolean recordVoteById(ServerPlayerEntity player, int voteId, int optionIndex) {
      GreenManVoteService.ActiveVote activeVote = ACTIVE_VOTES.get(voteId);
      if (activeVote == null) {
         return false;
      } else {
         GreenManVoteService.VoteResult voteResult = activeVote.recordVote(player, optionIndex);
         sendVoteResultMessage(player, voteResult);
         return voteResult == GreenManVoteService.VoteResult.ACCEPTED;
      }
   }

   private static void sendVoteResultMessage(ServerPlayerEntity player, GreenManVoteService.VoteResult voteResult) {
      if (player != null && voteResult != null) {
         if (voteResult == GreenManVoteService.VoteResult.ACCEPTED) {
            player.sendMessage(Text.literal("投票成功").formatted(Formatting.GREEN));
         } else {
            String message = switch (voteResult) {
               case ACCEPTED -> "投票成功";
               case ALREADY_VOTED -> "你已经在本次投票中投过票";
               case INVALID_OPTION -> "投票选项无效";
               case EXPIRED -> "本次投票已经结束";
               case NO_PLAYER -> "只有玩家可以投票";
            };
            player.sendMessage(Text.literal(message).formatted(Formatting.RED));
         }
      }
   }

   private static void broadcastVotePrompt(MinecraftServer server, GreenManVoteService.ActiveVote activeVote) {
      Text prompt = GreenManTextFormatter.parseRainbow("&6[投票 #" + activeVote.voteId + "] &f" + activeVote.title, Style.EMPTY);
      server.getPlayerManager().broadcast(prompt, false);
      Text actionBarPrompt = GreenManTextFormatter.parseRainbow("&e有人发起了投票：&f" + activeVote.title, Style.EMPTY);

      for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
         if (onlinePlayer != null && onlinePlayer.networkHandler != null) {
            onlinePlayer.networkHandler.sendPacket(new OverlayMessageS2CPacket(actionBarPrompt));
         }
      }

      for (int optionIndex = 0; optionIndex < activeVote.options.size(); optionIndex++) {
         String optionText = activeVote.options.get(optionIndex);
         int optionNumber = optionIndex + 1;
         MutableText optionComponent = Text.literal("  [" + (optionIndex + 1) + "] " + optionText).formatted(Formatting.AQUA);
         optionComponent.styled(
            style -> style.withClickEvent(new RunCommand("/vote choose " + activeVote.voteId + " " + optionNumber))
               .withHoverEvent(new ShowText(Text.literal("点击投票，或单独发送编号/选项文字")))
         );
         server.getPlayerManager().broadcast(optionComponent, false);
      }
   }

   private static void finishVote(int voteId, MinecraftServer server, boolean manual) {
      GreenManVoteService.ActiveVote activeVote = ACTIVE_VOTES.remove(voteId);
      if (activeVote != null) {
         if (activeVote.finishTask != null && manual) {
            activeVote.finishTask.cancel(false);
         }

         Map<Integer, Integer> countByOption = new LinkedHashMap<>();

         for (int optionIndex = 0; optionIndex < activeVote.options.size(); optionIndex++) {
            countByOption.put(optionIndex, 0);
         }

         List<GreenManVoteService.VoteRecord> voteRecords = activeVote.getVoteRecords();

         for (GreenManVoteService.VoteRecord voteRecord : voteRecords) {
            countByOption.computeIfPresent(voteRecord.optionIndex, (ignoredIndex, oldCount) -> oldCount + 1);
         }

         broadcastAndCache(server, "&6[投票 #" + voteId + " 已结束] &f" + activeVote.title);

         for (int optionIndex = 0; optionIndex < activeVote.options.size(); optionIndex++) {
            broadcastAndCache(server, "&e" + (optionIndex + 1) + ". &f" + activeVote.options.get(optionIndex) + " &7— " + countByOption.get(optionIndex) + "票");
         }

         if (GreenManServerConfig.isVoteResultsDetailEnabled()) {
            for (GreenManVoteService.VoteRecord voteRecord : voteRecords) {
               String detail = "&7- &f"
                  + voteRecord.playerName
                  + "：&e"
                  + activeVote.options.get(voteRecord.optionIndex)
                  + " &7("
                  + VOTE_TIME_FORMATTER.format(voteRecord.votedAt)
                  + ")";
               broadcastAndCache(server, detail);
            }
         }
      }
   }

   private static void broadcastAndCache(MinecraftServer server, String rawText) {
      if (server != null && rawText != null && !rawText.isEmpty()) {
         Text component = GreenManTextFormatter.parseRainbow(rawText, Style.EMPTY);
         server.getPlayerManager().broadcast(component, false);
         GreenManChatHistory.cacheSystemMessage(component.getString());
      }
   }

   private static int allocateVoteId() {
      for (int attempt = 0; attempt < Integer.MAX_VALUE; attempt++) {
         int candidate = NEXT_VOTE_ID.getAndUpdate(previous -> previous == Integer.MAX_VALUE ? 1 : previous + 1);
         if (!ACTIVE_VOTES.containsKey(candidate)) {
            return candidate;
         }
      }

      return -1;
   }

   private static List<String> sanitizeOptions(Collection<String> rawOptions) {
      List<String> sanitizedOptions = new ArrayList<>();

      for (String rawOption : rawOptions) {
         String sanitizedOption = sanitizeText(rawOption, 128);
         if (!sanitizedOption.isEmpty() && sanitizedOptions.stream().noneMatch(existing -> existing.equalsIgnoreCase(sanitizedOption))) {
            sanitizedOptions.add(sanitizedOption);
         }

         if (sanitizedOptions.size() >= GreenManServerConfig.getVoteMaxOptionCount()) {
            break;
         }
      }

      return sanitizedOptions;
   }

   private static synchronized ScheduledThreadPoolExecutor getScheduler() {
      if (scheduler == null || scheduler.isShutdown()) {
         scheduler = createScheduler();
      }

      return scheduler;
   }

   private static ScheduledThreadPoolExecutor createScheduler() {
      ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnableTask -> {
         Thread schedulerThread = new Thread(runnableTask, "GreenManServer-VoteScheduler");
         schedulerThread.setPriority(1);
         return schedulerThread;
      });
      scheduler.setRemoveOnCancelPolicy(true);
      return scheduler;
   }

   private static synchronized void shutdown() {
      for (Integer voteId : List.copyOf(ACTIVE_VOTES.keySet())) {
         cancelVote(voteId);
      }

      ScheduledThreadPoolExecutor currentScheduler = scheduler;
      scheduler = null;
      if (currentScheduler != null) {
         currentScheduler.shutdownNow();
      }
   }

   private static String sanitizeText(String rawText, int maximumCodePoints) {
      if (rawText != null && maximumCodePoints > 0) {
         StringBuilder textBuilder = new StringBuilder();
         rawText.codePoints()
            .filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != 167)
            .limit(maximumCodePoints)
            .forEach(textBuilder::appendCodePoint);
         return textBuilder.toString().trim();
      } else {
         return "";
      }
   }

   private static final class ActiveVote {
      private final int voteId;
      private final String title;
      private final List<String> options;
      private final String operator;
      private final Instant startTime;
      private final Instant endTime;
      private final Map<UUID, GreenManVoteService.VoteRecord> votes = new LinkedHashMap<>();
      private ScheduledFuture<?> finishTask;

      private ActiveVote(int voteId, String title, List<String> options, String operator, Instant startTime, Instant endTime) {
         this.voteId = voteId;
         this.title = title;
         this.options = List.copyOf(options);
         this.operator = operator;
         this.startTime = startTime;
         this.endTime = endTime;
      }

      private synchronized int findOption(String inputText) {
         String normalizedInput = inputText.trim();

         try {
            int numericOption = Integer.parseInt(normalizedInput) - 1;
            return numericOption >= 0 && numericOption < this.options.size() ? numericOption : -1;
         } catch (NumberFormatException var5) {
            for (int optionIndex = 0; optionIndex < this.options.size(); optionIndex++) {
               if (this.options.get(optionIndex).equalsIgnoreCase(normalizedInput)) {
                  return optionIndex;
               }
            }

            return -1;
         }
      }

      private synchronized GreenManVoteService.VoteResult recordVote(ServerPlayerEntity player, int optionIndex) {
         if (player == null) {
            return GreenManVoteService.VoteResult.NO_PLAYER;
         } else if (optionIndex >= 0 && optionIndex < this.options.size()) {
            if (!Instant.now().isBefore(this.endTime)) {
               return GreenManVoteService.VoteResult.EXPIRED;
            } else if (this.votes.containsKey(player.getUuid())) {
               return GreenManVoteService.VoteResult.ALREADY_VOTED;
            } else {
               Instant votedAt = Instant.now();
               this.votes
                  .put(player.getUuid(), new GreenManVoteService.VoteRecord(player.getName().getString(), player.getUuid(), optionIndex, votedAt));
               return GreenManVoteService.VoteResult.ACCEPTED;
            }
         } else {
            return GreenManVoteService.VoteResult.INVALID_OPTION;
         }
      }

      private synchronized List<GreenManVoteService.VoteRecord> getVoteRecords() {
         return List.copyOf(this.votes.values());
      }
   }

   private record MatchedVote(GreenManVoteService.ActiveVote activeVote, int optionIndex) {
   }

   private record VoteRecord(String playerName, UUID playerId, int optionIndex, Instant votedAt) {
   }

   private static enum VoteResult {
      ACCEPTED,
      ALREADY_VOTED,
      INVALID_OPTION,
      EXPIRED,
      NO_PLAYER;
   }
}
