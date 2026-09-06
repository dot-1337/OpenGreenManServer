package shit.shmily.chat;

import shit.shmily.config.GreenManServerConfig;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManChatLimiter {
   private static final Map<UUID, GreenManChatLimiter.PlayerChatState> PLAYER_STATES = new HashMap<>();

   private GreenManChatLimiter() {
   }

   public static void register() {
      ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(GreenManChatLimiter::allowChatMessage);
   }

   public static void removePlayer(ServerPlayerEntity player) {
      if (player != null) {
         PLAYER_STATES.remove(player.getUuid());
      }
   }

   private static boolean allowChatMessage(SignedMessage message, ServerPlayerEntity sender, Parameters chatType) {
      if (message != null && sender != null && GreenManServerConfig.isChatLimitEnabled() && !isAdministrator(sender)) {
         String signedContent = message.getSignedContent();
         int messageLength = signedContent.codePointCount(0, signedContent.length());
         if (messageLength > GreenManServerConfig.getChatMaxLength()) {
            sender.sendMessage(Text.literal("消息过长，最多允许 " + GreenManServerConfig.getChatMaxLength() + " 个字符").formatted(Formatting.RED));
            return false;
         } else {
            long currentTimeNanos = System.nanoTime();
            GreenManChatLimiter.PlayerChatState state = PLAYER_STATES.computeIfAbsent(
               sender.getUuid(), ignoredUuid -> new GreenManChatLimiter.PlayerChatState()
            );
            long cooldownNanos = GreenManServerConfig.getChatCooldownSeconds() * 1000000000L;
            if (cooldownNanos > 0L && state.lastAcceptedTimeNanos != 0L) {
               long remainingNanos = cooldownNanos - (currentTimeNanos - state.lastAcceptedTimeNanos);
               if (remainingNanos > 0L) {
                  long remainingSeconds = Math.max(1L, (remainingNanos + 999999999L) / 1000000000L);
                  sender.sendMessage(Text.literal("聊天过快，请等待 " + remainingSeconds + " 秒后再发送").formatted(Formatting.YELLOW));
                  return false;
               }
            }

            String normalizedMessage = signedContent.strip().toLowerCase(Locale.ROOT);
            long repeatWindowNanos = GreenManServerConfig.getChatRepeatWindowSeconds() * 1000000000L;
            if (repeatWindowNanos > 0L
               && normalizedMessage.equals(state.lastNormalizedMessage)
               && currentTimeNanos - state.lastAcceptedTimeNanos < repeatWindowNanos) {
               sender.sendMessage(Text.literal("请勿在短时间内重复发送相同消息").formatted(Formatting.YELLOW));
               return false;
            } else {
               state.lastAcceptedTimeNanos = currentTimeNanos;
               state.lastNormalizedMessage = normalizedMessage;
               return true;
            }
         }
      } else {
         return true;
      }
   }

   private static boolean isAdministrator(ServerPlayerEntity player) {
      return CommandManager.requirePermissionLevel(CommandManager.MODERATORS_CHECK).test(player.getCommandSource());
   }

   private static final class PlayerChatState {
      private long lastAcceptedTimeNanos;
      private String lastNormalizedMessage = "";
   }
}
