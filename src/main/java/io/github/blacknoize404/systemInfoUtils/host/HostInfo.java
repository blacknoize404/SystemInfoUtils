package io.github.blacknoize404.systemInfoUtils.host;

/**
 * Facts about the running machine that do not change at runtime.
 *
 * @param osName      display name ({@code "Windows 11"}, {@code "Fedora Linux 41"})
 * @param osVersion   version or build string
 * @param architecture CPU architecture ({@code x86_64}, {@code aarch64})
 * @param hostname    the machine's network name, best effort
 */
public record HostInfo(String osName, String osVersion, String architecture, String hostname) {
}
