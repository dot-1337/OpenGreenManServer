package shit.shmily.mixin;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {AbstractBlockState.class},
   priority = 1
)
public abstract class VanillaRedstoneSignalGuardMixin {
   @Unique
   private static final Map<String, Long> GREENMANSERVER_LAST_REDSTONE_DEBUG_NANOS = new ConcurrentHashMap<>();
   @Unique
   private static final long GREENMANSERVER_REDSTONE_DEBUG_INTERVAL_NANOS = 60000000000L;

   @Inject(
      method = {"getWeakRedstonePower"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void greenmanserver$clampVanillaSignal(
      BlockView blockGetter, BlockPos blockPos, Direction direction, CallbackInfoReturnable<Integer> callbackInfo
   ) {
      this.greenmanserver$clampVanillaOutput(blockPos, "普通信号", callbackInfo);
   }

   @Inject(
      method = {"getStrongRedstonePower"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void greenmanserver$clampVanillaDirectSignal(
      BlockView blockGetter, BlockPos blockPos, Direction direction, CallbackInfoReturnable<Integer> callbackInfo
   ) {
      this.greenmanserver$clampVanillaOutput(blockPos, "强充能信号", callbackInfo);
   }

   @Inject(
      method = {"getComparatorOutput"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void greenmanserver$clampVanillaAnalogSignal(
      World level, BlockPos blockPos, Direction direction, CallbackInfoReturnable<Integer> callbackInfo
   ) {
      this.greenmanserver$clampVanillaOutput(blockPos, "比较器信号", callbackInfo);
   }

   private void greenmanserver$clampVanillaOutput(BlockPos blockPos, String signalType, CallbackInfoReturnable<Integer> callbackInfo) {
      if (GreenManServerConfig.isFeatureEnabled("vanillaRedstone")) {
         BlockState blockState = (BlockState)(Object)this;
         Block block = blockState.getBlock();
         Identifier blockIdentifier = Registries.BLOCK.getId(block);
         if (blockIdentifier != null && "minecraft".equals(blockIdentifier.getNamespace())) {
            int calculatedSignal = (Integer)callbackInfo.getReturnValue();
            int clampedSignal = Math.max(0, Math.min(15, calculatedSignal));
            if (clampedSignal != calculatedSignal) {
               if (GreenManServerConfig.isFeatureEnabled("debugLogging") && greenmanserver$shouldWriteDebugLog(blockIdentifier + ":" + signalType)) {
                  GreenManServer.LOGGER
                     .info(
                        "[debug] 拦截原版红石修改：方块={}，位置={}，类型={}，原始值={}，修正值={}",
                        new Object[]{blockIdentifier, blockPos, signalType, calculatedSignal, clampedSignal}
                     );
               }

               callbackInfo.setReturnValue(clampedSignal);
            }
         }
      }
   }

   @Unique
   private static boolean greenmanserver$shouldWriteDebugLog(String debugKey) {
      if (debugKey != null && !debugKey.isEmpty()) {
         long currentTimeNanos = System.nanoTime();
         Long previousLogNanos = GREENMANSERVER_LAST_REDSTONE_DEBUG_NANOS.get(debugKey);
         if (previousLogNanos != null && currentTimeNanos - previousLogNanos < 60000000000L) {
            return false;
         } else {
            if (GREENMANSERVER_LAST_REDSTONE_DEBUG_NANOS.size() >= 256) {
               GREENMANSERVER_LAST_REDSTONE_DEBUG_NANOS.clear();
            }

            GREENMANSERVER_LAST_REDSTONE_DEBUG_NANOS.put(debugKey, currentTimeNanos);
            return true;
         }
      } else {
         return false;
      }
   }
}
