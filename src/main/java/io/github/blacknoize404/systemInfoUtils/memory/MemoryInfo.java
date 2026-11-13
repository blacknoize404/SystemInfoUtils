package io.github.blacknoize404.systemInfoUtils.memory;

/**
 * RAM and swap at one instant.
 *
 * @param totalBytes      installed RAM
 * @param availableBytes  memory usable right now (free plus reclaimable caches,
 *                        like Linux's {@code MemAvailable})
 * @param swapTotalBytes  swap configured
 * @param swapFreeBytes   swap still unused
 */
public record MemoryInfo(long totalBytes, long availableBytes, long swapTotalBytes, long swapFreeBytes) {

    /** RAM in use: {@code totalBytes - availableBytes}. */
    public long usedBytes() {
        return totalBytes - availableBytes;
    }

    /** RAM in use as a fraction in {@code [0, 1]} ({@code 0} when total is unknown). */
    public double usedPercent() {
        return totalBytes <= 0 ? 0 : (double) usedBytes() / totalBytes;
    }
}
