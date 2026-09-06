package shit.shmily.mixin;

import shit.shmily.chat.GreenManMentionService;
import shit.shmily.config.GreenManServerConfig;
import java.util.UUID;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({ServerPlayNetworkHandler.class})
public abstract class PlayerChatMixin {
   @Shadow
   public ServerPlayerEntity player;
   private SignedMessage greenmanserver$currentChatMessage;

   @Inject(
      method = {"sendChatMessage"},
      at = {@At("HEAD")}
   )
   private void greenmanserver$captureChatMessage(SignedMessage message, Parameters chatType, CallbackInfo callbackInfo) {
      if (!GreenManServerConfig.isFeatureEnabled("mentions")) {
         this.greenmanserver$currentChatMessage = null;
      } else {
         this.greenmanserver$currentChatMessage = message;
      }
   }

   @Inject(
      method = {"sendChatMessage"},
      at = {@At("RETURN")}
   )
   private void greenmanserver$clearChatMessage(SignedMessage message, Parameters chatType, CallbackInfo callbackInfo) {
      this.greenmanserver$currentChatMessage = null;
   }

   @ModifyArgs(
      method = {"sendChatMessage(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/network/message/MessageType$Parameters;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/s2c/play/ChatMessageS2CPacket;<init>(ILjava/util/UUID;ILnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/network/message/MessageBody$Serialized;Lnet/minecraft/text/Text;Lnet/minecraft/network/message/FilterMask;Lnet/minecraft/network/message/MessageType$Parameters;)V"
      )
   )
   private void addTitleToActualSender(Args packetConstructorArguments) {
      if (GreenManServerConfig.isFeatureEnabled("title")) {
         if (this.player != null && this.player.getEntityWorld().getServer() != null && packetConstructorArguments != null) {
            UUID senderUuid = (UUID)packetConstructorArguments.get(1);
            Parameters originalChatType = (Parameters)packetConstructorArguments.get(7);
            if (senderUuid != null && originalChatType != null) {
               ServerPlayerEntity actualSender = this.player
                  .getEntityWorld()
                  .getServer()
                  .getPlayerManager()
                  .getPlayerList()
                  .stream()
                  .filter(onlinePlayer -> onlinePlayer.getUuid().equals(senderUuid))
                  .findFirst()
                  .orElse(null);
               if (actualSender != null && GreenManServerConfig.hasTitle(actualSender)) {
                  MutableText titledSenderName = GreenManServerConfig.getTitleComponent(actualSender)
                     .append(Text.literal(actualSender.getName().getString()).formatted(Formatting.WHITE));
                  packetConstructorArguments.set(7, new Parameters(originalChatType.type(), titledSenderName, originalChatType.targetName()));
               }
            }
         }
      }
   }

   @ModifyArgs(
      method = {"sendChatMessage(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/network/message/MessageType$Parameters;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/s2c/play/ChatMessageS2CPacket;<init>(ILjava/util/UUID;ILnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/network/message/MessageBody$Serialized;Lnet/minecraft/text/Text;Lnet/minecraft/network/message/FilterMask;Lnet/minecraft/network/message/MessageType$Parameters;)V"
      )
   )
   private void greenmanserver$decorateMentionDisplay(Args packetConstructorArguments) {
      if (GreenManServerConfig.isFeatureEnabled("mentions")) {
         if (this.player != null
            && this.player.getEntityWorld().getServer() != null
            && this.greenmanserver$currentChatMessage != null
            && packetConstructorArguments != null) {
            String signedMessageText = this.greenmanserver$currentChatMessage.getSignedContent();
            UUID actualSenderUuid = (UUID)packetConstructorArguments.get(1);
            if (actualSenderUuid != null) {
               ServerPlayerEntity actualSender = this.player
                  .getEntityWorld()
                  .getServer()
                  .getPlayerManager()
                  .getPlayerList()
                  .stream()
                  .filter(onlinePlayer -> onlinePlayer != null && actualSenderUuid.equals(onlinePlayer.getUuid()))
                  .findFirst()
                  .orElse(null);
               if (actualSender != null) {
                  Text decoratedContent = GreenManMentionService.decorateMessage(
                     signedMessageText, this.player.getEntityWorld().getServer(), actualSender, this.player
                  );
                  if (decoratedContent != null) {
                     packetConstructorArguments.set(5, decoratedContent);
                  }
               }
            }
         }
      }
   }
}
