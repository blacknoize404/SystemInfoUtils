package io.github.blacknoize404.systemInfoUtils;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.providers.CpuTimes;
import io.github.blacknoize404.systemInfoUtils.providers.SystemInfoProvider;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskActivity;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;

import java.util.List;
import java.util.Optional;

/**
 * The entry point: one-shot readings of the machine. For continuous sampling
 * (a live view, a recorder) use {@link SystemMonitor}, which computes the
 * rates between consecutive reads.
 * <p>
 * Everything here is a snapshot taken when the method runs — {@link #memory()},
 * {@link #power()} and {@link #thermal()} move on their own; {@link #host()}
 * and {@link #cpu()} describe static facts and can be called once.
 * <p>
 * On Windows the native calls need
 * {@code --enable-native-access=SystemInfoUtils}; without it the provider
 * reports empty optionals rather than crash.
 */
public final class SystemInfo {

    private static final SystemInfoProvider PROVIDER = SystemInfoProvider.create();

    private SystemInfo() {
    }

    /** The operating system family detected at startup. */
    public static Platform platform() {
        return Platform.current();
    }

    /** OS name and version, architecture and hostname — static facts. */
    public static HostInfo host() {
        return PROVIDER.host();
    }

    /** Seconds since the machine booted; {@code -1} when unknown. */
    public static long uptimeSeconds() {
        return PROVIDER.uptimeSeconds();
    }

    /** Processor model and core counts — static facts. */
    public static CpuInfo cpu() {
        return PROVIDER.cpuInfo();
    }

    /**
     * A one-shot CPU load: reads the counters twice ~80 ms apart and diffs
     * them. On platforms without counters (macOS) the provider's own interval
     * reading is used. For sustained sampling prefer {@link SystemMonitor},
     * which diffs its own consecutive reads without sleeping.
     */
    public static CpuLoad cpuLoad() {
        Optional<CpuTimes> first = PROVIDER.cpuTimes();
        if (first.isPresent()) {
            sleep(80);
            Optional<CpuTimes> second = PROVIDER.cpuTimes();
            if (second.isPresent()) {
                CpuLoad load = first.get().loadTo(second.get());
                return new CpuLoad(load.total(), load.perCore(), PROVIDER.cpuFrequencyMhz());
            }
        }
        Optional<CpuLoad> direct = PROVIDER.cpuLoadNow();
        return direct.map(load -> new CpuLoad(load.total(), load.perCore(), PROVIDER.cpuFrequencyMhz()))
                .orElse(new CpuLoad(-1, new double[0], PROVIDER.cpuFrequencyMhz()));
    }

    /** Current clock in MHz, or {@code -1} when the platform doesn't say. */
    public static double cpuFrequencyMhz() {
        return PROVIDER.cpuFrequencyMhz();
    }

    /** RAM and swap right now. */
    public static MemoryInfo memory() {
        return PROVIDER.memory();
    }

    /** Mounted filesystems with their sizes. */
    public static List<DiskVolume> volumes() {
        return PROVIDER.volumes();
    }

    /**
     * A one-shot disk throughput, measured like {@link #cpuLoad()}: counters
     * ~80 ms apart, or the provider's direct reading. Empty where nothing is
     * readable (Windows in this version).
     */
    public static Optional<DiskActivity> diskRates() {
        Optional<long[]> first = PROVIDER.diskIoCounters();
        if (first.isPresent()) {
            sleep(80);
            Optional<long[]> second = PROVIDER.diskIoCounters();
            if (second.isPresent()) {
                long[] a = first.get(), b = second.get();
                double read = Math.max(0, b[0] - a[0]) / 0.08;
                double write = Math.max(0, b[1] - a[1]) / 0.08;
                return Optional.of(new DiskActivity(read, write));
            }
        }
        return PROVIDER.diskRatesNow();
    }

    /** AC line and battery right now. */
    public static PowerInfo power() {
        return PROVIDER.power();
    }

    /** Temperature zones readable without extra privileges. */
    public static List<ThermalSensor> thermal() {
        return PROVIDER.thermal();
    }

    /** A single {@link SystemSample} — what the monitor publishes per tick. */
    static SystemSample sample(SystemInfoProvider provider, CpuLoad cpu, Optional<DiskActivity> disk) {
        return new SystemSample(java.time.Instant.now(), cpu, provider.memory(),
                disk, provider.power(), provider.thermal());
    }

    static SystemInfoProvider provider() {
        return PROVIDER;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
