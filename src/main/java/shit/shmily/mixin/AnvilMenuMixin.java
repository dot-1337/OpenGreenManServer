package shit.shmily.mixin;

import shit.shmily.config.GreenManServerConfig;
import shit.shmily.text.GreenManTextFormatter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AnvilScreenHandler.class})
public abstract class AnvilMenuMixin extends ForgingScreenHandler {
   @Shadow
   private String newItemName;

   protected AnvilMenuMixin() {
      super(null, 0, null, null, null);
   }

   @Inject(
      method = {"updateResult"},
      at = {@At("RETURN")}
   )
   private void greenmanserver$applyColoredName(CallbackInfo callbackInfo) {
      if (GreenManServerConfig.isFeatureEnabled("anvilNameColor") && this.newItemName != null && !this.newItemName.isEmpty()) {
         ItemStack resultStack = this.output.getStack(0);
         if (!resultStack.isEmpty() && this.newItemName.indexOf(38) >= 0) {
            Text coloredName = GreenManTextFormatter.parseRainbow(this.newItemName, Style.EMPTY);
            if (!coloredName.getString().isEmpty()) {
               resultStack.set(DataComponentTypes.CUSTOM_NAME, coloredName);
               this.output.setStack(0, resultStack);
            }
         }
      }
   }
}
