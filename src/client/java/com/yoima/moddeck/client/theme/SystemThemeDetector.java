package com.yoima.moddeck.client.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Best-effort OS theme detection used by the default Automatic mode. */
final class SystemThemeDetector {
    private static final Logger LOGGER = Logger.getLogger(SystemThemeDetector.class.getName());

    private SystemThemeDetector() {}

    static boolean prefersDark() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) return windowsPrefersDark();
            if (os.contains("mac")) return commandContains("defaults", "Dark", "read", "-g", "AppleInterfaceStyle");
            String gtkTheme = System.getenv("GTK_THEME");
            String desktop = System.getenv("XDG_CURRENT_DESKTOP");
            return containsDark(gtkTheme) || containsDark(desktop);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            LOGGER.log(Level.FINE, "Could not detect the operating-system theme; using dark fallback", exception);
            return true;
        }
    }

    private static boolean windowsPrefersDark() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme").redirectErrorStream(true).start();
        if (!process.waitFor(750, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return true;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return !output.matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x1(?:\\s|$).*");
    }

    private static boolean commandContains(String command, String expected, String... arguments)
            throws IOException, InterruptedException {
        String[] invocation = new String[arguments.length + 1];
        invocation[0] = command;
        System.arraycopy(arguments, 0, invocation, 1, arguments.length);
        Process process = new ProcessBuilder(invocation).redirectErrorStream(true).start();
        if (!process.waitFor(750, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return true;
        }
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).contains(expected);
    }

    private static boolean containsDark(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("dark");
    }
}
