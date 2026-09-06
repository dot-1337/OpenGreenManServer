package shit.shmily.anticheat;

import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class GreenManVanillaAnticheatController {
   private static final Set<String> GRIM_MOD_IDS = Set.of("grimac", "grimac-fabric-intermediary", "grimac-fabric-official");
   private static final boolean GRIM_LOADED = detectGrimLoaded();

   private GreenManVanillaAnticheatController() {
   }

   public static boolean isGrimLoaded() {
      return GRIM_LOADED;
   }

   private static boolean detectGrimLoaded() {
      FabricLoader fabricLoader = FabricLoader.getInstance();

      for (String grimModId : GRIM_MOD_IDS) {
         if (fabricLoader.isModLoaded(grimModId)) {
            return true;
         }
      }

      return false;
   }
}
