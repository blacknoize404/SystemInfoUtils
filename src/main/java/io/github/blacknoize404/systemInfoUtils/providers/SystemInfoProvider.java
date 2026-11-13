package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.Platform;
import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskActivity;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;

import java.util.List;
import java.util.Optional;

/**
 * How the library reads each metric on one operating system. Two ways of
 * measuring rates exist:
 * <ul>
 *   <li><b>Cumulative counters</b> ({@link #cpuTimes()}, {@link #diskIoCounters()})
 *   — cheap reads the monitor diffs between samples; the honest shape of the data.</li>
 *   <li><b>Direct rates</b> ({@link #cpuLoadNow()}, {@link #diskRatesNow()}) — for
 *   platforms whose tools report an interval average instead of counters
 *   (macOS's {@code top -l 2} and {@code iostat}). The provider runs whatever it
 *   takes to produce one reading.</li>
 * </ul>
 * A provider returns {@link Optional#empty()} for every capability its platform
 * cannot offer; callers then leave the metric out rather than fabricate it.
 */
public interface SystemInfoProvider {

    /** Processor model and core counts. */
    CpuInfo cpuInfo();

    /** Cumulative CPU tick counters, when the platform keeps them. */
    Optional<CpuTimes> cpuTimes();

    /** A CPU load reading produced directly, when no counters exist. */
    default Optional<CpuLoad> cpuLoadNow() {
        return Optional.empty();
    }

    /** Current clock in MHz, or {@code -1} when unknown. */
    double cpuFrequencyMhz();

    /** RAM and swap. */
    MemoryInfo memory();

    /** Mounted filesystems. */
    List<DiskVolume> volumes();

    /** Cumulative {@code {readBytes, writeBytes}} over all disks, when kept. */
    Optional<long[]> diskIoCounters();

    /** Disk throughput produced directly, when no counters exist. */
    default Optional<DiskActivity> diskRatesNow() {
        return Optional.empty();
    }

    /** AC line and battery. */
    PowerInfo power();

    /** OS name, version, architecture and hostname. */
    HostInfo host();

    /** Seconds since the machine booted. */
    long uptimeSeconds();

    /** Temperature zones, where readable without extra privileges. */
    List<ThermalSensor> thermal();

    /** The provider for the running platform. */
    static SystemInfoProvider create() {
        return switch (Platform.current()) {
            case WINDOWS -> WindowsFfm.create();
            case MACOS -> new MacOsShell();
            case LINUX -> new LinuxProcfs(java.nio.file.Path.of("/"));
            case OTHER -> new Fallback();
        };
    }
}
