package shit.shmily.mixin;

import shit.shmily.music.GreenManMusicService;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket.Status;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ServerCommonNetworkHandler.class})
public abstract class ResourcePackResponseMixin {
   @Inject(
      method = {"onResourcePackStatus"},
      at = {@At("RETURN")}
   )
   private void greenmanserver$handleMusicResourcePackResponse(ResourcePackStatusC2SPacket responsePacket, CallbackInfo callbackInfo) {
      if (responsePacket != null) {
         boolean loadedSuccessfully = responsePacket.status() == Status.SUCCESSFULLY_LOADED;
         if (loadedSuccessfully || responsePacket.status().hasFinished()) {
            GreenManMusicService.handleResourcePackResponse(responsePacket.id(), loadedSuccessfully);
         }
      }
   }
}
