package io.github.blacknoize404.systemInfoUtils.storage;

/**
 * A mounted filesystem.
 *
 * @param mount      where it is mounted ({@code "C:\\"}, {@code "/"})
 * @param label      the volume name the user knows, possibly empty
 * @param fileSystem the filesystem type ({@code NTFS}, {@code ext4},
 *                   {@code apfs}), possibly empty
 * @param totalBytes capacity
 * @param freeBytes  space still writable
 */
public record DiskVolume(String mount, String label, String fileSystem, long totalBytes, long freeBytes) {

    /** Bytes in use: {@code totalBytes - freeBytes}. */
    public long usedBytes() {
        return totalBytes - freeBytes;
    }
}
