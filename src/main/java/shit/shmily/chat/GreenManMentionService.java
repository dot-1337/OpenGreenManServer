package shit.shmily.chat;

import shit.shmily.config.GreenManServerConfig;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.s2c.play.ChatSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatSuggestionsS2CPacket.Action;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManMentionService {
   private static final int MAX_PLAYER_NAME_LENGTH = 16;
   private static final List<String> GLOBAL_MENTION_KEYWORDS = List.of("所有人", "全体成员");
   private static final List<String> GLOBAL_MENTION_COMPLETIONS = GLOBAL_MENTION_KEYWORDS.stream()
      .map(globalMentionKeyword -> "@" + globalMentionKeyword)
      .toList();
   private static final ConcurrentHashMap<UUID, ArrayDeque<Long>> GLOBAL_MENTION_TIMESTAMPS = new ConcurrentHashMap<>();
   private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

   private GreenManMentionService() {
   }

   public static void register() {
      if (REGISTERED.compareAndSet(false, true)) {
         ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(GreenManMentionService::allowGlobalMentionMessage);
      }
   }

   public static void handlePlayerJoin(ServerPlayerEntity joinedPlayer) {
      if (GreenManServerConfig.isFeatureEnabled("mentions")
         && joinedPlayer != null
         && joinedPlayer.networkHandler != null
         && joinedPlayer.getEntityWorld().getServer() != null) {
         MinecraftServer server = joinedPlayer.getEntityWorld().getServer();
         sendCompletionPacket(joinedPlayer, Action.ADD, createMentionCompletions(server, joinedPlayer));
         List<String> joinedPlayerCompletion = List.of("@" + joinedPlayer.getGameProfile().name());

         for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            if (onlinePlayer != null && !onlinePlayer.getUuid().equals(joinedPlayer.getUuid())) {
               sendCompletionPacket(onlinePlayer, Action.ADD, joinedPlayerCompletion);
            }
         }
      }
   }

   public static void handlePlayerDisconnect(ServerPlayerEntity disconnectedPlayer) {
      if (disconnectedPlayer != null && disconnectedPlayer.getEntityWorld().getServer() != null) {
         MinecraftServer server = disconnectedPlayer.getEntityWorld().getServer();
         GLOBAL_MENTION_TIMESTAMPS.remove(disconnectedPlayer.getUuid());
         List<String> disconnectedCompletion = List.of("@" + disconnectedPlayer.getGameProfile().name());

         for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            if (onlinePlayer != null && !onlinePlayer.getUuid().equals(disconnectedPlayer.getUuid())) {
               sendCompletionPacket(onlinePlayer, Action.REMOVE, disconnectedCompletion);
            }
         }
      }
   }

   public static void refreshAllCompletions(MinecraftServer server) {
      if (server != null) {
         for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            sendCompletionPacket(onlinePlayer, Action.REMOVE, GLOBAL_MENTION_COMPLETIONS);
         }

         List<String> allRemovableCompletions = createAllRemovableCompletions(server);
         Action action = GreenManServerConfig.isFeatureEnabled("mentions") ? Action.ADD : Action.REMOVE;

         for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
            List<String> synchronizedCompletions = action == Action.ADD ? createMentionCompletions(server, onlinePlayer) : allRemovableCompletions;
            sendCompletionPacket(onlinePlayer, action, synchronizedCompletions);
         }
      }
   }

   public static Text decorateMessage(String messageText, MinecraftServer server, ServerPlayerEntity sender, ServerPlayerEntity receiver) {
      if (!GreenManServerConfig.isFeatureEnabled("mentions")) {
         return null;
      } else if (messageText != null && !messageText.isEmpty() && server != null && receiver != null) {
         List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager()
            .getPlayerList()
            .stream()
            .filter(
               onlinePlayer -> onlinePlayer != null
                  && onlinePlayer.getGameProfile() != null
                  && onlinePlayer.getGameProfile().name() != null
                  && !onlinePlayer.getGameProfile().name().isEmpty()
                  && onlinePlayer.getGameProfile().name().length() <= 16
            )
            .sorted(Comparator.<ServerPlayerEntity>comparingInt(onlinePlayer -> onlinePlayer.getGameProfile().name().length()).reversed())
            .toList();
         MutableText decoratedMessage = Text.empty();
         int currentTextIndex = 0;
         boolean receiverMentioned = false;
         String receiverActionBarMessage = "";
         boolean foundOnlineMention = false;

         while (currentTextIndex < messageText.length()) {
            int atIndex = messageText.indexOf(64, currentTextIndex);
            if (atIndex < 0) {
               decoratedMessage.append(Text.literal(messageText.substring(currentTextIndex)));
               break;
            }

            decoratedMessage.append(Text.literal(messageText.substring(currentTextIndex, atIndex)));
            int nameStartIndex = atIndex + 1;
            String globalMentionKeyword = findGlobalMentionKeyword(messageText, nameStartIndex, sender);
            if (!globalMentionKeyword.isEmpty()) {
               int globalMentionEndIndex = nameStartIndex + globalMentionKeyword.length();
               appendMentionBoundaryBefore(decoratedMessage, messageText, atIndex);
               decoratedMessage.append(Text.literal("@" + globalMentionKeyword).formatted(Formatting.LIGHT_PURPLE));
               appendMentionBoundaryAfter(decoratedMessage, messageText, globalMentionEndIndex);
               foundOnlineMention = true;
               if (sender == null || !sender.getUuid().equals(receiver.getUuid())) {
                  receiverMentioned = true;
                  if (!receiverActionBarMessage.startsWith("有人@了")) {
                     receiverActionBarMessage = "有人@了" + globalMentionKeyword;
                  }
               }

               currentTextIndex = globalMentionEndIndex;
            } else {
               GreenManMentionService.MentionMatch mentionMatch = findLongestMentionMatch(messageText, nameStartIndex, onlinePlayers);
               ServerPlayerEntity mentionedPlayer = mentionMatch.player();
               String mentionedName = mentionMatch.playerName();
               int nameEndIndex = mentionMatch.endIndex();
               if (mentionedPlayer != null && !mentionedName.isEmpty()) {
                  appendMentionBoundaryBefore(decoratedMessage, messageText, atIndex);
                  decoratedMessage.append(Text.literal("@" + mentionedName).formatted(Formatting.GOLD));
                  appendMentionBoundaryAfter(decoratedMessage, messageText, nameEndIndex);
                  foundOnlineMention = true;
                  if (mentionedPlayer.getUuid().equals(receiver.getUuid()) && (sender == null || !sender.getUuid().equals(receiver.getUuid()))) {
                     receiverMentioned = true;
                     if (receiverActionBarMessage.isEmpty()) {
                        receiverActionBarMessage = "你被@了";
                     }
                  }

                  currentTextIndex = nameEndIndex;
               } else {
                  decoratedMessage.append(Text.literal("@"));
                  currentTextIndex = nameStartIndex;
               }
            }
         }

         if (receiverMentioned) {
            sendMentionSound(receiver);
            if (GreenManServerConfig.isFeatureEnabled("mentionActionBar")) {
               sendMentionActionBar(receiver, receiverActionBarMessage);
            }
         }

         return !foundOnlineMention ? null : decoratedMessage;
      } else {
         return null;
      }
   }

   private static String findGlobalMentionKeyword(String messageText, int keywordStartIndex, ServerPlayerEntity sender) {
      if (canUseGlobalMention(sender) && messageText != null && keywordStartIndex >= 0 && keywordStartIndex <= messageText.length()) {
         for (String globalMentionKeyword : GLOBAL_MENTION_KEYWORDS) {
            int keywordEndIndex = keywordStartIndex + globalMentionKeyword.length();
            if (keywordEndIndex <= messageText.length()
               && messageText.regionMatches(true, keywordStartIndex, globalMentionKeyword, 0, globalMentionKeyword.length())) {
               return globalMentionKeyword;
            }
         }

         return "";
      } else {
         return "";
      }
   }

   private static GreenManMentionService.MentionMatch findLongestMentionMatch(String messageText, int nameStartIndex, List<ServerPlayerEntity> onlinePlayers) {
      if (messageText != null && nameStartIndex >= 0 && nameStartIndex <= messageText.length() && onlinePlayers != null && !onlinePlayers.isEmpty()) {
         for (ServerPlayerEntity onlinePlayer : onlinePlayers) {
            String playerName = onlinePlayer.getGameProfile().name();
            if (nameStartIndex + playerName.length() <= messageText.length()
               && messageText.regionMatches(true, nameStartIndex, playerName, 0, playerName.length())) {
               return new GreenManMentionService.MentionMatch(onlinePlayer, playerName, nameStartIndex + playerName.length());
            }
         }

         return GreenManMentionService.MentionMatch.NOT_FOUND;
      } else {
         return GreenManMentionService.MentionMatch.NOT_FOUND;
      }
   }

   private static void appendMentionBoundaryBefore(MutableText decoratedMessage, String messageText, int atIndex) {
      if (decoratedMessage != null && messageText != null && atIndex > 0 && atIndex <= messageText.length()) {
         if (isMentionTextCharacter(messageText.charAt(atIndex - 1)) && !decoratedMessage.getString().endsWith(" ")) {
            decoratedMessage.append(Text.literal(" "));
         }
      }
   }

   private static void appendMentionBoundaryAfter(MutableText decoratedMessage, String messageText, int nameEndIndex) {
      if (decoratedMessage != null && messageText != null && nameEndIndex >= 0 && nameEndIndex < messageText.length()) {
         if (isMentionTextCharacter(messageText.charAt(nameEndIndex)) || messageText.charAt(nameEndIndex) == '@') {
            decoratedMessage.append(Text.literal(" "));
         }
      }
   }

   private static boolean isMentionTextCharacter(char character) {
      return Character.isLetterOrDigit(character) || character == '_';
   }

   private static List<String> createMentionCompletions(MinecraftServer server, ServerPlayerEntity receiver) {
      if (server == null) {
         return List.of();
      } else {
         List<String> playerMentionCompletions = Arrays.stream(server.getPlayerManager().getPlayerNames())
            .filter(playerName -> playerName != null && !playerName.isEmpty() && playerName.length() <= 16)
            .map(playerName -> "@" + playerName)
            .toList();
         return !canUseGlobalMention(receiver)
            ? playerMentionCompletions
            : Stream.concat(playerMentionCompletions.stream(), GLOBAL_MENTION_COMPLETIONS.stream()).distinct().toList();
      }
   }

   private static List<String> createAllRemovableCompletions(MinecraftServer server) {
      return server == null
         ? GLOBAL_MENTION_COMPLETIONS
         : Stream.concat(
               Arrays.stream(server.getPlayerManager().getPlayerNames())
                  .filter(playerName -> playerName != null && !playerName.isEmpty() && playerName.length() <= 16)
                  .map(playerName -> "@" + playerName),
               GLOBAL_MENTION_COMPLETIONS.stream()
            )
            .distinct()
            .toList();
   }

   private static boolean isAdministrator(ServerPlayerEntity player) {
      return player == null ? false : CommandManager.requirePermissionLevel(CommandManager.MODERATORS_CHECK).test(player.getCommandSource());
   }

   private static boolean allowGlobalMentionMessage(SignedMessage playerChatMessage, ServerPlayerEntity sender, Parameters chatType) {
      if (playerChatMessage != null && sender != null && GreenManServerConfig.isFeatureEnabled("mentions")) {
         String messageText = playerChatMessage.getSignedContent();
         if (!containsGlobalMention(messageText)) {
            return true;
         } else if (isAdministrator(sender)) {
            return true;
         } else if (!canUseGlobalMention(sender)) {
            sender.sendMessage(Text.literal("你没有使用 @所有人 或 @全体成员 的权限").formatted(Formatting.RED));
            return false;
         } else if (!tryAcquireGlobalMention(sender)) {
            int windowSeconds = GreenManServerConfig.getMemberGlobalMentionWindowSeconds();
            int maximumCount = GreenManServerConfig.getMemberGlobalMentionMaxCount();
            sender.sendMessage(Text.literal("全服提醒过于频繁，当前限制为 " + windowSeconds + " 秒内最多 " + maximumCount + " 次").formatted(Formatting.YELLOW));
            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private static boolean containsGlobalMention(String messageText) {
      if (messageText != null && !messageText.isEmpty()) {
         for (String globalMentionKeyword : GLOBAL_MENTION_KEYWORDS) {
            if (messageText.contains("@" + globalMentionKeyword)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean canUseGlobalMention(ServerPlayerEntity player) {
      if (player == null) {
         return false;
      } else if (isAdministrator(player)) {
         return true;
      } else {
         Boolean playerOverride = GreenManServerConfig.getMemberGlobalMentionOverride(player.getUuid());
         return playerOverride != null ? playerOverride : GreenManServerConfig.isMemberGlobalMentionEnabled();
      }
   }

   private static boolean tryAcquireGlobalMention(ServerPlayerEntity sender) {
      if (sender == null) {
         return false;
      } else {
         long currentTimeNanos = System.nanoTime();
         long windowNanos = GreenManServerConfig.getMemberGlobalMentionWindowSeconds() * 1000000000L;
         long cutoffTimeNanos = currentTimeNanos - windowNanos;
         int maximumCount = GreenManServerConfig.getMemberGlobalMentionMaxCount();
         ArrayDeque<Long> usageTimestamps = GLOBAL_MENTION_TIMESTAMPS.computeIfAbsent(sender.getUuid(), ignoredUuid -> new ArrayDeque<>());
         synchronized (usageTimestamps) {
            while (!usageTimestamps.isEmpty() && usageTimestamps.peekFirst() < cutoffTimeNanos) {
               usageTimestamps.pollFirst();
            }

            if (usageTimestamps.size() >= maximumCount) {
               return false;
            } else {
               usageTimestamps.addLast(currentTimeNanos);
               return true;
            }
         }
      }
   }

   private static void sendCompletionPacket(ServerPlayerEntity receiver, Action action, List<String> completionEntries) {
      if (receiver != null && receiver.networkHandler != null && action != null && completionEntries != null && !completionEntries.isEmpty()) {
         receiver.networkHandler.sendPacket(new ChatSuggestionsS2CPacket(action, completionEntries));
      }
   }

   private static void sendMentionSound(ServerPlayerEntity receiver) {
      if (receiver != null && receiver.networkHandler != null) {
         receiver.networkHandler
            .sendPacket(
               new PlaySoundS2CPacket(
                  SoundEvents.BLOCK_NOTE_BLOCK_PLING,
                  SoundCategory.PLAYERS,
                  receiver.getX(),
                  receiver.getY(),
                  receiver.getZ(),
                  0.7F,
                  1.6F,
                  receiver.getRandom().nextLong()
               )
            );
      }
   }

   private static void sendMentionActionBar(ServerPlayerEntity receiver, String actionBarMessage) {
      if (receiver != null && receiver.networkHandler != null && actionBarMessage != null && !actionBarMessage.isEmpty()) {
         Text actionBarComponent = Text.literal(actionBarMessage).formatted(new Formatting[]{Formatting.GOLD, Formatting.BOLD});
         receiver.networkHandler.sendPacket(new OverlayMessageS2CPacket(actionBarComponent));
      }
   }

   private record MentionMatch(ServerPlayerEntity player, String playerName, int endIndex) {
      private static final GreenManMentionService.MentionMatch NOT_FOUND = new GreenManMentionService.MentionMatch(null, "", -1);
   }
}
