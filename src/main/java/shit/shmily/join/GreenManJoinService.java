package shit.shmily.join;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.music.GreenManMusicService;
import shit.shmily.text.GreenManTextFormatter;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GreenManJoinService {
   private GreenManJoinService() {
   }

   public static void handlePlayerJoin(ServerPlayerEntity joinedPlayer) {
      if (joinedPlayer != null && joinedPlayer.getEntityWorld().getServer() != null) {
         Optional<GreenManPlayerWelcomeConfig.PlayerWelcomeSettings> customSettings = GreenManPlayerWelcomeConfig.getEnabledSettings(joinedPlayer.getUuid());
         if (GreenManServerConfig.isJoinMessageAllowed(joinedPlayer.getUuid())) {
            if (GreenManServerConfig.isFeatureEnabled("joinMessage")
               && (customSettings.isEmpty() || customSettings.get().personalMessageEnabled() || customSettings.get().broadcastMessageEnabled())) {
               sendJoinMessages(joinedPlayer, customSettings.orElse(null));
            }

            if (GreenManServerConfig.isFeatureEnabled("joinSound")
               && (customSettings.isEmpty() || customSettings.get().soundEnabled() && !"OFF".equals(customSettings.get().soundMode()))) {
               long delaySeconds = customSettings.map(GreenManPlayerWelcomeConfig.PlayerWelcomeSettings::soundDelaySeconds)
                  .orElse(GreenManServerConfig.getJoinSoundDelaySeconds())
                  .intValue();
               long delayTicks = Math.max(0L, Math.min(1200L, delaySeconds * 20L));
               GreenManJoinSoundScheduler.schedule(
                  joinedPlayer.getEntityWorld().getServer(), (int)delayTicks, () -> playJoinSound(joinedPlayer, customSettings.orElse(null))
               );
            }
         }
      }
   }

   private static void sendJoinMessages(ServerPlayerEntity joinedPlayer) {
      sendJoinMessages(joinedPlayer, GreenManPlayerWelcomeConfig.getEnabledSettings(joinedPlayer.getUuid()).orElse(null));
   }

   private static void sendJoinMessages(ServerPlayerEntity joinedPlayer, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings customSettings) {
      if (customSettings != null ? customSettings.personalMessageEnabled() : GreenManServerConfig.isJoinMessagePersonalEnabled()) {
         String template = customSettings != null ? customSettings.personalMessageTemplate() : GreenManServerConfig.getJoinMessagePersonalTemplate();
         String nameStyle = customSettings != null ? customSettings.playerNameStyle() : GreenManServerConfig.getJoinMessagePlayerNameStyle();
         Text personalMessage = buildTemplateMessage(template, nameStyle, joinedPlayer);
         if (!personalMessage.getString().isEmpty()) {
            joinedPlayer.sendMessage(personalMessage);
         }
      }

      if (customSettings != null ? customSettings.broadcastMessageEnabled() : GreenManServerConfig.isJoinMessageBroadcastEnabled()) {
         String template = customSettings != null ? customSettings.personalMessageTemplate() : GreenManServerConfig.getJoinMessageBroadcastTemplate();
         String nameStyle = customSettings != null ? customSettings.playerNameStyle() : GreenManServerConfig.getJoinMessagePlayerNameStyle();
         Text broadcastMessage = buildTemplateMessage(template, nameStyle, joinedPlayer);
         if (!broadcastMessage.getString().isEmpty()) {
            joinedPlayer.getEntityWorld().getServer().getPlayerManager().broadcast(broadcastMessage, false);
         }
      }
   }

   private static Text buildTemplateMessage(String rawTemplate, ServerPlayerEntity joinedPlayer) {
      return buildTemplateMessage(rawTemplate, GreenManServerConfig.getJoinMessagePlayerNameStyle(), joinedPlayer);
   }

   private static Text buildTemplateMessage(String rawTemplate, String playerNameStyle, ServerPlayerEntity joinedPlayer) {
      if (rawTemplate != null && !rawTemplate.isEmpty() && joinedPlayer != null && joinedPlayer.getEntityWorld().getServer() != null) {
         String resolvedTemplate = rawTemplate.replace("{online_count}", Integer.toString(joinedPlayer.getEntityWorld().getServer().getCurrentPlayerCount()))
            .replace("{max_players}", Integer.toString(joinedPlayer.getEntityWorld().getServer().getMaxPlayerCount()))
            .replace("{server_name}", GreenManServerConfig.getTabServerName());
         String[] templateParts = resolvedTemplate.split("\\{player}", -1);
         MutableText resultComponent = Text.empty();

         for (int partIndex = 0; partIndex < templateParts.length; partIndex++) {
            resultComponent.append(GreenManTextFormatter.parseRainbow(templateParts[partIndex], Style.EMPTY));
            if (partIndex + 1 < templateParts.length) {
               String styledPlayerName = (playerNameStyle == null ? "" : playerNameStyle) + joinedPlayer.getName().getString();
               resultComponent.append(GreenManTextFormatter.parseRainbow(styledPlayerName, Style.EMPTY));
            }
         }

         return resultComponent;
      } else {
         return Text.empty();
      }
   }

   private static void playJoinSound(ServerPlayerEntity joinedPlayer, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings customSettings) {
      if (joinedPlayer != null && joinedPlayer.networkHandler != null && joinedPlayer.getEntityWorld().getServer() != null) {
         String joinSoundMode = customSettings != null ? customSettings.soundMode() : GreenManServerConfig.getJoinSoundMode();
         if (!"OFF".equals(joinSoundMode)) {
            if ("INTERNAL".equals(joinSoundMode) || "BOTH".equals(joinSoundMode)) {
               List<ServerPlayerEntity> recipients = customSettings != null && customSettings.soundBroadcastEnabled()
                  ? joinedPlayer.getEntityWorld().getServer().getPlayerManager().getPlayerList()
                  : (
                     customSettings == null && GreenManServerConfig.isJoinSoundBroadcastEnabled()
                        ? joinedPlayer.getEntityWorld().getServer().getPlayerManager().getPlayerList()
                        : List.of(joinedPlayer)
                  );
               String internalFile = customSettings != null ? customSettings.internalMusicFile() : GreenManServerConfig.getJoinInternalMusicFile();
               GreenManMusicService.playInternalAudio(recipients, internalFile);
            }

            if ("VANILLA".equals(joinSoundMode) || "BOTH".equals(joinSoundMode)) {
               playVanillaSounds(joinedPlayer, customSettings);
            }
         }
      }
   }

   private static void playVanillaSounds(ServerPlayerEntity joinedPlayer, GreenManPlayerWelcomeConfig.PlayerWelcomeSettings customSettings) {
      List<String> configuredSoundIds = customSettings != null ? customSettings.vanillaSoundIds() : GreenManServerConfig.getJoinVanillaSoundIds();
      if (configuredSoundIds.isEmpty()) {
         GreenManServer.LOGGER.warn("进服原版音效未播放：joinVanillaSoundIds为空");
      } else {
         for (ServerPlayerEntity soundRecipient : customSettings != null
            ? (customSettings.soundBroadcastEnabled() ? joinedPlayer.getEntityWorld().getServer().getPlayerManager().getPlayerList() : List.of(joinedPlayer))
            : (
               GreenManServerConfig.isJoinSoundBroadcastEnabled()
                  ? joinedPlayer.getEntityWorld().getServer().getPlayerManager().getPlayerList()
                  : List.of(joinedPlayer)
            )) {
            if (soundRecipient != null && soundRecipient.networkHandler != null) {
               for (String configuredSoundId : configuredSoundIds) {
                  Identifier soundIdentifier = Identifier.tryParse(configuredSoundId);
                  if (soundIdentifier == null) {
                     GreenManServer.LOGGER.warn("进服原版音效ID格式无效：{}", configuredSoundId);
                  } else {
                     Optional<Reference<SoundEvent>> soundHolder = Registries.SOUND_EVENT.getEntry(soundIdentifier);
                     if (soundHolder.isEmpty()) {
                        GreenManServer.LOGGER.warn("进服原版音效不存在：{}", soundIdentifier);
                     } else {
                        PlaySoundS2CPacket soundPacket = new PlaySoundS2CPacket(
                           (RegistryEntry)soundHolder.get(),
                           SoundCategory.MASTER,
                           soundRecipient.getX(),
                           soundRecipient.getY(),
                           soundRecipient.getZ(),
                           1.0F,
                           1.0F,
                           soundRecipient.getRandom().nextLong()
                        );
                        soundRecipient.networkHandler.sendPacket(soundPacket);
                     }
                  }
               }
            }
         }
      }
   }
}
