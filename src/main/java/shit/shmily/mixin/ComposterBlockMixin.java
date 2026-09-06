package shit.shmily.mixin;

import shit.shmily.config.GreenManServerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ComposterBlock.class})
public abstract class ComposterBlockMixin {
   @Inject(
      method = {"onUseWithItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void continueFillingWhenFull(
      ItemStack itemStack,
      BlockState blockState,
      World level,
      BlockPos blockPos,
      PlayerEntity player,
      Hand interactionHand,
      BlockHitResult blockHitResult,
      CallbackInfoReturnable<ActionResult> callbackInfo
   ) {
      if (GreenManServerConfig.isFeatureEnabled("composter")) {
         if (level instanceof ServerWorld serverLevel
            && (Integer)blockState.get(ComposterBlock.LEVEL) >= 7
            && ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.containsKey(itemStack.getItem())) {
            player.getInventory().offerOrDrop(new ItemStack(Items.BONE_MEAL));
            BlockState emptyState = (BlockState)blockState.with(ComposterBlock.LEVEL, 0);
            serverLevel.setBlockState(blockPos, emptyState, 3);
            int originalItemCount = itemStack.getCount();
            BlockState filledState = ComposterBlock.compost(player, emptyState, serverLevel, itemStack, blockPos);
            if (player.getAbilities().creativeMode) {
               itemStack.setCount(originalItemCount);
            }

            serverLevel.syncWorldEvent(1500, blockPos, filledState == emptyState ? 0 : 1);
            player.incrementStat(Stats.USED.getOrCreateStat(itemStack.getItem()));
            callbackInfo.setReturnValue(ActionResult.SUCCESS);
         }
      }
   }
}
