package service;

import com.google.gson.Gson;
import jakarta.servlet.ServletContext;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SystemConfigService {

    private static final String CONFIG_CLASSPATH = "config/SystemConfig.json";
    private static final String CONFIG_REALPATH  = "/WEB-INF/classes/config/SystemConfig.json";

    private final Gson gson = new Gson();

    public static class SystemConfig {
        public int minMinutesAfterStart;                 // checkout
        public int checkinAllowedBeforeStartMinutes;     // checkin
    }

    private SystemConfig defaultCfg() {
        SystemConfig cfg = new SystemConfig();
        cfg.minMinutesAfterStart = 60;
        cfg.checkinAllowedBeforeStartMinutes = 60;
        return cfg;
    }

    public SystemConfig load(ServletContext ctx) {
        // 1) Ưu tiên runtime file
        try {
            if (ctx != null) {
                String realPath = ctx.getRealPath(CONFIG_REALPATH);
                if (realPath != null) {
                    Path path = Paths.get(realPath);
                    if (Files.exists(path)) {
                        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        SystemConfig cfg = gson.fromJson(json, SystemConfig.class);
                        if (cfg == null) return defaultCfg();

                        // fill default nếu thiếu field / json cũ
                        if (cfg.minMinutesAfterStart < 0 || cfg.minMinutesAfterStart > 600) cfg.minMinutesAfterStart = 60;
                        if (cfg.checkinAllowedBeforeStartMinutes < 0 || cfg.checkinAllowedBeforeStartMinutes > 600)
                            cfg.checkinAllowedBeforeStartMinutes = 60;

                        return cfg;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2) Fallback classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_CLASSPATH)) {
            if (is == null) return defaultCfg();

            SystemConfig cfg = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), SystemConfig.class);
            if (cfg == null) return defaultCfg();

            if (cfg.minMinutesAfterStart < 0 || cfg.minMinutesAfterStart > 600) cfg.minMinutesAfterStart = 60;
            if (cfg.checkinAllowedBeforeStartMinutes < 0 || cfg.checkinAllowedBeforeStartMinutes > 600)
                cfg.checkinAllowedBeforeStartMinutes = 60;

            return cfg;

        } catch (Exception e) {
            return defaultCfg();
        }
    }
}
