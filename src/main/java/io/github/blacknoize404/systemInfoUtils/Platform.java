package io.github.blacknoize404.systemInfoUtils;

/**
 * The operating system family the library is running on, detected once from
 * {@code os.name}. {@link #OTHER} covers anything else — the providers then
 * fall back to what plain Java can report.
 */
public enum Platform {
    WINDOWS,
    MACOS,
    LINUX,
    OTHER;

    private static final Platform CURRENT = detect();

    /** The platform of this JVM, resolved once. */
    public static Platform current() {
        return CURRENT;
    }

    private static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) return WINDOWS;
        if (os.contains("mac")) return MACOS;
        if (os.contains("linux")) return LINUX;
        return OTHER;
    }
}
