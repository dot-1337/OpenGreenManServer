package shit.shmily.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import shit.shmily.config.GreenManServerConfig;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.Proxy.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GreenManNetworkCheckBlocker implements PreLaunchEntrypoint {
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("greenmanserver.json");
   private static final Gson GSON = new Gson();
   private static final Logger LOGGER = LoggerFactory.getLogger("greenmanserver-network");
   private static final boolean DEFAULT_BLOCKING_ENABLED = true;
   private static final Proxy REJECTING_LOCAL_PROXY = new Proxy(Type.HTTP, new InetSocketAddress("127.0.0.1", 1));
   private static final List<Proxy> REJECTING_PROXY_LIST = List.of(REJECTING_LOCAL_PROXY);
   private static final List<String> REQUIRED_MINECRAFT_HOSTS = List.of(
      "sessionserver.mojang.com",
      "authserver.mojang.com",
      "api.mojang.com",
      "api.minecraftservices.com",
      "textures.minecraft.net",
      "login.microsoftonline.com",
      "user.auth.xboxlive.com",
      "xsts.auth.xboxlive.com",
      "resources.download.minecraft.net"
   );
   private static final List<String> BLOCKED_NETWORK_CHECK_HOSTS = List.of(
      "api.modrinth.com", "api.curseforge.com", "api.github.com", "bstats.org", "sentry.io", "google-analytics.com", "api.segment.io"
   );
   private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
   private static final Set<String> LOGGED_BLOCKED_HOSTS = ConcurrentHashMap.newKeySet();
   private static volatile ProxySelector delegatedProxySelector;
   private static volatile boolean blockingEnabled = true;

   public void onPreLaunch() {
      blockingEnabled = readEarlyBlockingSetting();
      install();
   }

   public static void applyRuntimeConfig() {
      blockingEnabled = GreenManServerConfig.isFeatureEnabled("modNetworkChecks");
      install();
      if (!blockingEnabled) {
         LOGGED_BLOCKED_HOSTS.clear();
      }
   }

   private static void install() {
      if (INSTALLED.compareAndSet(false, true)) {
         delegatedProxySelector = ProxySelector.getDefault();

         try {
            ProxySelector.setDefault(new GreenManNetworkCheckBlocker.SelectiveNetworkCheckProxySelector());
            LOGGER.info("模组更新检查与遥测网络拦截已安装，当前状态：{}", blockingEnabled ? "开启" : "关闭");
         } catch (SecurityException var1) {
            INSTALLED.set(false);
            LOGGER.error("JVM 安全策略禁止安装模组网络检查拦截器，服务器将继续启动", var1);
         }
      }
   }

   private static boolean readEarlyBlockingSetting() {
      if (Files.notExists(CONFIG_PATH)) {
         return true;
      } else {
         try {
            boolean var2;
            try (Reader configReader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
               JsonObject configObject = (JsonObject)GSON.fromJson(configReader, JsonObject.class);
               if (configObject != null && configObject.has("disableModNetworkChecks") && configObject.get("disableModNetworkChecks").isJsonPrimitive()) {
                  return configObject.get("disableModNetworkChecks").getAsBoolean();
               }

               var2 = true;
            }

            return var2;
         } catch (RuntimeException | IOException var5) {
            LOGGER.warn("无法在启动早期读取模组网络检查开关，将使用默认开启状态", var5);
            return true;
         }
      }
   }

   private static boolean shouldBlock(URI targetUri) {
      if (blockingEnabled && targetUri != null) {
         String targetScheme = targetUri.getScheme();
         if (targetScheme != null && ("http".equalsIgnoreCase(targetScheme) || "https".equalsIgnoreCase(targetScheme))) {
            String targetHost = normalizeHost(targetUri.getHost());
            if (targetHost.isEmpty()) {
               return false;
            } else {
               return matchesAnyHostRule(targetHost, REQUIRED_MINECRAFT_HOSTS) ? false : matchesAnyHostRule(targetHost, BLOCKED_NETWORK_CHECK_HOSTS);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static String normalizeHost(String rawHost) {
      if (rawHost != null && !rawHost.isBlank()) {
         String normalizedHost = rawHost.trim().toLowerCase(Locale.ROOT);

         while (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
         }

         return normalizedHost;
      } else {
         return "";
      }
   }

   private static boolean matchesAnyHostRule(String targetHost, List<String> hostRules) {
      if (targetHost != null && !targetHost.isEmpty() && hostRules != null && !hostRules.isEmpty()) {
         for (String hostRule : hostRules) {
            if (hostRule != null && !hostRule.isEmpty() && (targetHost.equals(hostRule) || targetHost.endsWith("." + hostRule))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static final class SelectiveNetworkCheckProxySelector extends ProxySelector {
      @Override
      public List<Proxy> select(URI targetUri) {
         if (GreenManNetworkCheckBlocker.shouldBlock(targetUri)) {
            String blockedHost = GreenManNetworkCheckBlocker.normalizeHost(targetUri.getHost());
            if (GreenManNetworkCheckBlocker.LOGGED_BLOCKED_HOSTS.add(blockedHost)) {
               GreenManNetworkCheckBlocker.LOGGER.info("已拦截模组更新检查或遥测主机：{}", blockedHost);
            }

            return GreenManNetworkCheckBlocker.REJECTING_PROXY_LIST;
         } else {
            ProxySelector activeDelegate = GreenManNetworkCheckBlocker.delegatedProxySelector;
            return activeDelegate == null ? List.of(Proxy.NO_PROXY) : activeDelegate.select(targetUri);
         }
      }

      @Override
      public void connectFailed(URI targetUri, SocketAddress proxyAddress, IOException failureCause) {
         if (!GreenManNetworkCheckBlocker.shouldBlock(targetUri) || !GreenManNetworkCheckBlocker.REJECTING_LOCAL_PROXY.address().equals(proxyAddress)) {
            ProxySelector activeDelegate = GreenManNetworkCheckBlocker.delegatedProxySelector;
            if (activeDelegate != null) {
               activeDelegate.connectFailed(targetUri, proxyAddress, failureCause);
            }
         }
      }
   }
}
