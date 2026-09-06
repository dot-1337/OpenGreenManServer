package shit.shmily.command;

import shit.shmily.GreenManServer;
import shit.shmily.announcement.GreenManAnnouncementService;
import shit.shmily.announcement.GreenManScheduledAnnouncementService;
import shit.shmily.chat.GreenManChatArchive;
import shit.shmily.chat.GreenManChatHistory;
import shit.shmily.chat.GreenManMentionService;
import shit.shmily.config.GreenManBanWhitelistConfig;
import shit.shmily.config.GreenManServerConfig;
import shit.shmily.config.GreenManTweakerooWhitelistConfig;
import shit.shmily.itemclear.GreenManItemClearService;
import shit.shmily.join.GreenManPlayerWelcomeConfig;
import shit.shmily.memory.GreenManMemoryManager;
import shit.shmily.music.GreenManMusicService;
import shit.shmily.network.GreenManNetworkCheckBlocker;
import shit.shmily.performance.GreenManBackgroundTaskManager;
import shit.shmily.punishment.GreenManAnticheatService;
import shit.shmily.punishment.GreenManMuteService;
import shit.shmily.punishment.GreenManPunishmentService;
import shit.shmily.vote.GreenManVoteService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManServerCommands {
   private GreenManServerCommands() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT.register(GreenManServerCommands::registerCommands);
   }

   private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandBuildContext, RegistrationEnvironment commandSelection) {
      GreenManPunishmentService.registerEnhancedVanillaBanCommand(dispatcher);
      GreenManPunishmentService.registerEnhancedVanillaBanIpCommand(dispatcher);
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                                                         "greenman"
                                                                                                      )
                                                                                                      .requires(CommandManager.requirePermissionLevel(CommandManager.MODERATORS_CHECK)))
                                                                                                   .then(
                                                                                                      ((LiteralArgumentBuilder)CommandManager.literal("title")
                                                                                                            .then(
                                                                                                               CommandManager.literal("set")
                                                                                                                  .then(
                                                                                                                     CommandManager.argument(
                                                                                                                           "player", EntityArgumentType.player()
                                                                                                                        )
                                                                                                                        .then(
                                                                                                                           CommandManager.argument(
                                                                                                                                 "color",
                                                                                                                                 StringArgumentType.word()
                                                                                                                              )
                                                                                                                              .then(
                                                                                                                                 CommandManager.argument(
                                                                                                                                       "title",
                                                                                                                                       StringArgumentType.greedyString()
                                                                                                                                    )
                                                                                                                                    .executes(
                                                                                                                                       GreenManServerCommands::setTitle
                                                                                                                                    )
                                                                                                                              )
                                                                                                                        )
                                                                                                                  )
                                                                                                            ))
                                                                                                         .then(
                                                                                                            CommandManager.literal("clear")
                                                                                                               .then(
                                                                                                                  CommandManager.argument(
                                                                                                                        "player", EntityArgumentType.player()
                                                                                                                     )
                                                                                                                     .executes(
                                                                                                                        GreenManServerCommands::clearTitle
                                                                                                                     )
                                                                                                               )
                                                                                                         )
                                                                                                   ))
                                                                                                .then(
                                                                                                   CommandManager.literal("servername")
                                                                                                      .then(
                                                                                                         CommandManager.literal("set")
                                                                                                            .then(
                                                                                                               CommandManager.argument(
                                                                                                                     "name", StringArgumentType.greedyString()
                                                                                                                  )
                                                                                                                  .executes(
                                                                                                                     GreenManServerCommands::setServerName
                                                                                                                  )
                                                                                                            )
                                                                                                      )
                                                                                                ))
                                                                                             .then(
                                                                                                CommandManager.literal("ban")
                                                                                                   .then(
                                                                                                      CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                         .then(
                                                                                                            ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                                                     "duration", StringArgumentType.word()
                                                                                                                  )
                                                                                                                  .executes(GreenManServerCommands::banPlayer))
                                                                                                               .then(
                                                                                                                  CommandManager.argument(
                                                                                                                        "reason",
                                                                                                                        StringArgumentType.greedyString()
                                                                                                                     )
                                                                                                                     .executes(
                                                                                                                        GreenManServerCommands::banPlayer
                                                                                                                     )
                                                                                                               )
                                                                                                         )
                                                                                                   )
                                                                                             ))
                                                                                          .then(
                                                                                             CommandManager.literal("unban")
                                                                                                .then(
                                                                                                   CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                      .executes(GreenManServerCommands::unbanPlayer)
                                                                                                )
                                                                                          ))
                                                                                       .then(
                                                                                          CommandManager.literal("mute")
                                                                                             .then(
                                                                                                CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                   .then(
                                                                                                      ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                                               "duration", StringArgumentType.word()
                                                                                                            )
                                                                                                            .executes(GreenManServerCommands::mutePlayer))
                                                                                                         .then(
                                                                                                            CommandManager.argument(
                                                                                                                  "reason", StringArgumentType.greedyString()
                                                                                                               )
                                                                                                               .executes(GreenManServerCommands::mutePlayer)
                                                                                                         )
                                                                                                   )
                                                                                             )
                                                                                       ))
                                                                                    .then(
                                                                                       CommandManager.literal("unmute")
                                                                                          .then(
                                                                                             CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                .executes(GreenManServerCommands::unmutePlayer)
                                                                                          )
                                                                                    ))
                                                                                 .then(
                                                                                    CommandManager.literal("mutelist")
                                                                                       .executes(
                                                                                          context -> GreenManMuteService.showMuteList(
                                                                                             (ServerCommandSource)context.getSource()
                                                                                          )
                                                                                       )
                                                                                 ))
                                                                              .then(
                                                                                 CommandManager.literal("kick")
                                                                                    .then(
                                                                                       ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                                "player", EntityArgumentType.players()
                                                                                             )
                                                                                             .executes(GreenManServerCommands::kickPlayer))
                                                                                          .then(
                                                                                             CommandManager.argument("reason", StringArgumentType.greedyString())
                                                                                                .executes(GreenManServerCommands::kickPlayer)
                                                                                          )
                                                                                    )
                                                                              ))
                                                                           .then(
                                                                              CommandManager.literal("banlist")
                                                                                 .executes(
                                                                                    context -> GreenManPunishmentService.showBanList(
                                                                                       (ServerCommandSource)context.getSource()
                                                                                    )
                                                                                 )
                                                                           ))
                                                                        .then(
                                                                           CommandManager.literal("grimstreak")
                                                                              .then(
                                                                                 CommandManager.argument("player", EntityArgumentType.player())
                                                                                    .then(
                                                                                       CommandManager.argument("check", StringArgumentType.word())
                                                                                          .then(
                                                                                             ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                                      "violation_level",
                                                                                                      IntegerArgumentType.integer(0, 1000000)
                                                                                                   )
                                                                                                   .executes(GreenManServerCommands::grimStreak))
                                                                                                .then(
                                                                                                   CommandManager.argument(
                                                                                                         "verbose", StringArgumentType.greedyString()
                                                                                                      )
                                                                                                      .executes(GreenManServerCommands::grimStreak)
                                                                                                )
                                                                                          )
                                                                                    )
                                                                              )
                                                                        ))
                                                                     .then(
                                                                        CommandManager.literal("tweakeroo")
                                                                           .then(
                                                                              ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                                          "whitelist"
                                                                                       )
                                                                                       .then(
                                                                                          CommandManager.literal("add")
                                                                                             .then(
                                                                                                CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                   .executes(context -> updateTweakerooWhitelist(context, true))
                                                                                             )
                                                                                       ))
                                                                                    .then(
                                                                                       CommandManager.literal("remove")
                                                                                          .then(
                                                                                             CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                .executes(context -> updateTweakerooWhitelist(context, false))
                                                                                          )
                                                                                    ))
                                                                                 .then(
                                                                                    CommandManager.literal("list")
                                                                                       .executes(GreenManServerCommands::showTweakerooWhitelist)
                                                                                 )
                                                                           )
                                                                     ))
                                                                  .then(
                                                                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("whitelist")
                                                                              .requires(CommandManager.requirePermissionLevel(CommandManager.OWNERS_CHECK)))
                                                                           .then(
                                                                              ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("player")
                                                                                       .then(
                                                                                          CommandManager.literal("add")
                                                                                             .then(
                                                                                                CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                   .executes(
                                                                                                      context -> updateBanWhitelistPlayers(context, true)
                                                                                                   )
                                                                                             )
                                                                                       ))
                                                                                    .then(
                                                                                       CommandManager.literal("remove")
                                                                                          .then(
                                                                                             CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                                                                .executes(context -> updateBanWhitelistPlayers(context, false))
                                                                                          )
                                                                                    ))
                                                                                 .then(
                                                                                    CommandManager.literal("list")
                                                                                       .executes(GreenManServerCommands::showBanWhitelistPlayers)
                                                                                 )
                                                                           ))
                                                                        .then(
                                                                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("ip")
                                                                                    .then(
                                                                                       CommandManager.literal("add")
                                                                                          .then(
                                                                                             CommandManager.argument("ip", StringArgumentType.word())
                                                                                                .executes(context -> updateBanWhitelistIp(context, true))
                                                                                          )
                                                                                    ))
                                                                                 .then(
                                                                                    CommandManager.literal("remove")
                                                                                       .then(
                                                                                          CommandManager.argument("ip", StringArgumentType.word())
                                                                                             .executes(context -> updateBanWhitelistIp(context, false))
                                                                                       )
                                                                                 ))
                                                                              .then(
                                                                                 CommandManager.literal("list")
                                                                                    .executes(GreenManServerCommands::showBanWhitelistIps)
                                                                              )
                                                                        )
                                                                  ))
                                                               .then(
                                                                  CommandManager.literal("template")
                                                                     .then(
                                                                        ((LiteralArgumentBuilder)CommandManager.literal("reset")
                                                                              .executes(context -> resetTemplates(context, "all")))
                                                                           .then(
                                                                              CommandManager.argument("scope", StringArgumentType.word())
                                                                                 .executes(
                                                                                    context -> resetTemplates(
                                                                                       context, StringArgumentType.getString(context, "scope")
                                                                                    )
                                                                                 )
                                                                           )
                                                                     )
                                                               ))
                                                            .then(
                                                               CommandManager.literal("banip")
                                                                  .then(
                                                                     CommandManager.argument("ip", StringArgumentType.word())
                                                                        .then(
                                                                           ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                    "duration", StringArgumentType.word()
                                                                                 )
                                                                                 .executes(GreenManServerCommands::banIp))
                                                                              .then(
                                                                                 CommandManager.argument("reason", StringArgumentType.greedyString())
                                                                                    .executes(GreenManServerCommands::banIp)
                                                                              )
                                                                        )
                                                                  )
                                                            ))
                                                         .then(
                                                            CommandManager.literal("unbanip")
                                                               .then(
                                                                  CommandManager.argument("ip", StringArgumentType.word())
                                                                     .executes(GreenManServerCommands::unbanIp)
                                                               )
                                                         ))
                                                      .then(
                                                         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                              "vote"
                                                                           )
                                                                           .executes(GreenManServerCommands::showVoteUsage))
                                                                        .then(
                                                                           ((LiteralArgumentBuilder)CommandManager.literal("create")
                                                                                 .executes(GreenManServerCommands::showVoteCreateUsage))
                                                                              .then(
                                                                                 ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                          "duration", IntegerArgumentType.integer(1, 86400)
                                                                                       )
                                                                                       .executes(GreenManServerCommands::showVoteCreateUsage))
                                                                                    .then(
                                                                                       ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                                "title", StringArgumentType.string()
                                                                                             )
                                                                                             .executes(GreenManServerCommands::showVoteCreateUsage))
                                                                                          .then(
                                                                                             CommandManager.argument(
                                                                                                   "options", StringArgumentType.greedyString()
                                                                                                )
                                                                                                .executes(GreenManServerCommands::createVote)
                                                                                          )
                                                                                    )
                                                                              )
                                                                        ))
                                                                     .then(
                                                                        ((LiteralArgumentBuilder)CommandManager.literal("legacy")
                                                                              .executes(GreenManServerCommands::showVoteLegacyUsage))
                                                                           .then(
                                                                              ((RequiredArgumentBuilder)CommandManager.argument(
                                                                                       "duration", IntegerArgumentType.integer(1, 86400)
                                                                                    )
                                                                                    .executes(GreenManServerCommands::showVoteLegacyUsage))
                                                                                 .then(
                                                                                    CommandManager.argument("content", StringArgumentType.greedyString())
                                                                                       .executes(GreenManServerCommands::createLegacyVote)
                                                                                 )
                                                                           )
                                                                     ))
                                                                  .then(CommandManager.literal("status").executes(context -> {
                                                                     ((ServerCommandSource)context.getSource())
                                                                        .sendFeedback(() -> Text.literal(GreenManVoteService.getStatus()), false);
                                                                     return 1;
                                                                  })))
                                                               .then(
                                                                  ((LiteralArgumentBuilder)CommandManager.literal("finish")
                                                                        .executes(context -> showVoteIdUsage(context, "finish")))
                                                                     .then(
                                                                        CommandManager.argument("vote_id", IntegerArgumentType.integer(1))
                                                                           .executes(GreenManServerCommands::finishVote)
                                                                     )
                                                               ))
                                                            .then(
                                                               ((LiteralArgumentBuilder)CommandManager.literal("cancel")
                                                                     .executes(context -> showVoteIdUsage(context, "cancel")))
                                                                  .then(
                                                                     CommandManager.argument("vote_id", IntegerArgumentType.integer(1))
                                                                        .executes(GreenManServerCommands::cancelVote)
                                                                  )
                                                            )
                                                      ))
                                                   .then(
                                                      ((LiteralArgumentBuilder)CommandManager.literal("punishment")
                                                            .then(
                                                               CommandManager.literal("template")
                                                                  .then(
                                                                     ((LiteralArgumentBuilder)CommandManager.literal("reset")
                                                                           .executes(context -> resetTemplates(context, "all")))
                                                                        .then(
                                                                           CommandManager.argument("scope", StringArgumentType.word())
                                                                              .executes(
                                                                                 context -> resetTemplates(
                                                                                    context, StringArgumentType.getString(context, "scope")
                                                                                 )
                                                                              )
                                                                        )
                                                                  )
                                                            ))
                                                         .then(
                                                            ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                           "chatannouncement"
                                                                        )
                                                                        .then(
                                                                           CommandManager.literal("status")
                                                                              .executes(GreenManServerCommands::showBanChatAnnouncementStatus)
                                                                        ))
                                                                     .then(
                                                                        CommandManager.literal("enable")
                                                                           .executes(context -> setFeatureEnabledDirect(context, "banChatAnnouncement", true))
                                                                     ))
                                                                  .then(
                                                                     CommandManager.literal("disable")
                                                                        .executes(context -> setFeatureEnabledDirect(context, "banChatAnnouncement", false))
                                                                  ))
                                                               .then(
                                                                  CommandManager.literal("template")
                                                                     .then(
                                                                        CommandManager.argument("text", StringArgumentType.greedyString())
                                                                           .executes(GreenManServerCommands::setBanChatAnnouncementTemplate)
                                                                     )
                                                               )
                                                         )
                                                   ))
                                                .then(
                                                   ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                        "chatlimit"
                                                                     )
                                                                     .then(
                                                                        CommandManager.literal("status").executes(GreenManServerCommands::showChatLimitStatus)
                                                                     ))
                                                                  .then(
                                                                     CommandManager.literal("enable").executes(context -> setChatLimitEnabled(context, true))
                                                                  ))
                                                               .then(CommandManager.literal("disable").executes(context -> setChatLimitEnabled(context, false))))
                                                            .then(
                                                               CommandManager.literal("cooldown")
                                                                  .then(
                                                                     CommandManager.argument("seconds", IntegerArgumentType.integer(0, 60))
                                                                        .executes(GreenManServerCommands::setChatCooldown)
                                                                  )
                                                            ))
                                                         .then(
                                                            CommandManager.literal("maxlength")
                                                               .then(
                                                                  CommandManager.argument("length", IntegerArgumentType.integer(1, 256))
                                                                     .executes(GreenManServerCommands::setChatMaxLength)
                                                               )
                                                         ))
                                                      .then(
                                                         CommandManager.literal("repeat")
                                                            .then(
                                                               CommandManager.argument("seconds", IntegerArgumentType.integer(0, 300))
                                                                  .executes(GreenManServerCommands::setChatRepeatWindow)
                                                            )
                                                      )
                                                ))
                                             .then(
                                                ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("chatarchive")
                                                            .then(CommandManager.literal("status").executes(GreenManServerCommands::showChatArchiveStatus)))
                                                         .then(CommandManager.literal("enable").executes(context -> setChatArchiveEnabled(context, true))))
                                                      .then(CommandManager.literal("disable").executes(context -> setChatArchiveEnabled(context, false))))
                                                   .then(CommandManager.literal("clear").executes(GreenManServerCommands::clearChatArchives))
                                             ))
                                          .then(
                                             ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                               "chathistory"
                                                            )
                                                            .then(CommandManager.literal("status").executes(GreenManServerCommands::showChatHistoryStatus)))
                                                         .then(CommandManager.literal("enable").executes(context -> setChatHistoryEnabled(context, true))))
                                                      .then(CommandManager.literal("disable").executes(context -> setChatHistoryEnabled(context, false))))
                                                   .then(
                                                      CommandManager.literal("size")
                                                         .then(
                                                            CommandManager.argument("count", IntegerArgumentType.integer(0, 500))
                                                               .executes(GreenManServerCommands::setChatHistorySize)
                                                         )
                                                   ))
                                                .then(CommandManager.literal("clear").executes(GreenManServerCommands::clearChatHistory))
                                          ))
                                       .then(
                                          ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                  "announcement"
                                                               )
                                                               .then(CommandManager.literal("status").executes(GreenManServerCommands::showAnnouncementStatus)))
                                                            .then(CommandManager.literal("enable").executes(context -> setAnnouncementEnabled(context, true))))
                                                         .then(CommandManager.literal("disable").executes(context -> setAnnouncementEnabled(context, false))))
                                                      .then(
                                                         CommandManager.literal("set")
                                                            .then(
                                                               CommandManager.argument("message", StringArgumentType.greedyString())
                                                                  .executes(GreenManServerCommands::setAnnouncement)
                                                            )
                                                      ))
                                                   .then(
                                                      ((LiteralArgumentBuilder)CommandManager.literal("mode")
                                                            .then(CommandManager.literal("everyjoin").executes(context -> setAnnouncementMode(context, true))))
                                                         .then(CommandManager.literal("once").executes(context -> setAnnouncementMode(context, false)))
                                                   ))
                                                .then(CommandManager.literal("clear").executes(GreenManServerCommands::clearAnnouncement)))
                                             .then(CommandManager.literal("resetseen").executes(GreenManServerCommands::resetAnnouncementSeen))
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                                 "scheduledannouncement"
                                                                              )
                                                                              .then(
                                                                                 CommandManager.literal("status")
                                                                                    .executes(GreenManServerCommands::showScheduledAnnouncementStatus)
                                                                              ))
                                                                           .then(
                                                                              CommandManager.literal("enable")
                                                                                 .executes(context -> setScheduledAnnouncementEnabled(context, true))
                                                                           ))
                                                                        .then(
                                                                           CommandManager.literal("disable")
                                                                              .executes(context -> setScheduledAnnouncementEnabled(context, false))
                                                                        ))
                                                                     .then(
                                                                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                                                       "mode"
                                                                                    )
                                                                                    .then(
                                                                                       CommandManager.literal("interval")
                                                                                          .executes(
                                                                                             context -> setScheduledAnnouncementMode(context, "interval")
                                                                                          )
                                                                                    ))
                                                                                 .then(
                                                                                    CommandManager.literal("daily")
                                                                                       .executes(context -> setScheduledAnnouncementMode(context, "daily"))
                                                                                 ))
                                                                              .then(
                                                                                 CommandManager.literal("weekly")
                                                                                    .executes(context -> setScheduledAnnouncementMode(context, "weekly"))
                                                                              ))
                                                                           .then(
                                                                              CommandManager.literal("monthly")
                                                                                 .executes(context -> setScheduledAnnouncementMode(context, "monthly"))
                                                                           )
                                                                     ))
                                                                  .then(
                                                                     CommandManager.literal("daily")
                                                                        .then(
                                                                           CommandManager.argument("time", StringArgumentType.word())
                                                                              .executes(GreenManServerCommands::configureScheduledDaily)
                                                                        )
                                                                  ))
                                                               .then(
                                                                  CommandManager.literal("weekly")
                                                                     .then(
                                                                        CommandManager.argument("weekday", IntegerArgumentType.integer(1, 7))
                                                                           .then(
                                                                              CommandManager.argument("time", StringArgumentType.word())
                                                                                 .executes(GreenManServerCommands::configureScheduledWeekly)
                                                                           )
                                                                     )
                                                               ))
                                                            .then(
                                                               CommandManager.literal("monthly")
                                                                  .then(
                                                                     CommandManager.argument("monthday", IntegerArgumentType.integer(1, 31))
                                                                        .then(
                                                                           CommandManager.argument("time", StringArgumentType.word())
                                                                              .executes(GreenManServerCommands::configureScheduledMonthly)
                                                                        )
                                                                  )
                                                            ))
                                                         .then(
                                                            CommandManager.literal("interval")
                                                               .then(
                                                                  CommandManager.argument("minutes", IntegerArgumentType.integer(1, 1000000))
                                                                     .executes(GreenManServerCommands::setScheduledAnnouncementInterval)
                                                               )
                                                         ))
                                                      .then(
                                                         CommandManager.literal("time")
                                                            .then(
                                                               CommandManager.argument("time", StringArgumentType.word())
                                                                  .executes(GreenManServerCommands::setScheduledAnnouncementTime)
                                                            )
                                                      ))
                                                   .then(
                                                      CommandManager.literal("weekday")
                                                         .then(
                                                            CommandManager.argument("weekday", IntegerArgumentType.integer(1, 7))
                                                               .executes(GreenManServerCommands::setScheduledAnnouncementWeekday)
                                                         )
                                                   ))
                                                .then(
                                                   CommandManager.literal("monthday")
                                                      .then(
                                                         CommandManager.argument("monthday", IntegerArgumentType.integer(1, 31))
                                                            .executes(GreenManServerCommands::setScheduledAnnouncementMonthDay)
                                                      )
                                                ))
                                             .then(
                                                CommandManager.literal("content")
                                                   .then(
                                                      CommandManager.argument("title", StringArgumentType.string())
                                                         .then(
                                                            CommandManager.argument("text", StringArgumentType.greedyString())
                                                               .executes(GreenManServerCommands::setScheduledAnnouncementContent)
                                                         )
                                                   )
                                             ))
                                          .then(CommandManager.literal("now").executes(GreenManServerCommands::sendScheduledAnnouncementNow))
                                    ))
                                 .then(
                                    CommandManager.literal("sound")
                                       .then(
                                          CommandManager.literal("internal")
                                             .then(
                                                CommandManager.argument("target", StringArgumentType.word())
                                                   .then(
                                                      CommandManager.argument("file_name", StringArgumentType.greedyString())
                                                         .executes(GreenManServerCommands::playInternalSound)
                                                   )
                                             )
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                            "join"
                                                         )
                                                         .then(CommandManager.literal("status").executes(GreenManServerCommands::showJoinStatus)))
                                                      .then(CommandManager.literal("enable").executes(context -> setFeatureFlag(context, "joinMessage", true))))
                                                   .then(CommandManager.literal("disable").executes(context -> setFeatureFlag(context, "joinMessage", false))))
                                                .then(
                                                   CommandManager.literal("allow")
                                                      .then(
                                                         CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                            .executes(context -> updateJoinAllowedPlayers(context, true))
                                                      )
                                                ))
                                             .then(
                                                CommandManager.literal("deny")
                                                   .then(
                                                      CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                         .executes(context -> updateJoinAllowedPlayers(context, false))
                                                   )
                                             ))
                                          .then(CommandManager.literal("list").executes(GreenManServerCommands::showJoinAllowedPlayers)))
                                       .then(
                                          ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("channel")
                                                      .then(CommandManager.literal("personal").executes(context -> setJoinChannels(context, true, false))))
                                                   .then(CommandManager.literal("broadcast").executes(context -> setJoinChannels(context, false, true))))
                                                .then(CommandManager.literal("both").executes(context -> setJoinChannels(context, true, true))))
                                             .then(CommandManager.literal("off").executes(context -> setJoinChannels(context, false, false)))
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                            "sound"
                                                         )
                                                         .then(
                                                            ((LiteralArgumentBuilder)CommandManager.literal("broadcast")
                                                                  .then(
                                                                     CommandManager.literal("enable").executes(context -> setJoinSoundBroadcast(context, true))
                                                                  ))
                                                               .then(
                                                                  CommandManager.literal("disable").executes(context -> setJoinSoundBroadcast(context, false))
                                                               )
                                                         ))
                                                      .then(
                                                         ((LiteralArgumentBuilder)CommandManager.literal("delay")
                                                               .then(CommandManager.literal("status").executes(GreenManServerCommands::showJoinSoundDelay)))
                                                            .then(
                                                               CommandManager.argument("seconds", IntegerArgumentType.integer(0, 60))
                                                                  .executes(GreenManServerCommands::setJoinSoundDelay)
                                                            )
                                                      ))
                                                   .then(CommandManager.literal("off").executes(context -> setJoinSound(context, "OFF", ""))))
                                                .then(
                                                   CommandManager.literal("vanilla")
                                                      .then(
                                                         CommandManager.argument("sound_id", StringArgumentType.greedyString())
                                                            .executes(
                                                               context -> setJoinSound(context, "VANILLA", StringArgumentType.getString(context, "sound_id"))
                                                            )
                                                      )
                                                ))
                                             .then(
                                                CommandManager.literal("internal")
                                                   .then(
                                                      CommandManager.argument("file_name", StringArgumentType.greedyString())
                                                         .executes(
                                                            context -> setJoinSound(context, "INTERNAL", StringArgumentType.getString(context, "file_name"))
                                                         )
                                                   )
                                             ))
                                          .then(
                                             CommandManager.literal("both")
                                                .then(
                                                   CommandManager.argument("file_name", StringArgumentType.greedyString())
                                                      .executes(context -> setJoinSound(context, "BOTH", StringArgumentType.getString(context, "file_name")))
                                                )
                                          )
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                      "globalmention"
                                                   )
                                                   .then(CommandManager.literal("status").executes(GreenManServerCommands::showGlobalMentionStatus)))
                                                .then(CommandManager.literal("enable").executes(context -> setMemberGlobalMentionEnabled(context, true))))
                                             .then(CommandManager.literal("disable").executes(context -> setMemberGlobalMentionEnabled(context, false))))
                                          .then(
                                             CommandManager.literal("limit")
                                                .then(
                                                   CommandManager.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                                      .then(
                                                         CommandManager.argument("count", IntegerArgumentType.integer(1, 20))
                                                            .executes(GreenManServerCommands::setGlobalMentionLimit)
                                                      )
                                                )
                                          ))
                                       .then(
                                          CommandManager.literal("allow")
                                             .then(
                                                CommandManager.argument("player", EntityArgumentType.player())
                                                   .executes(context -> setGlobalMentionOverride(context, true))
                                             )
                                       ))
                                    .then(
                                       CommandManager.literal("deny")
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(context -> setGlobalMentionOverride(context, false))
                                          )
                                    ))
                                 .then(
                                    CommandManager.literal("inherit")
                                       .then(
                                          CommandManager.argument("player", EntityArgumentType.player())
                                             .executes(GreenManServerCommands::clearGlobalMentionOverride)
                                       )
                                 )
                           ))
                        .then(
                           CommandManager.literal("performance").then(CommandManager.literal("status").executes(GreenManServerCommands::showPerformanceStatus))
                        ))
                     .then(
                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                             "memory"
                                          )
                                          .then(CommandManager.literal("status").executes(GreenManServerCommands::showMemoryStatus)))
                                       .then(CommandManager.literal("enable").executes(context -> setMemoryOptimizationEnabled(context, true))))
                                    .then(CommandManager.literal("disable").executes(context -> setMemoryOptimizationEnabled(context, false))))
                                 .then(
                                    CommandManager.literal("threshold")
                                       .then(
                                          CommandManager.argument("percent", IntegerArgumentType.integer(60, 95))
                                             .executes(GreenManServerCommands::setMemoryThreshold)
                                       )
                                 ))
                              .then(
                                 CommandManager.literal("cooldown")
                                    .then(
                                       CommandManager.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                          .executes(GreenManServerCommands::setMemoryCooldown)
                                    )
                              ))
                           .then(CommandManager.literal("gc").executes(GreenManServerCommands::requestMemoryCollection))
                     ))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                          "itemclear"
                                       )
                                       .then(CommandManager.literal("status").executes(context -> {
                                          ((ServerCommandSource)context.getSource())
                                             .sendFeedback(() -> Text.literal(GreenManItemClearService.getStatus()), false);
                                          return 1;
                                       })))
                                    .then(CommandManager.literal("enable").executes(context -> setFeatureEnabledDirect(context, "itemClear", true))))
                                 .then(CommandManager.literal("disable").executes(context -> setFeatureEnabledDirect(context, "itemClear", false))))
                              .then(
                                 CommandManager.literal("interval")
                                    .then(
                                       CommandManager.argument("minutes", IntegerArgumentType.integer(1, 1000000))
                                          .executes(GreenManServerCommands::setItemClearInterval)
                                    )
                              ))
                           .then(
                              CommandManager.literal("countdown")
                                 .then(
                                    CommandManager.argument("seconds", IntegerArgumentType.integer(1, 60))
                                       .executes(GreenManServerCommands::setItemClearCountdown)
                                 )
                           ))
                        .then(CommandManager.literal("now").executes(GreenManServerCommands::startItemClearNow))
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("config")
                           .then(CommandManager.literal("status").executes(GreenManServerCommands::showFeatureStatus)))
                        .then(CommandManager.literal("reload").executes(GreenManServerCommands::reloadConfig)))
                     .then(
                        CommandManager.literal("set")
                           .then(
                              ((RequiredArgumentBuilder)CommandManager.argument("feature", StringArgumentType.word())
                                    .then(CommandManager.literal("enable").executes(context -> setFeatureEnabled(context, true))))
                                 .then(CommandManager.literal("disable").executes(context -> setFeatureEnabled(context, false)))
                           )
                     )
               ))
            .then(CommandManager.literal("reload").executes(GreenManServerCommands::reloadConfig))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("vote").executes(GreenManServerCommands::showPublicVoteUsage))
            .then(
               ((LiteralArgumentBuilder)CommandManager.literal("choose").executes(GreenManServerCommands::showPublicVoteUsage))
                  .then(
                     ((RequiredArgumentBuilder)CommandManager.argument("vote_id", IntegerArgumentType.integer(1))
                           .executes(GreenManServerCommands::showPublicVoteUsage))
                        .then(CommandManager.argument("option", IntegerArgumentType.integer(1)).executes(GreenManServerCommands::chooseVote))
                  )
            )
      );
   }

   private static int showVoteUsage(CommandContext<ServerCommandSource> commandContext) {
      ((ServerCommandSource)commandContext.getSource())
         .sendError(Text.literal("正确格式：/greenman vote create <秒数> \"标题\" 选项1|选项2|...；管理命令：status、finish <编号>、cancel <编号>"));
      return 0;
   }

   private static int showVoteCreateUsage(CommandContext<ServerCommandSource> commandContext) {
      ((ServerCommandSource)commandContext.getSource())
         .sendError(Text.literal("正确格式：/greenman vote create <秒数> \"标题\" 选项1|选项2|...；示例：/greenman vote create 60 \"今晚玩什么\" 生存|建筑|小游戏"));
      return 0;
   }

   private static int showVoteLegacyUsage(CommandContext<ServerCommandSource> commandContext) {
      ((ServerCommandSource)commandContext.getSource())
         .sendError(Text.literal("正确格式：/greenman vote legacy <秒数> 标题|选项1|选项2|...；示例：/greenman vote legacy 60 今晚玩什么|生存|建筑"));
      return 0;
   }

   private static int showVoteIdUsage(CommandContext<ServerCommandSource> commandContext, String actionName) {
      ((ServerCommandSource)commandContext.getSource())
         .sendError(Text.literal("正确格式：/greenman vote " + actionName + " <投票编号>；可先使用 /greenman vote status 查看编号"));
      return 0;
   }

   private static int showPublicVoteUsage(CommandContext<ServerCommandSource> commandContext) {
      ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("正确格式：/vote choose <投票编号> <选项编号>；也可以点击聊天选项，或单独发送选项编号/完整选项文字"));
      return 0;
   }

   private static int createVote(CommandContext<ServerCommandSource> commandContext) {
      int durationSeconds = IntegerArgumentType.getInteger(commandContext, "duration");
      String voteTitle = StringArgumentType.getString(commandContext, "title");
      String optionsText = StringArgumentType.getString(commandContext, "options");
      String[] optionParts = optionsText.split("[\\|｜]", -1);
      if (optionParts.length < 2) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("格式：/greenman vote create <秒数> \"标题\" 选项1|选项2|..."));
         return 0;
      } else {
         List<String> voteOptions = new ArrayList<>(Arrays.asList(optionParts));
         int voteId = GreenManVoteService.createVote((ServerCommandSource)commandContext.getSource(), voteTitle, durationSeconds, voteOptions);
         return sendVoteCreationResult(commandContext, voteId);
      }
   }

   private static int createLegacyVote(CommandContext<ServerCommandSource> commandContext) {
      int durationSeconds = IntegerArgumentType.getInteger(commandContext, "duration");
      String content = StringArgumentType.getString(commandContext, "content");
      String[] contentParts = content.split("[\\|｜]", -1);
      if (contentParts.length < 3) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("格式：/greenman vote create <秒数> 标题|选项1|选项2|..."));
         return 0;
      } else {
         List<String> options = new ArrayList<>();

         for (int partIndex = 1; partIndex < contentParts.length; partIndex++) {
            options.add(contentParts[partIndex]);
         }

         int voteId = GreenManVoteService.createVote((ServerCommandSource)commandContext.getSource(), contentParts[0], durationSeconds, options);
         return sendVoteCreationResult(commandContext, voteId);
      }
   }

   private static int sendVoteCreationResult(CommandContext<ServerCommandSource> commandContext, int voteId) {
      if (voteId < 0) {
         ((ServerCommandSource)commandContext.getSource())
            .sendError(Text.literal("投票创建失败，请检查功能开关、标题、选项数量和时长；正确格式：/greenman vote create <秒数> \"标题\" 选项1|选项2|..."));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("投票创建成功，编号：" + voteId), true);
         return 1;
      }
   }

   private static int banIp(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      String ipAddress = StringArgumentType.getString(commandContext, "ip");
      String durationText = StringArgumentType.getString(commandContext, "duration");
      String reasonText = getOptionalStringArgument(commandContext, "reason");
      return GreenManPunishmentService.banIp((ServerCommandSource)commandContext.getSource(), ipAddress, durationText, reasonText);
   }

   private static int grimStreak(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(commandContext, "player");
      String checkName = StringArgumentType.getString(commandContext, "check");
      int violationLevel = IntegerArgumentType.getInteger(commandContext, "violation_level");
      String verboseText = getOptionalStringArgument(commandContext, "verbose");
      return GreenManAnticheatService.recordStreakAction(
         (ServerCommandSource)commandContext.getSource(), targetPlayer.getUuid(), checkName, violationLevel, verboseText
      );
   }

   private static int unbanIp(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      String ipAddress = StringArgumentType.getString(commandContext, "ip");
      return GreenManPunishmentService.unbanIp((ServerCommandSource)commandContext.getSource(), ipAddress);
   }

   private static int chooseVote(CommandContext<ServerCommandSource> commandContext) {
      ServerPlayerEntity player = ((ServerCommandSource)commandContext.getSource()).getPlayer();
      int voteId = IntegerArgumentType.getInteger(commandContext, "vote_id");
      int optionNumber = IntegerArgumentType.getInteger(commandContext, "option");
      boolean accepted = GreenManVoteService.recordVoteById(player, voteId, optionNumber - 1);
      return accepted ? 1 : 0;
   }

   private static int finishVote(CommandContext<ServerCommandSource> commandContext) {
      int voteId = IntegerArgumentType.getInteger(commandContext, "vote_id");
      boolean finished = GreenManVoteService.finishVoteById(((ServerCommandSource)commandContext.getSource()).getServer(), voteId);
      if (!finished) {
         ((ServerCommandSource)commandContext.getSource())
            .sendError(Text.literal("投票不存在或已经结束；正确格式：/greenman vote finish <投票编号>；可先使用 /greenman vote status 查看编号"));
         return 0;
      } else {
         return 1;
      }
   }

   private static int cancelVote(CommandContext<ServerCommandSource> commandContext) {
      int voteId = IntegerArgumentType.getInteger(commandContext, "vote_id");
      boolean cancelled = GreenManVoteService.cancelVote(voteId);
      if (!cancelled) {
         ((ServerCommandSource)commandContext.getSource())
            .sendError(Text.literal("投票不存在或已经结束；正确格式：/greenman vote cancel <投票编号>；可先使用 /greenman vote status 查看编号"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("投票 #" + voteId + " 已取消"), true);
         return 1;
      }
   }

   private static int resetTemplates(CommandContext<ServerCommandSource> commandContext, String requestedScope) {
      String templateScope = requestedScope != null && !requestedScope.isBlank() ? requestedScope.toLowerCase(Locale.ROOT) : "all";
      if (!GreenManServerConfig.resetTemplates(templateScope)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("模板范围无效或保存失败；可用：all、punishment、join、announcement、mute；也支持对应中文别名"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("模板已恢复默认值，范围：" + templateScope), true);
         return 1;
      }
   }

   private static int banPlayer(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      String durationText = StringArgumentType.getString(commandContext, "duration");
      String reasonText = getOptionalStringArgument(commandContext, "reason");
      return GreenManPunishmentService.banPlayers((ServerCommandSource)commandContext.getSource(), targetProfiles, durationText, reasonText);
   }

   private static int unbanPlayer(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      return GreenManPunishmentService.unbanPlayers((ServerCommandSource)commandContext.getSource(), targetProfiles);
   }

   private static int mutePlayer(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      String durationText = StringArgumentType.getString(commandContext, "duration");
      String reasonText = getOptionalStringArgument(commandContext, "reason");
      return GreenManMuteService.mutePlayers((ServerCommandSource)commandContext.getSource(), targetProfiles, durationText, reasonText);
   }

   private static int unmutePlayer(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      return GreenManMuteService.unmutePlayers((ServerCommandSource)commandContext.getSource(), targetProfiles);
   }

   private static int kickPlayer(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      Collection<ServerPlayerEntity> targetPlayers = EntityArgumentType.getPlayers(commandContext, "player");
      String reasonText = getOptionalStringArgument(commandContext, "reason");
      return GreenManPunishmentService.kickPlayers((ServerCommandSource)commandContext.getSource(), targetPlayers, reasonText);
   }

   private static String getOptionalStringArgument(CommandContext<ServerCommandSource> commandContext, String argumentName) {
      if (commandContext != null && argumentName != null && !argumentName.isEmpty()) {
         try {
            return StringArgumentType.getString(commandContext, argumentName);
         } catch (IllegalArgumentException var3) {
            return "";
         }
      } else {
         return "";
      }
   }

   private static int setTitle(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(commandContext, "player");
      String colorName = StringArgumentType.getString(commandContext, "color");
      String titleText = StringArgumentType.getString(commandContext, "title");
      Formatting titleColor = GreenManServerConfig.parseTitleColor(colorName);
      if (titleColor == null) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("称号颜色无效，可用 gold、aqua、red 等名称，或使用 &c、&l、&o 组合符号"));
         return 0;
      } else if (!GreenManServerConfig.setTitle(targetPlayer, colorName, titleText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("称号不能为空、不能包含控制字符，或配置文件保存失败"));
         return 0;
      } else {
         GreenManServer.refreshPlayerTabName(targetPlayer);
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(
               () -> Text.literal(
                  "已将 " + targetPlayer.getName().getString() + " 的称号设置为 " + GreenManServerConfig.getTitleComponent(targetPlayer).getString()
               ),
               true
            );
         return 1;
      }
   }

   private static int clearTitle(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(commandContext, "player");
      if (!GreenManServerConfig.clearTitle(targetPlayer)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("称号清除失败，配置文件可能无法保存"));
         return 0;
      } else {
         GreenManServer.refreshPlayerTabName(targetPlayer);
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("已清除 " + targetPlayer.getName().getString() + " 的称号"), true);
         return 1;
      }
   }

   private static int setServerName(CommandContext<ServerCommandSource> commandContext) {
      String serverName = StringArgumentType.getString(commandContext, "name");
      if (!GreenManServerConfig.setTabServerName(serverName)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("服务器名称不能为空、不能包含控制字符，或配置文件保存失败"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("TAB 服务器名称已更新为：" + GreenManServerConfig.getTabServerName()), true);
         return 1;
      }
   }

   private static int showChatLimitStatus(CommandContext<ServerCommandSource> commandContext) {
      String status = GreenManServerConfig.isChatLimitEnabled() ? "开启" : "关闭";
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(
            () -> Text.literal(
               "聊天限制："
                  + status
                  + "，冷却 "
                  + GreenManServerConfig.getChatCooldownSeconds()
                  + " 秒，最大长度 "
                  + GreenManServerConfig.getChatMaxLength()
                  + "，重复窗口 "
                  + GreenManServerConfig.getChatRepeatWindowSeconds()
                  + " 秒"
            ),
            false
         );
      return 1;
   }

   private static int setChatLimitEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setChatLimitEnabled(enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("聊天限制已" + (enabled ? "开启" : "关闭") + "，管理员不受影响"), true);
         return 1;
      }
   }

   private static int setChatCooldown(CommandContext<ServerCommandSource> commandContext) {
      int seconds = IntegerArgumentType.getInteger(commandContext, "seconds");
      if (!GreenManServerConfig.setChatCooldownSeconds(seconds)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("普通玩家聊天冷却已设为 " + seconds + " 秒"), true);
         return 1;
      }
   }

   private static int setChatMaxLength(CommandContext<ServerCommandSource> commandContext) {
      int maximumLength = IntegerArgumentType.getInteger(commandContext, "length");
      if (!GreenManServerConfig.setChatMaxLength(maximumLength)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("普通玩家聊天最大长度已设为 " + maximumLength + " 个字符"), true);
         return 1;
      }
   }

   private static int setChatRepeatWindow(CommandContext<ServerCommandSource> commandContext) {
      int seconds = IntegerArgumentType.getInteger(commandContext, "seconds");
      if (!GreenManServerConfig.setChatRepeatWindowSeconds(seconds)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("重复消息限制窗口已设为 " + seconds + " 秒"), true);
         return 1;
      }
   }

   private static int showChatArchiveStatus(CommandContext<ServerCommandSource> commandContext) {
      String archiveStatus = GreenManServerConfig.isChatArchiveEnabled() ? "开启" : "关闭";
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(
            () -> Text.literal(
               "聊天记录归档："
                  + archiveStatus
                  + "，保留 "
                  + GreenManServerConfig.getChatArchiveRetentionDays()
                  + " 天，单文件上限 "
                  + GreenManServerConfig.getChatArchiveMaxFileSizeMib()
                  + " MiB，待写入任务 "
                  + GreenManChatArchive.getPendingTaskCount()
                  + "，目录："
                  + GreenManChatArchive.getArchiveDirectoryForDisplay()
            ),
            false
         );
      return 1;
   }

   private static int setChatArchiveEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setFeatureEnabled("chatArchive", enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("服务器本地聊天记录归档已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int clearChatArchives(CommandContext<ServerCommandSource> commandContext) {
      if (!GreenManChatArchive.requestClearArchives()) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("聊天归档清理任务提交失败，请稍后重试"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("聊天归档清理任务已提交，只会删除 GreenManServer 自己的聊天日志"), true);
         return 1;
      }
   }

   private static int showChatHistoryStatus(CommandContext<ServerCommandSource> commandContext) {
      String historyStatus = GreenManServerConfig.isChatHistoryEnabled() ? "开启" : "关闭";
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(
            () -> Text.literal(
               "聊天历史回放："
                  + historyStatus
                  + "，配置缓存 "
                  + GreenManServerConfig.getChatHistoryCacheSize()
                  + " 条，当前缓存 "
                  + GreenManChatHistory.getCachedMessageCount()
                  + " 条"
            ),
            false
         );
      return 1;
   }

   private static int setChatHistoryEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setFeatureEnabled("chatHistory", enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         if (enabled) {
            GreenManChatHistory.requestWarmup(((ServerCommandSource)commandContext.getSource()).getServer());
         }

         if (!enabled) {
            GreenManChatHistory.clearHistory();
         }

         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("聊天历史回放已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int setChatHistorySize(CommandContext<ServerCommandSource> commandContext) {
      int cacheSize = IntegerArgumentType.getInteger(commandContext, "count");
      if (!GreenManServerConfig.setChatHistoryCacheSize(cacheSize)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManChatHistory.trimToConfiguredSize();
         if (cacheSize > 0 && GreenManServerConfig.isChatHistoryEnabled()) {
            GreenManChatHistory.requestWarmup(((ServerCommandSource)commandContext.getSource()).getServer());
         }

         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("最近聊天缓存已设置为 " + cacheSize + " 条"), true);
         return 1;
      }
   }

   private static int clearChatHistory(CommandContext<ServerCommandSource> commandContext) {
      GreenManChatHistory.clearHistory();
      ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("已清空内存中的最近聊天缓存，服务器本地聊天归档未删除"), true);
      return 1;
   }

   private static int showAnnouncementStatus(CommandContext<ServerCommandSource> commandContext) {
      String announcementStatus = GreenManServerConfig.isAnnouncementEnabled() ? "开启" : "关闭";
      String contentStatus = GreenManServerConfig.getAnnouncementText().isEmpty() ? "未设置" : "已设置";
      String displayMode = GreenManServerConfig.isAnnouncementShowEveryJoin() ? "每次进入显示" : "每个版本只显示一次";
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(
            () -> Text.literal(
               "上线公告："
                  + announcementStatus
                  + "，正文："
                  + contentStatus
                  + "，模式："
                  + displayMode
                  + "，标题样式："
                  + GreenManServerConfig.getAnnouncementTitle()
                  + "，版本："
                  + GreenManServerConfig.getAnnouncementVersion()
                  + "，已读记录："
                  + GreenManAnnouncementService.getSeenPlayerCount()
                  + " 人"
            ),
            false
         );
      return 1;
   }

   private static int setAnnouncementMode(CommandContext<ServerCommandSource> commandContext, boolean showEveryJoin) {
      if (!GreenManServerConfig.setAnnouncementShowEveryJoin(showEveryJoin)) {
         return sendSaveFailure(commandContext);
      } else if (!showEveryJoin && !GreenManAnnouncementService.resetSeenPlayers()) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("公告模式已保存，但已读状态后台保存任务未提交；关服时仍会再次保存"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal("上线公告已设置为" + (showEveryJoin ? "每次进入服务器都显示" : "每个公告版本每名玩家只显示一次")), true);
         return 1;
      }
   }

   private static int setAnnouncementEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setFeatureEnabled("announcement", enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("上线公告已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int setAnnouncement(CommandContext<ServerCommandSource> commandContext) {
      String announcementText = StringArgumentType.getString(commandContext, "message");
      if (!GreenManServerConfig.setAnnouncementText(announcementText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("公告不能为空，或配置文件保存失败"));
         return 0;
      } else {
         GreenManAnnouncementService.resetSeenPlayers();
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal("公告设置成功，当前模式：" + (GreenManServerConfig.isAnnouncementShowEveryJoin() ? "每次进入显示" : "每个版本只显示一次")), true);
         return 1;
      }
   }

   private static int clearAnnouncement(CommandContext<ServerCommandSource> commandContext) {
      if (!GreenManServerConfig.clearAnnouncementText()) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManAnnouncementService.resetSeenPlayers();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("上线公告正文已清空"), true);
         return 1;
      }
   }

   private static int resetAnnouncementSeen(CommandContext<ServerCommandSource> commandContext) {
      if (!GreenManAnnouncementService.resetSeenPlayers()) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("公告已读状态已在内存清空，但后台保存任务未提交，请检查服务器状态"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("已清除公告已读状态，当前公告会在玩家下次进入时再次显示"), true);
         return 1;
      }
   }

   private static int showScheduledAnnouncementStatus(CommandContext<ServerCommandSource> commandContext) {
      String statusText = "定时公告="
         + (GreenManServerConfig.isScheduledAnnouncementEnabled() ? "开启" : "关闭")
         + "，模式="
         + GreenManServerConfig.getScheduledAnnouncementMode()
         + "，间隔="
         + GreenManServerConfig.getScheduledAnnouncementIntervalMinutes()
         + "分钟，时间="
         + GreenManServerConfig.getScheduledAnnouncementTime()
         + "，星期="
         + GreenManServerConfig.getScheduledAnnouncementWeekday()
         + "，每月日期="
         + GreenManServerConfig.getScheduledAnnouncementMonthDay()
         + "，标题="
         + GreenManServerConfig.getScheduledAnnouncementTitle()
         + "，正文="
         + GreenManServerConfig.getScheduledAnnouncementText();
      ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal(statusText), false);
      return 1;
   }

   private static int setScheduledAnnouncementEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setScheduledAnnouncementEnabled(enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int setScheduledAnnouncementMode(CommandContext<ServerCommandSource> commandContext, String mode) {
      if (!GreenManServerConfig.setScheduledAnnouncementMode(mode)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告模式已设为：" + mode), true);
         return 1;
      }
   }

   private static int setScheduledAnnouncementInterval(CommandContext<ServerCommandSource> commandContext) {
      int intervalMinutes = IntegerArgumentType.getInteger(commandContext, "minutes");
      if (!GreenManServerConfig.configureScheduledAnnouncementInterval(intervalMinutes)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告已设为每隔 " + intervalMinutes + " 分钟发布"), true);
         return 1;
      }
   }

   private static int configureScheduledDaily(CommandContext<ServerCommandSource> commandContext) {
      String timeText = StringArgumentType.getString(commandContext, "time");
      if (!GreenManServerConfig.configureScheduledAnnouncementDaily(timeText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("时间格式无效，请使用HH:mm"));
         return 0;
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告已设为每天 " + timeText + " 发布"), true);
         return 1;
      }
   }

   private static int configureScheduledWeekly(CommandContext<ServerCommandSource> commandContext) {
      int weekday = IntegerArgumentType.getInteger(commandContext, "weekday");
      String timeText = StringArgumentType.getString(commandContext, "time");
      if (!GreenManServerConfig.configureScheduledAnnouncementWeekly(weekday, timeText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("星期或时间无效，星期范围1至7，时间格式为HH:mm"));
         return 0;
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告已设为每周第 " + weekday + " 天 " + timeText + " 发布"), true);
         return 1;
      }
   }

   private static int configureScheduledMonthly(CommandContext<ServerCommandSource> commandContext) {
      int monthDay = IntegerArgumentType.getInteger(commandContext, "monthday");
      String timeText = StringArgumentType.getString(commandContext, "time");
      if (!GreenManServerConfig.configureScheduledAnnouncementMonthly(monthDay, timeText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("日期或时间无效，日期范围1至31，时间格式为HH:mm"));
         return 0;
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告已设为每月 " + monthDay + " 日 " + timeText + " 发布"), true);
         return 1;
      }
   }

   private static int setScheduledAnnouncementTime(CommandContext<ServerCommandSource> commandContext) {
      String timeText = StringArgumentType.getString(commandContext, "time");
      if (!GreenManServerConfig.setScheduledAnnouncementTime(timeText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("时间格式无效，请使用24小时制HH:mm，例如20:30"));
         return 0;
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告时间已设为 " + timeText), true);
         return 1;
      }
   }

   private static int setScheduledAnnouncementWeekday(CommandContext<ServerCommandSource> commandContext) {
      int weekday = IntegerArgumentType.getInteger(commandContext, "weekday");
      if (!GreenManServerConfig.setScheduledAnnouncementWeekday(weekday)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("每周定时公告星期已设为 " + weekday + "（1周一，7周日）"), true);
         return 1;
      }
   }

   private static int setScheduledAnnouncementMonthDay(CommandContext<ServerCommandSource> commandContext) {
      int monthDay = IntegerArgumentType.getInteger(commandContext, "monthday");
      if (!GreenManServerConfig.setScheduledAnnouncementMonthDay(monthDay)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManScheduledAnnouncementService.refreshSchedule();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("每月定时公告日期已设为 " + monthDay + " 日；当月不存在该日期时跳过"), true);
         return 1;
      }
   }

   private static int setScheduledAnnouncementContent(CommandContext<ServerCommandSource> commandContext) {
      String titleText = StringArgumentType.getString(commandContext, "title");
      String bodyText = StringArgumentType.getString(commandContext, "text");
      if (!GreenManServerConfig.setScheduledAnnouncementContent(titleText, bodyText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("定时公告标题和正文不能为空，或配置保存失败"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告独立内容设置成功"), true);
         return 1;
      }
   }

   private static int sendScheduledAnnouncementNow(CommandContext<ServerCommandSource> commandContext) {
      if (!GreenManScheduledAnnouncementService.sendNow(((ServerCommandSource)commandContext.getSource()).getServer())) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("定时公告未开启、正文为空或服务器不可用"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("定时公告已立即向全服发送"), true);
         return 1;
      }
   }

   private static int showGlobalMentionStatus(CommandContext<ServerCommandSource> commandContext) {
      String memberStatus = GreenManServerConfig.isMemberGlobalMentionEnabled() ? "开启" : "关闭";
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(
            () -> Text.literal(
               "普通成员 @所有人："
                  + memberStatus
                  + "，限制 "
                  + GreenManServerConfig.getMemberGlobalMentionWindowSeconds()
                  + " 秒内最多 "
                  + GreenManServerConfig.getMemberGlobalMentionMaxCount()
                  + " 次；OP 不受限制，单人覆盖优先于全局开关"
            ),
            false
         );
      return 1;
   }

   private static int setMemberGlobalMentionEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setFeatureEnabled("memberGlobalMention", enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManMentionService.refreshAllCompletions(((ServerCommandSource)commandContext.getSource()).getServer());
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("普通成员 @所有人 权限已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int setGlobalMentionLimit(CommandContext<ServerCommandSource> commandContext) {
      int windowSeconds = IntegerArgumentType.getInteger(commandContext, "seconds");
      int maximumCount = IntegerArgumentType.getInteger(commandContext, "count");
      if (!GreenManServerConfig.setMemberGlobalMentionRateLimit(windowSeconds, maximumCount)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal("普通成员全服提醒已限制为 " + windowSeconds + " 秒内最多 " + maximumCount + " 次"), true);
         return 1;
      }
   }

   private static int setGlobalMentionOverride(CommandContext<ServerCommandSource> commandContext, boolean allowed) throws CommandSyntaxException {
      ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(commandContext, "player");
      if (!GreenManServerConfig.setMemberGlobalMentionOverride(targetPlayer.getUuid(), allowed)) {
         return sendSaveFailure(commandContext);
      } else {
         GreenManMentionService.refreshAllCompletions(((ServerCommandSource)commandContext.getSource()).getServer());
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal("已" + (allowed ? "允许 " : "禁止 ") + targetPlayer.getName().getString() + " 使用 @所有人"), true);
         return 1;
      }
   }

   private static int clearGlobalMentionOverride(CommandContext<ServerCommandSource> commandContext) throws CommandSyntaxException {
      ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(commandContext, "player");
      if (!GreenManServerConfig.clearMemberGlobalMentionOverride(targetPlayer.getUuid())) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("该玩家没有单独的 @所有人 权限设置，或配置保存失败"));
         return 0;
      } else {
         GreenManMentionService.refreshAllCompletions(((ServerCommandSource)commandContext.getSource()).getServer());
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal(targetPlayer.getName().getString() + " 已改为跟随普通成员全局 @所有人 开关"), true);
         return 1;
      }
   }

   private static int showPerformanceStatus(CommandContext<ServerCommandSource> commandContext) {
      int pendingTaskCount = GreenManBackgroundTaskManager.getPendingTaskCount();
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(() -> Text.literal("启动资源优化使用最多一个低优先级懒启动后台线程，当前待处理任务：" + pendingTaskCount + "；玩家加入仅读取内存缓存"), false);
      return 1;
   }

   private static int showMemoryStatus(CommandContext<ServerCommandSource> commandContext) {
      GreenManMemoryManager.MemorySnapshot snapshot = GreenManMemoryManager.getSnapshot();
      String status = GreenManServerConfig.isMemoryOptimizationEnabled() ? "开启" : "关闭";
      String maximumText = snapshot.maximumBytes() <= 0L ? "未知" : formatMebibytes(snapshot.maximumBytes()) + " MiB";
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(
            () -> Text.literal(
               "内存优化："
                  + status
                  + "，已用 "
                  + formatMebibytes(snapshot.usedBytes())
                  + " MiB，已提交 "
                  + formatMebibytes(snapshot.committedBytes())
                  + " MiB，最大 "
                  + maximumText
                  + "，使用率 "
                  + String.format(Locale.ROOT, "%.1f", snapshot.maximumUsagePercent())
                  + "%，自动阈值 "
                  + GreenManServerConfig.getMemoryPressureThresholdPercent()
                  + "%，冷却 "
                  + GreenManServerConfig.getMemoryGcCooldownSeconds()
                  + " 秒"
            ),
            false
         );
      return 1;
   }

   private static int setMemoryOptimizationEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      if (!GreenManServerConfig.setMemoryOptimizationEnabled(enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("保守内存优化已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int setMemoryThreshold(CommandContext<ServerCommandSource> commandContext) {
      int thresholdPercent = IntegerArgumentType.getInteger(commandContext, "percent");
      if (!GreenManServerConfig.setMemoryPressureThresholdPercent(thresholdPercent)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("自动内存回收阈值已设为 " + thresholdPercent + "% "), true);
         return 1;
      }
   }

   private static int setMemoryCooldown(CommandContext<ServerCommandSource> commandContext) {
      int seconds = IntegerArgumentType.getInteger(commandContext, "seconds");
      if (!GreenManServerConfig.setMemoryGcCooldownSeconds(seconds)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("自动内存回收冷却已设为 " + seconds + " 秒"), true);
         return 1;
      }
   }

   private static int requestMemoryCollection(CommandContext<ServerCommandSource> commandContext) {
      if (!GreenManMemoryManager.requestManualCollection()) {
         ((ServerCommandSource)commandContext.getSource())
            .sendError(Text.literal("距离上次内存回收请求尚未达到配置冷却时间（" + GreenManServerConfig.getMemoryGcCooldownSeconds() + " 秒），请稍后再试"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("已向 JVM 请求执行一次兼容性内存回收"), true);
         return 1;
      }
   }

   private static int setItemClearInterval(CommandContext<ServerCommandSource> commandContext) {
      int intervalMinutes = IntegerArgumentType.getInteger(commandContext, "minutes");
      if (!GreenManServerConfig.setItemClearIntervalMinutes(intervalMinutes)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("掉落物清除间隔设置失败，允许范围为1至1000000分钟"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("掉落物清除间隔已设为 " + intervalMinutes + " 分钟"), true);
         return 1;
      }
   }

   private static int setItemClearCountdown(CommandContext<ServerCommandSource> commandContext) {
      int countdownSeconds = IntegerArgumentType.getInteger(commandContext, "seconds");
      if (!GreenManServerConfig.setItemClearCountdownSeconds(countdownSeconds)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("掉落物清除倒计时设置失败，允许范围为1至60秒"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("掉落物清除倒计时已设为 " + countdownSeconds + " 秒"), true);
         return 1;
      }
   }

   private static int startItemClearNow(CommandContext<ServerCommandSource> commandContext) {
      MinecraftServer server = ((ServerCommandSource)commandContext.getSource()).getServer();
      if (!GreenManItemClearService.startManualCountdown(server)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("掉落物清除功能已关闭或服务器不可用"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("已启动掉落物清除倒计时"), true);
         return 1;
      }
   }

   private static int reloadConfig(CommandContext<ServerCommandSource> commandContext) {
      GreenManBanWhitelistConfig.load();
      GreenManTweakerooWhitelistConfig.load();
      GreenManServerConfig.load();
      GreenManPlayerWelcomeConfig.load();
      GreenManPlayerWelcomeConfig.syncAllowedPlayerTexts(GreenManServerConfig.getJoinMessageAllowedPlayerUuids());
      GreenManScheduledAnnouncementService.refreshSchedule();
      GreenManNetworkCheckBlocker.applyRuntimeConfig();
      GreenManServer.refreshAllPlayerTabNames(((ServerCommandSource)commandContext.getSource()).getServer());
      GreenManMentionService.refreshAllCompletions(((ServerCommandSource)commandContext.getSource()).getServer());
      GreenManChatHistory.trimToConfiguredSize();
      if (!GreenManServerConfig.isChatHistoryEnabled()) {
         GreenManChatHistory.clearHistory();
      }

      ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("GreenManServer 配置已重载"), true);
      return 1;
   }

   private static int updateTweakerooWhitelist(CommandContext<ServerCommandSource> commandContext, boolean allowed) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      if (targetProfiles != null && !targetProfiles.isEmpty()) {
         int changedPlayerCount = 0;

         for (PlayerConfigEntry targetProfile : targetProfiles) {
            if (targetProfile != null && targetProfile.id() != null) {
               boolean changed = allowed
                  ? GreenManTweakerooWhitelistConfig.addPlayer(targetProfile.id().toString())
                  : GreenManTweakerooWhitelistConfig.removePlayer(targetProfile.id().toString());
               if (!allowed) {
                  changed |= GreenManTweakerooWhitelistConfig.removePlayer(targetProfile.name());
               }

               if (changed) {
                  changedPlayerCount++;
               }
            }
         }

         if (changedPlayerCount == 0) {
            ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal(allowed ? "目标已在Tweakeroo兼容名单中或配置保存失败" : "目标不在Tweakeroo兼容名单中或配置保存失败"));
            return 0;
         } else {
            int resultPlayerCount = changedPlayerCount;
            ((ServerCommandSource)commandContext.getSource())
               .sendFeedback(() -> Text.literal("Tweakeroo兼容名单已" + (allowed ? "添加" : "移除") + " " + resultPlayerCount + " 名玩家"), true);
            return changedPlayerCount;
         }
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("没有找到可修改的玩家"));
         return 0;
      }
   }

   private static int showTweakerooWhitelist(CommandContext<ServerCommandSource> commandContext) {
      List<String> playerEntries = GreenManTweakerooWhitelistConfig.getPlayers();
      if (playerEntries.isEmpty()) {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("Tweakeroo兼容名单为空"), false);
         return 1;
      } else {
         List<String> displayNames = playerEntries.stream().map(entry -> resolveTweakerooDisplayName((ServerCommandSource)commandContext.getSource(), entry)).toList();
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("Tweakeroo兼容名单：" + String.join("、", displayNames)), false);
         return playerEntries.size();
      }
   }

   private static int updateBanWhitelistPlayers(CommandContext<ServerCommandSource> commandContext, boolean allowed) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      if (targetProfiles != null && !targetProfiles.isEmpty()) {
         int changedPlayerCount = 0;

         for (PlayerConfigEntry targetProfile : targetProfiles) {
            if (targetProfile != null && targetProfile.id() != null) {
               boolean changed = allowed
                  ? GreenManBanWhitelistConfig.addPlayer(targetProfile.id().toString())
                  : GreenManBanWhitelistConfig.removePlayer(targetProfile.id().toString());
               if (!allowed && targetProfile.name() != null && !targetProfile.name().isBlank()) {
                  changed |= GreenManBanWhitelistConfig.removePlayer(targetProfile.name());
               }

               if (changed) {
                  changedPlayerCount++;
               }
            }
         }

         if (changedPlayerCount == 0) {
            ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal(allowed ? "玩家已在GreenMan反作弊白名单中或保存失败" : "玩家不在GreenMan反作弊白名单中或保存失败"));
            return 0;
         } else {
            int resultPlayerCount = changedPlayerCount;
            ((ServerCommandSource)commandContext.getSource())
               .sendFeedback(() -> Text.literal("GreenMan反作弊玩家白名单已" + (allowed ? "添加" : "移除") + " " + resultPlayerCount + " 名玩家"), true);
            return resultPlayerCount;
         }
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("没有找到可修改的玩家"));
         return 0;
      }
   }

   private static int showBanWhitelistPlayers(CommandContext<ServerCommandSource> commandContext) {
      List<String> playerEntries = GreenManBanWhitelistConfig.getPlayers();
      if (playerEntries.isEmpty()) {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("GreenMan反作弊玩家白名单为空"), false);
         return 1;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("GreenMan反作弊玩家白名单：" + String.join("、", playerEntries)), false);
         return playerEntries.size();
      }
   }

   private static int updateBanWhitelistIp(CommandContext<ServerCommandSource> commandContext, boolean allowed) {
      String ipAddress = StringArgumentType.getString(commandContext, "ip");
      boolean changed = allowed ? GreenManBanWhitelistConfig.addIpAddress(ipAddress) : GreenManBanWhitelistConfig.removeIpAddress(ipAddress);
      if (!changed) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal(allowed ? "IP已存在、格式无效或配置保存失败" : "IP不存在、格式无效或配置保存失败"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal("GreenMan IP白名单已" + (allowed ? "添加" : "移除") + "：" + ipAddress), true);
         return 1;
      }
   }

   private static int showBanWhitelistIps(CommandContext<ServerCommandSource> commandContext) {
      List<String> ipEntries = GreenManBanWhitelistConfig.getIpAddresses();
      if (ipEntries.isEmpty()) {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("GreenMan IP白名单为空"), false);
         return 1;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("GreenMan IP白名单：" + String.join("、", ipEntries)), false);
         return ipEntries.size();
      }
   }

   private static String resolveTweakerooDisplayName(ServerCommandSource commandSource, String rawEntry) {
      if (rawEntry != null && !rawEntry.isBlank()) {
         String normalizedEntry = rawEntry.trim();

         UUID playerUuid;
         try {
            playerUuid = UUID.fromString(normalizedEntry);
         } catch (IllegalArgumentException var7) {
            return normalizedEntry;
         }

         if (commandSource != null && commandSource.getServer() != null) {
            ServerPlayerEntity onlinePlayer = commandSource.getServer().getPlayerManager().getPlayer(playerUuid);
            if (onlinePlayer != null
               && onlinePlayer.getGameProfile() != null
               && onlinePlayer.getGameProfile().name() != null
               && !onlinePlayer.getGameProfile().name().isBlank()) {
               return onlinePlayer.getGameProfile().name();
            } else {
               try {
                  return commandSource.getServer()
                     .getApiServices()
                     .nameToIdCache()
                     .getByUuid(playerUuid)
                     .<String>map(PlayerConfigEntry::name)
                     .filter(name -> name != null && !name.isBlank())
                     .orElse(normalizedEntry);
               } catch (RuntimeException var6) {
                  return normalizedEntry;
               }
            }
         } else {
            return normalizedEntry;
         }
      } else {
         return "未知玩家";
      }
   }

   private static int playInternalSound(CommandContext<ServerCommandSource> commandContext) {
      String targetName = StringArgumentType.getString(commandContext, "target");
      String fileName = StringArgumentType.getString(commandContext, "file_name").trim();
      if (fileName.isEmpty()) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("文件名不能为空，格式：/greenman sound internal <玩家|all> <文件名>"));
         return 0;
      } else if (((ServerCommandSource)commandContext.getSource()).getServer() == null) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("当前没有可用的服务端实例"));
         return 0;
      } else {
         List<ServerPlayerEntity> recipients;
         if ("all".equalsIgnoreCase(targetName)) {
            recipients = ((ServerCommandSource)commandContext.getSource()).getServer().getPlayerManager().getPlayerList();
         } else {
            ServerPlayerEntity targetPlayer = ((ServerCommandSource)commandContext.getSource()).getServer().getPlayerManager().getPlayer(targetName);
            if (targetPlayer == null) {
               ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("未找到在线玩家：" + targetName + "；目标可填写all"));
               return 0;
            }

            recipients = List.of(targetPlayer);
         }

         if (recipients.isEmpty()) {
            ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("当前没有可接收内部音频的在线玩家"));
            return 0;
         } else if (!GreenManMusicService.playInternalAudio(recipients, fileName)) {
            ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("内部音频播放失败，请确认文件位于GreenManMusic目录且资源包地址可访问"));
            return 0;
         } else {
            String targetDescription = "all".equalsIgnoreCase(targetName) ? "全服在线玩家" : targetName;
            ((ServerCommandSource)commandContext.getSource())
               .sendFeedback(() -> Text.literal("已向" + targetDescription + "推送内部音频：" + fileName + "；客户端加载成功后播放"), true);
            return recipients.size();
         }
      }
   }

   private static int showFeatureStatus(CommandContext<ServerCommandSource> commandContext) {
      String status = String.join(
         "；",
         featureStatus("tabStatus"),
         featureStatus("title"),
         featureStatus("latencyDisplay"),
         featureStatus("composter"),
         featureStatus("memory"),
         featureStatus("chatLimit"),
         featureStatus("chatArchive"),
         featureStatus("mentions"),
         featureStatus("dispenserRecipe"),
         featureStatus("entityUnloadProtection"),
         featureStatus("vanillaRedstone"),
         featureStatus("anvilNameColor"),
         featureStatus("debugLogging"),
         featureStatus("punishmentTemplates"),
         featureStatus("announcement"),
         featureStatus("chatHistory"),
         featureStatus("memberGlobalMention"),
         featureStatus("modNetworkChecks"),
         featureStatus("mentionActionBar"),
         featureStatus("joinMessage"),
         featureStatus("joinSound"),
         featureStatus("banChatAnnouncement"),
         featureStatus("grimAnticheat"),
         featureStatus("grimSafeWalkExempt"),
         featureStatus("voting"),
         featureStatus("mute")
      );
      ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("功能开关：" + status), false);
      return 1;
   }

   private static int showBanChatAnnouncementStatus(CommandContext<ServerCommandSource> commandContext) {
      String statusText = "封禁聊天公告："
         + (GreenManServerConfig.isPunishmentChatBanAnnouncementEnabled() ? "开启" : "关闭")
         + "，模板："
         + GreenManServerConfig.getPunishmentChatBanAnnouncementTemplate();
      ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal(statusText), false);
      return 1;
   }

   private static int setBanChatAnnouncementTemplate(CommandContext<ServerCommandSource> commandContext) {
      String templateText = StringArgumentType.getString(commandContext, "text");
      if (!GreenManServerConfig.setPunishmentChatBanAnnouncementTemplate(templateText)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("封禁聊天公告模板不能为空或保存失败"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(
               () -> Text.literal("封禁聊天公告模板设置成功，当前模式：" + (GreenManServerConfig.isPunishmentChatBanAnnouncementEnabled() ? "开启" : "关闭")), true
            );
         return 1;
      }
   }

   private static int setFeatureEnabledDirect(CommandContext<ServerCommandSource> commandContext, String featureName, boolean enabled) {
      if (!GreenManServerConfig.setFeatureEnabled(featureName, enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         String featureDisplayName = "itemClear".equals(featureName) ? "掉落物清除" : "封禁聊天公告";
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal(featureDisplayName + "已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int showJoinStatus(CommandContext<ServerCommandSource> commandContext) {
      String statusText = "进服提示="
         + (GreenManServerConfig.isFeatureEnabled("joinMessage") ? "开启" : "关闭")
         + "，个人="
         + (GreenManServerConfig.isJoinMessagePersonalEnabled() ? "开启" : "关闭")
         + "，全服="
         + (GreenManServerConfig.isJoinMessageBroadcastEnabled() ? "开启" : "关闭")
         + "，白名单人数="
         + GreenManServerConfig.getJoinMessageAllowedPlayerUuids().size()
         + "，音效="
         + (GreenManServerConfig.isFeatureEnabled("joinSound") ? GreenManServerConfig.getJoinSoundMode() : "关闭")
         + "，音效接收范围="
         + (GreenManServerConfig.isJoinSoundBroadcastEnabled() ? "全服" : "仅触发玩家")
         + "，音效延迟="
         + GreenManServerConfig.getJoinSoundDelaySeconds()
         + "秒，原版音效="
         + String.join("、", GreenManServerConfig.getJoinVanillaSoundIds())
         + "，内部资源包地址="
         + (GreenManServerConfig.getResolvedJoinMusicResourcePackUrl().isEmpty() ? "未配置" : GreenManServerConfig.getResolvedJoinMusicResourcePackUrl());
      ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal(statusText), false);
      return 1;
   }

   private static int showJoinSoundDelay(CommandContext<ServerCommandSource> commandContext) {
      ((ServerCommandSource)commandContext.getSource())
         .sendFeedback(() -> Text.literal("进服音效延迟当前为 " + GreenManServerConfig.getJoinSoundDelaySeconds() + " 秒（允许0至60秒）"), false);
      return 1;
   }

   private static int setJoinSoundDelay(CommandContext<ServerCommandSource> commandContext) {
      int delaySeconds = IntegerArgumentType.getInteger(commandContext, "seconds");
      if (!GreenManServerConfig.setJoinSoundDelaySeconds(delaySeconds)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("进服音效延迟设置失败，允许范围为0至60秒，请检查配置目录权限"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("进服音效延迟已设为 " + delaySeconds + " 秒"), true);
         return 1;
      }
   }

   private static int updateJoinAllowedPlayers(CommandContext<ServerCommandSource> commandContext, boolean allowed) throws CommandSyntaxException {
      Collection<PlayerConfigEntry> targetProfiles = GameProfileArgumentType.getProfileArgument(commandContext, "player");
      if (targetProfiles != null && !targetProfiles.isEmpty()) {
         List<UUID> targetPlayerUuids = targetProfiles.stream().<UUID>map(PlayerConfigEntry::id).toList();
         if (!GreenManServerConfig.updateJoinMessageAllowedPlayers(targetPlayerUuids, allowed)) {
            return sendSaveFailure(commandContext);
         } else {
            boolean welcomeTemplateSyncSucceeded = allowed
               ? GreenManPlayerWelcomeConfig.enablePlayers(targetPlayerUuids)
               : targetPlayerUuids.stream().allMatch(GreenManPlayerWelcomeConfig::disablePlayer);
            if (!welcomeTemplateSyncSucceeded) {
               ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("欢迎名单已更新，但玩家专属欢迎配置保存失败，请检查config目录权限"));
               return 0;
            } else {
               String targetNames = targetProfiles.stream().<CharSequence>map(PlayerConfigEntry::name).sorted().collect(Collectors.joining("、"));
               ((ServerCommandSource)commandContext.getSource())
                  .sendFeedback(() -> Text.literal("已" + (allowed ? "允许 " : "禁止 ") + targetNames + " 触发个人进服欢迎和音效"), true);
               return targetProfiles.size();
            }
         }
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("没有找到可修改的玩家"));
         return 0;
      }
   }

   private static int showJoinAllowedPlayers(CommandContext<ServerCommandSource> commandContext) {
      List<String> allowedPlayerUuids = GreenManServerConfig.getJoinMessageAllowedPlayerUuids();
      if (allowedPlayerUuids.isEmpty()) {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("进服欢迎白名单为空，当前没有玩家会触发欢迎或音效"), false);
         return 1;
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("进服欢迎白名单（UUID）：" + String.join("、", allowedPlayerUuids)), false);
         return allowedPlayerUuids.size();
      }
   }

   private static int setJoinChannels(CommandContext<ServerCommandSource> commandContext, boolean personalEnabled, boolean broadcastEnabled) {
      if (!GreenManServerConfig.setJoinMessageChannels(personalEnabled, broadcastEnabled)) {
         return sendSaveFailure(commandContext);
      } else {
         String currentMode = personalEnabled && broadcastEnabled ? "个人欢迎和全服广播" : (personalEnabled ? "仅个人欢迎" : (broadcastEnabled ? "仅全服广播" : "全部关闭"));
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("进服提示通道设置成功，当前模式：" + currentMode), true);
         return 1;
      }
   }

   private static int setJoinSound(CommandContext<ServerCommandSource> commandContext, String soundMode, String soundValue) {
      if (!GreenManServerConfig.setJoinSound(soundMode, soundValue)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("进服音效设置失败，请检查模式、声音ID或GreenManMusic文件名"));
         return 0;
      } else {
         ((ServerCommandSource)commandContext.getSource())
            .sendFeedback(() -> Text.literal("进服音效设置成功，当前模式：" + soundMode + "；多个原版音效可在配置 joinVanillaSoundIds 中用数组设置"), true);
         return 1;
      }
   }

   private static int setJoinSoundBroadcast(CommandContext<ServerCommandSource> commandContext, boolean broadcastEnabled) {
      if (!GreenManServerConfig.setJoinSoundBroadcastEnabled(broadcastEnabled)) {
         return sendSaveFailure(commandContext);
      } else {
         String currentMode = broadcastEnabled ? "全服播放" : "仅触发玩家播放";
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("进服音效接收范围设置成功，当前模式：" + currentMode), true);
         return 1;
      }
   }

   private static int setFeatureFlag(CommandContext<ServerCommandSource> commandContext, String featureName, boolean enabled) {
      if (!GreenManServerConfig.setFeatureEnabled(featureName, enabled)) {
         return sendSaveFailure(commandContext);
      } else {
         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("进服提示已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static int setFeatureEnabled(CommandContext<ServerCommandSource> commandContext, boolean enabled) {
      String featureName = StringArgumentType.getString(commandContext, "feature");
      if (!GreenManServerConfig.setFeatureEnabled(featureName, enabled)) {
         ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("未知功能或保存失败，可用功能：" + GreenManServerConfig.getFeatureNamesForDisplay()));
         return 0;
      } else {
         GreenManServer.refreshAllPlayerTabNames(((ServerCommandSource)commandContext.getSource()).getServer());
         if ("mentions".equals(featureName) || "memberGlobalMention".equals(featureName)) {
            GreenManMentionService.refreshAllCompletions(((ServerCommandSource)commandContext.getSource()).getServer());
         }

         if ("chatHistory".equals(featureName) && !enabled) {
            GreenManChatHistory.clearHistory();
         }

         if ("modNetworkChecks".equals(featureName)) {
            GreenManNetworkCheckBlocker.applyRuntimeConfig();
         }

         ((ServerCommandSource)commandContext.getSource()).sendFeedback(() -> Text.literal("功能 " + featureName + " 已" + (enabled ? "开启" : "关闭")), true);
         return 1;
      }
   }

   private static String featureStatus(String featureName) {
      return GreenManServerConfig.getFeatureDescription(featureName)
         + "("
         + featureName
         + ")="
         + (GreenManServerConfig.isFeatureEnabled(featureName) ? "开启" : "关闭");
   }

   private static int sendSaveFailure(CommandContext<ServerCommandSource> commandContext) {
      ((ServerCommandSource)commandContext.getSource()).sendError(Text.literal("设置保存失败，请检查配置目录权限"));
      return 0;
   }

   private static long formatMebibytes(long bytes) {
      return Math.max(0L, bytes) / 1048576L;
   }
}
