package shit.shmily.mixin;

import shit.shmily.config.GreenManServerConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ShapedRecipe.class})
public abstract class DispenserRecipeMixin {
   @Accessor("result")
   protected abstract ItemStack greenmanserver$getRecipeResult();

   @Inject(
      method = {"matches(Lnet/minecraft/recipe/input/CraftingRecipeInput;Lnet/minecraft/world/World;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void greenmanserver$allowDamagedBow(CraftingRecipeInput craftingInput, World level, CallbackInfoReturnable<Boolean> callbackInfo) {
      ItemStack recipeResult = this.greenmanserver$getRecipeResult();
      if (recipeResult != null && !recipeResult.isEmpty() && recipeResult.isOf(Items.DISPENSER)) {
         if (craftingInput != null && craftingInput.getWidth() == 3 && craftingInput.getHeight() == 3) {
            ItemStack centerBowStack = craftingInput.getStackInSlot(1, 1);
            if (!GreenManServerConfig.isFeatureEnabled("dispenserRecipe")) {
               if (centerBowStack.isOf(Items.BOW) && centerBowStack.isDamaged()) {
                  callbackInfo.setReturnValue(false);
               }
            } else {
               for (int row = 0; row < 3; row++) {
                  for (int column = 0; column < 3; column++) {
                     ItemStack ingredientStack = craftingInput.getStackInSlot(column, row);
                     boolean centerSlot = row == 1 && column == 1;
                     boolean redstoneSlot = row == 2 && column == 1;
                     boolean validStack = centerSlot
                        ? ingredientStack.isOf(Items.BOW)
                        : (redstoneSlot ? ingredientStack.isOf(Items.REDSTONE) : ingredientStack.isOf(Items.COBBLESTONE));
                     if (!validStack) {
                        return;
                     }
                  }
               }

               callbackInfo.setReturnValue(true);
            }
         }
      }
   }
}
