package shit.shmily.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GreenManTextFormatter {
   private GreenManTextFormatter() {
   }

   public static boolean containsColorSymbol(String rawText) {
      return rawText != null && !rawText.isEmpty() ? rawText.indexOf(38) >= 0 : false;
   }

   public static Text parse(String rawText, Style defaultStyle) {
      if (rawText != null && !rawText.isEmpty()) {
         Style safeDefaultStyle = defaultStyle == null ? Style.EMPTY : defaultStyle;
         MutableText resultComponent = Text.empty();
         Style currentStyle = safeDefaultStyle;
         StringBuilder pendingText = new StringBuilder();

         for (int characterIndex = 0; characterIndex < rawText.length(); characterIndex++) {
            char currentCharacter = rawText.charAt(characterIndex);
            if (currentCharacter == '&' && characterIndex + 1 < rawText.length()) {
               if (rawText.charAt(characterIndex + 1) == '#' && characterIndex + 7 < rawText.length()) {
                  String hexColorText = rawText.substring(characterIndex + 2, characterIndex + 8);
                  Integer rgbColor = parseHexColor(hexColorText);
                  if (rgbColor != null) {
                     appendStyledText(resultComponent, pendingText, currentStyle);
                     currentStyle = currentStyle.withColor(rgbColor);
                     characterIndex += 7;
                     continue;
                  }
               }

               Formatting legacyFormatting = Formatting.byCode(Character.toLowerCase(rawText.charAt(characterIndex + 1)));
               if (legacyFormatting != null) {
                  appendStyledText(resultComponent, pendingText, currentStyle);
                  currentStyle = legacyFormatting == Formatting.RESET ? safeDefaultStyle : currentStyle.withExclusiveFormatting(legacyFormatting);
                  characterIndex++;
               } else {
                  pendingText.append(currentCharacter);
               }
            } else {
               pendingText.append(currentCharacter);
            }
         }

         appendStyledText(resultComponent, pendingText, currentStyle);
         return resultComponent;
      } else {
         return Text.empty();
      }
   }

   public static Text parseRainbow(String rawText, Style defaultStyle) {
      if (rawText != null && !rawText.isEmpty()) {
         if (!rawText.contains("&u")) {
            return parse(rawText, defaultStyle);
         } else {
            Style safeDefaultStyle = defaultStyle == null ? Style.EMPTY : defaultStyle;
            MutableText rainbowComponent = Text.empty();
            Formatting[] rainbowColors = new Formatting[]{
               Formatting.RED, Formatting.GOLD, Formatting.YELLOW, Formatting.GREEN, Formatting.AQUA, Formatting.LIGHT_PURPLE
            };
            Style currentStyle = safeDefaultStyle;
            StringBuilder pendingText = new StringBuilder();
            boolean rainbowModeEnabled = false;
            int colorIndex = 0;

            for (int characterIndex = 0; characterIndex < rawText.length(); characterIndex++) {
               char currentCharacter = rawText.charAt(characterIndex);
               if (currentCharacter == '&' && characterIndex + 1 < rawText.length()) {
                  if (rawText.charAt(characterIndex + 1) == 'u') {
                     appendStyledText(rainbowComponent, pendingText, currentStyle);
                     rainbowModeEnabled = true;
                     characterIndex++;
                  } else {
                     if (rawText.charAt(characterIndex + 1) == '#' && characterIndex + 7 < rawText.length()) {
                        Integer rgbColor = parseHexColor(rawText.substring(characterIndex + 2, characterIndex + 8));
                        if (rgbColor != null) {
                           appendStyledText(rainbowComponent, pendingText, currentStyle);
                           currentStyle = currentStyle.withColor(rgbColor);
                           characterIndex += 7;
                           continue;
                        }
                     }

                     Formatting legacyFormatting = Formatting.byCode(Character.toLowerCase(rawText.charAt(characterIndex + 1)));
                     if (legacyFormatting != null) {
                        appendStyledText(rainbowComponent, pendingText, currentStyle);
                        currentStyle = legacyFormatting == Formatting.RESET ? safeDefaultStyle : currentStyle.withExclusiveFormatting(legacyFormatting);
                        characterIndex++;
                     } else {
                        pendingText.append(currentCharacter);
                     }
                  }
               } else if (rainbowModeEnabled && currentCharacter != '\n' && currentCharacter != '\r') {
                  appendStyledText(rainbowComponent, pendingText, currentStyle.withColor(rainbowColors[colorIndex % rainbowColors.length]));
                  pendingText.append(currentCharacter);
                  appendStyledText(rainbowComponent, pendingText, currentStyle.withColor(rainbowColors[colorIndex % rainbowColors.length]));
                  colorIndex++;
               } else {
                  pendingText.append(currentCharacter);
               }
            }

            appendStyledText(rainbowComponent, pendingText, currentStyle);
            return rainbowComponent;
         }
      } else {
         return Text.empty();
      }
   }

   private static Integer parseHexColor(String hexColorText) {
      if (hexColorText != null && hexColorText.length() == 6) {
         try {
            return Integer.parseInt(hexColorText, 16) & 16777215;
         } catch (NumberFormatException var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void appendStyledText(MutableText resultComponent, StringBuilder pendingText, Style currentStyle) {
      if (pendingText.length() != 0) {
         resultComponent.append(Text.literal(pendingText.toString()).fillStyle(currentStyle));
         pendingText.setLength(0);
      }
   }
}
