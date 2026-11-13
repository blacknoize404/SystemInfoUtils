package io.github.blacknoize404.systemInfoUtils.providers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Runs a system command and captures its stdout. Providers on macOS lean on
 * userland tools ({@code sysctl}, {@code top}, {@code pmset}); a failure or a
 * timeout yields {@code null} so the caller can mark the metric unavailable.
 */
final class Shell {

    private Shell() {
    }

    /** Runs {@code command}; stdout on success, {@code null} on any failure. */
    static String output(String... command) {
        return output(10, command);
    }

    static String output(int timeoutSeconds, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean done = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return null;
            }
            byte[] bytes = process.getInputStream().readAllBytes();
            if (process.exitValue() != 0) return null;
            String text = new String(bytes, consoleCharset());
            return text.isBlank() ? null : text;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    /** The console encoding of the OS — what {@code sysctl} & friends print in. */
    static Charset consoleCharset() {
        return StandardCharsets.UTF_8;
    }
}
