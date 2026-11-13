package io.github.blacknoize404.systemInfoUtils.providers;

import java.net.InetAddress;

/** Small shared helpers for the providers. */
final class Providers {

    private Providers() {
    }

    /** The machine's hostname, env var first (no reverse DNS lookup). */
    static String hostname() {
        String env = System.getenv("COMPUTERNAME");
        if (env == null || env.isBlank()) env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) return env;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }
}
