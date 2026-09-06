package shit.shmily.mixin;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TntBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.rule.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {TntBlock.class},
   priority = 2000
)
public abstract class VanillaTntBlockMixin extends Block {
   protected VanillaTntBlockMixin(Settings properties) {
      super(properties);
   }

   @Inject(
      method = {"onBlockAdded"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreTntPlacement(
      BlockState blockState, World level, BlockPos blockPos, BlockState previousBlockState, boolean movedByPiston, CallbackInfo callbackInfo
   ) {
      if (greenmanserver$isProtectionEnabled()) {
         if (previousBlockState.isOf(blockState.getBlock())) {
            callbackInfo.cancel();
         } else {
            if (level.isReceivingRedstonePower(blockPos) && greenmanserver$primeTnt(level, blockPos, null)) {
               level.removeBlock(blockPos, false);
            }

            callbackInfo.cancel();
         }
      }
   }

   @Inject(
      method = {"neighborUpdate"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreTntNeighborSignal(
      BlockState blockState,
      World level,
      BlockPos blockPos,
      Block changedBlock,
      WireOrientation orientation,
      boolean movedByPiston,
      CallbackInfo callbackInfo
   ) {
      if (greenmanserver$isProtectionEnabled()) {
         if (level.isReceivingRedstonePower(blockPos) && greenmanserver$primeTnt(level, blockPos, null)) {
            level.removeBlock(blockPos, false);
         }

         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"onDestroyedByExplosion"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreTntChainReaction(ServerWorld serverLevel, BlockPos blockPos, Explosion explosion, CallbackInfo callbackInfo) {
      if (greenmanserver$isProtectionEnabled()) {
         if (!(Boolean)serverLevel.getGameRules().getValue(GameRules.TNT_EXPLODES)) {
            callbackInfo.cancel();
         } else {
            LivingEntity explosionOwner = explosion.getCausingEntity();
            TntEntity primedTnt = new TntEntity(
               serverLevel, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, explosionOwner
            );
            primedTnt.setFuse(serverLevel.random.nextInt(20) + 10);
            serverLevel.spawnEntity(primedTnt);
            callbackInfo.cancel();
         }
      }
   }

   @Inject(
      method = {"onBreak"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreUnstableTntBreak(
      World level, BlockPos blockPos, BlockState blockState, PlayerEntity player, CallbackInfoReturnable<BlockState> callbackInfo
   ) {
      if (greenmanserver$isProtectionEnabled()) {
         if (!level.isClient() && !player.getAbilities().creativeMode && (Boolean)blockState.get(TntBlock.UNSTABLE)) {
            greenmanserver$primeTnt(level, blockPos, player);
         }

         BlockState resultingState = super.onBreak(level, blockPos, blockState, player);
         callbackInfo.setReturnValue(resultingState);
      }
   }

   @Inject(
      method = {"onUseWithItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreTntItemIgnition(
      ItemStack itemStack,
      BlockState blockState,
      World level,
      BlockPos blockPos,
      PlayerEntity player,
      Hand interactionHand,
      BlockHitResult blockHitResult,
      CallbackInfoReturnable<ActionResult> callbackInfo
   ) {
      if (greenmanserver$isProtectionEnabled()) {
         boolean isIgnitionItem = itemStack.isOf(Items.FLINT_AND_STEEL) || itemStack.isOf(Items.FIRE_CHARGE);
         if (isIgnitionItem) {
            boolean primedSuccessfully = greenmanserver$primeTnt(level, blockPos, player);
            if (primedSuccessfully) {
               level.setBlockState(blockPos, Blocks.AIR.getDefaultState(), 11);
               Item ignitionItem = itemStack.getItem();
               if (itemStack.isOf(Items.FLINT_AND_STEEL)) {
                  itemStack.damage(1, player, interactionHand.getEquipmentSlot());
               } else {
                  itemStack.decrementUnlessCreative(1, player);
               }

               player.incrementStat(Stats.USED.getOrCreateStat(ignitionItem));
               callbackInfo.setReturnValue(ActionResult.SUCCESS);
            } else if (level instanceof ServerWorld serverLevel && !(Boolean)serverLevel.getGameRules().getValue(GameRules.TNT_EXPLODES)) {
               player.sendMessage(Text.translatable("block.minecraft.tnt.disabled"), true);
               callbackInfo.setReturnValue(ActionResult.PASS);
            } else {
               callbackInfo.setReturnValue(ActionResult.SUCCESS);
            }
         }
      }
   }

   @Inject(
      method = {"onProjectileHit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreTntProjectileIgnition(
      World level, BlockState blockState, BlockHitResult blockHitResult, ProjectileEntity projectile, CallbackInfo callbackInfo
   ) {
      if (greenmanserver$isProtectionEnabled()) {
         if (level instanceof ServerWorld serverLevel) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            Entity projectileOwner = projectile.getOwner();
            if (projectile.isOnFire()
               && projectile.canModifyAt(serverLevel, blockPos)
               && greenmanserver$primeTnt(level, blockPos, projectileOwner instanceof LivingEntity livingOwner ? livingOwner : null)) {
               level.removeBlock(blockPos, false);
            }
         }

         callbackInfo.cancel();
      }
   }

   @Inject(
      method = {"primeTnt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void greenmanserver$restorePublicPrime(World level, BlockPos blockPos, CallbackInfoReturnable<Boolean> callbackInfo) {
      if (greenmanserver$isProtectionEnabled()) {
         callbackInfo.setReturnValue(greenmanserver$primeTnt(level, blockPos, null));
      }
   }

   @Inject(
      method = {"shouldDropItemsOnExplosion"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$restoreTntExplosionDrop(Explosion explosion, CallbackInfoReturnable<Boolean> callbackInfo) {
      if (greenmanserver$isProtectionEnabled()) {
         callbackInfo.setReturnValue(false);
      }
   }

   private static boolean greenmanserver$primeTnt(World level, BlockPos blockPos, LivingEntity owner) {
      if (level instanceof ServerWorld serverLevel && (Boolean)serverLevel.getGameRules().getValue(GameRules.TNT_EXPLODES)) {
         TntEntity primedTnt = new TntEntity(level, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, owner);
         level.spawnEntity(primedTnt);
         level.playSound(
            null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F
         );
         level.emitGameEvent(owner, GameEvent.PRIME_FUSE, blockPos);
         if (GreenManServerConfig.isFeatureEnabled("debugLogging")) {
            GreenManServer.LOGGER.info("[debug] 拦截并恢复原版 TNT 触发：位置={}，触发者类={}", blockPos, owner == null ? "无" : owner.getClass().getName());
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean greenmanserver$isProtectionEnabled() {
      return GreenManServerConfig.isFeatureEnabled("vanillaRedstone");
   }
}
