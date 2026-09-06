package shit.shmily.mixin;

import shit.shmily.GreenManServer;
import shit.shmily.config.GreenManServerConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.world.storage.ChunkDataAccess;
import net.minecraft.world.storage.ChunkDataList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {ServerEntityManager.class},
   priority = 2000
)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityLike> {
   @Unique
   private static final Map<Long, Long> GREENMANSERVER_LAST_ENTITY_DEBUG_NANOS = new ConcurrentHashMap<>();
   @Unique
   private static final long GREENMANSERVER_ENTITY_DEBUG_INTERVAL_NANOS = 60000000000L;
   @Shadow
   @Final
   SectionedEntityCache<T> cache;
   @Shadow
   @Final
   ChunkDataAccess<T> dataAccess;
   @Shadow
   @Final
   private Long2ObjectMap<?> managedStatuses;

   @Inject(
      method = {"unload(J)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$keepImportantEntities(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
      if (GreenManServerConfig.isFeatureEnabled("entityUnloadProtection")) {
         if (this.managedStatuses.get(chunkPos) instanceof Enum<?> statusEnum && "LOADED".equals(statusEnum.name())) {
            List<T> protectedEntities = this.cache
               .getTrackingSections(chunkPos)
               .<T>flatMap(EntityTrackingSection::stream)
               .filter(PersistentEntitySectionManagerMixin::isProtectedEntity)
               .toList();
            if (!protectedEntities.isEmpty()) {
               List<T> entities = this.cache.getTrackingSections(chunkPos).<T>flatMap(EntityTrackingSection::stream).filter(EntityLike::shouldSave).toList();
               this.dataAccess.writeChunkData(new ChunkDataList(new ChunkPos(chunkPos), entities));
               if (GreenManServerConfig.isFeatureEnabled("debugLogging") && greenmanserver$shouldWriteDebugLog(chunkPos)) {
                  for (T protectedEntity : protectedEntities) {
                     GreenManServer.LOGGER
                        .info(
                           "[debug] 拦截实体区块卸载：区块={}，实体类={}，来源命名空间={}",
                           new Object[]{new ChunkPos(chunkPos), protectedEntity.getClass().getName(), greenmanserver$getSourceNamespace(protectedEntity)}
                        );
                  }
               }

               cir.setReturnValue(true);
            }
         }
      }
   }

   private static boolean isProtectedEntity(EntityLike entityAccess) {
      return !(entityAccess instanceof Entity actualEntity)
         ? false
         : actualEntity instanceof LivingEntity || actualEntity instanceof AbstractBoatEntity || actualEntity instanceof AbstractMinecartEntity;
   }

   private static String greenmanserver$getSourceNamespace(EntityLike entityAccess) {
      if (entityAccess instanceof Entity actualEntity) {
         return actualEntity.getType().getRegistryEntry().registryKey().getValue().getNamespace();
      } else {
         return entityAccess == null ? "unknown" : entityAccess.getClass().getPackageName();
      }
   }

   @Unique
   private static boolean greenmanserver$shouldWriteDebugLog(long chunkPos) {
      long currentTimeNanos = System.nanoTime();
      Long previousLogNanos = GREENMANSERVER_LAST_ENTITY_DEBUG_NANOS.get(chunkPos);
      if (previousLogNanos != null && currentTimeNanos - previousLogNanos < 60000000000L) {
         return false;
      } else {
         if (GREENMANSERVER_LAST_ENTITY_DEBUG_NANOS.size() >= 4096) {
            GREENMANSERVER_LAST_ENTITY_DEBUG_NANOS.clear();
         }

         GREENMANSERVER_LAST_ENTITY_DEBUG_NANOS.put(chunkPos, currentTimeNanos);
         return true;
      }
   }
}
