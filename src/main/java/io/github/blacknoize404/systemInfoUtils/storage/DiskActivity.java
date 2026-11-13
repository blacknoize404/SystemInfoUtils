package io.github.blacknoize404.systemInfoUtils.storage;

/**
 * Disk throughput over an interval, summed over every disk.
 *
 * @param readBytesPerSecond  bytes read per second
 * @param writeBytesPerSecond bytes written per second
 */
public record DiskActivity(double readBytesPerSecond, double writeBytesPerSecond) {
}
