package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.Battery;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Windows: raw Win32/NT calls through the foreign-function API, no native
 * library to ship. Needs {@code --enable-native-access=SystemInfoUtils}.
 * <ul>
 *   <li>CPU times: {@code NtQuerySystemInformation(SystemProcessorPerformanceInformation)}
 *   — kernel time includes idle, so busy = kernel + user − idle.</li>
 *   <li>Memory: {@code GlobalMemoryStatusEx}. Battery: {@code GetSystemPowerStatus}.
 *   Uptime: {@code GetTickCount64}. Clock: {@code CallNtPowerInformation(ProcessorInformation)}.</li>
 *   <li>Physical cores: {@code GetLogicalProcessorInformation} (RelationProcessorCore count).</li>
 *   <li>Volumes: {@code GetVolumeInformationW} for label and filesystem.</li>
 * </ul>
 * Disk counters are not exposed: {@code IOCTL_DISK_PERFORMANCE} needs admin and
 * PDH counter names are localized, so {@link #diskIoCounters()} stays empty in
 * this version. Thermal zones need WMI — also left out.
 */
final class WindowsFfm implements SystemInfoProvider {

    private static final int PROCESSOR_PERFORMANCE_INFORMATION = 8;
    private static final int PROCESSOR_INFORMATION = 11;
    private static final int RELATION_PROCESSOR_CORE = 0;

    private final MethodHandle ntQuery;
    private final MethodHandle memoryStatus;
    private final MethodHandle powerStatus;
    private final MethodHandle tickCount;
    private final MethodHandle callPower;
    private final MethodHandle logicalInfo;
    private final MethodHandle volumeInfo;
    private final MethodHandle rtlVersion;

    private WindowsFfm(MethodHandle ntQuery, MethodHandle memoryStatus, MethodHandle powerStatus,
                       MethodHandle tickCount, MethodHandle callPower, MethodHandle logicalInfo,
                       MethodHandle volumeInfo, MethodHandle rtlVersion) {
        this.ntQuery = ntQuery;
        this.memoryStatus = memoryStatus;
        this.powerStatus = powerStatus;
        this.tickCount = tickCount;
        this.callPower = callPower;
        this.logicalInfo = logicalInfo;
        this.volumeInfo = volumeInfo;
        this.rtlVersion = rtlVersion;
    }

    static WindowsFfm create() {
        return new WindowsFfm(
                handle("ntdll", "NtQuerySystemInformation",
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)),
                handle("kernel32", "GlobalMemoryStatusEx",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                handle("kernel32", "GetSystemPowerStatus",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                handle("kernel32", "GetTickCount64",
                        FunctionDescriptor.of(JAVA_LONG)),
                handle("powrprof", "CallNtPowerInformation",
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT)),
                handle("kernel32", "GetLogicalProcessorInformation",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)),
                handle("kernel32", "GetVolumeInformationW",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT)),
                handle("ntdll", "RtlGetVersion",
                        FunctionDescriptor.of(JAVA_INT, ADDRESS)));
    }

    private static MethodHandle handle(String dll, String name, FunctionDescriptor descriptor) {
        try {
            Optional<MemorySegment> symbol = SymbolLookup.libraryLookup(dll, Arena.global()).find(name);
            return symbol.map(s -> Linker.nativeLinker().downcallHandle(s, descriptor)).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public CpuInfo cpuInfo() {
        String model = cpuModelFromRegistry();
        if (model == null) model = System.getenv("PROCESSOR_IDENTIFIER");
        int logical = Runtime.getRuntime().availableProcessors();
        return new CpuInfo(model == null ? "" : model.trim(), physicalCores(), logical);
    }

    /** The friendly name lives in the registry; {@code reg query} needs no admin. */
    static String cpuModelFromRegistry() {
        String out = Shell.output("reg", "query",
                "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString");
        return parseRegSz(out, "ProcessorNameString");
    }

    static String parseRegSz(String output, String valueName) {
        if (output == null) return null;
        for (String line : output.split("\\R")) {
            if (line.contains(valueName)) {
                int idx = line.lastIndexOf("REG_SZ");
                if (idx >= 0) return line.substring(idx + 6).trim();
            }
        }
        return null;
    }

    private int physicalCores() {
        if (logicalInfo == null) return Runtime.getRuntime().availableProcessors();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(JAVA_INT);
            int ignored = (int) logicalInfo.invokeExact(MemorySegment.NULL, size);
            int needed = size.get(JAVA_INT, 0);
            if (needed <= 0) return Runtime.getRuntime().availableProcessors();
            MemorySegment buffer = arena.allocate(needed);
            int ok = (int) logicalInfo.invokeExact(buffer, size);
            if (ok == 0) return Runtime.getRuntime().availableProcessors();
            int count = 0, record = 24;
            for (long off = 0; off + record <= needed; off += record) {
                if (buffer.get(JAVA_INT, off + 8) == RELATION_PROCESSOR_CORE) count++;
            }
            return count > 0 ? count : Runtime.getRuntime().availableProcessors();
        } catch (Throwable t) {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    /**
     * SYSTEM_PROCESSOR_PERFORMANCE_INFORMATION per logical core:
     * IdleTime@0, KernelTime@8, UserTime@16 — 48 bytes each.
     * Kernel includes idle → busy = kernel + user − idle. Element 0 sums the cores.
     */
    @Override
    public Optional<CpuTimes> cpuTimes() {
        if (ntQuery == null) return Optional.empty();
        int cores = Runtime.getRuntime().availableProcessors();
        try (Arena arena = Arena.ofConfined()) {
            int bytes = 48 * cores;
            MemorySegment buffer = arena.allocate(bytes);
            MemorySegment retLen = arena.allocate(JAVA_INT);
            int status = (int) ntQuery.invokeExact(PROCESSOR_PERFORMANCE_INFORMATION, buffer, bytes, retLen);
            if (status != 0) return Optional.empty();
            long[] idle = new long[cores + 1], busy = new long[cores + 1];
            for (int i = 0; i < cores; i++) {
                long off = i * 48L;
                long idleT = buffer.get(JAVA_LONG, off);
                long kernel = buffer.get(JAVA_LONG, off + 8);
                long user = buffer.get(JAVA_LONG, off + 16);
                idle[i + 1] = idleT;
                busy[i + 1] = kernel + user - idleT;
                idle[0] += idleT;
                busy[0] += kernel + user - idleT;
            }
            return Optional.of(new CpuTimes(idle, busy));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** PROCESSOR_POWER_INFORMATION per core: Number, MaxMhz, CurrentMhz@8 — 24 bytes. */
    @Override
    public double cpuFrequencyMhz() {
        if (callPower == null) return -1;
        int cores = Runtime.getRuntime().availableProcessors();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(24L * cores);
            int status = (int) callPower.invokeExact(PROCESSOR_INFORMATION, MemorySegment.NULL, 0, buffer, 24 * cores);
            if (status != 0) return -1;
            double sum = 0;
            for (int i = 0; i < cores; i++) sum += buffer.get(JAVA_INT, i * 24L + 8);
            return sum / cores;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** MEMORYSTATUSEX: length@0, load@4, totalPhys@8, availPhys@16, totalPage@24, availPage@32. */
    @Override
    public MemoryInfo memory() {
        if (memoryStatus == null) return new MemoryInfo(-1, -1, -1, -1);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(64);
            buffer.set(JAVA_INT, 0, 64);
            int ok = (int) memoryStatus.invokeExact(buffer);
            if (ok == 0) return new MemoryInfo(-1, -1, -1, -1);
            return new MemoryInfo(buffer.get(JAVA_LONG, 8), buffer.get(JAVA_LONG, 16),
                    buffer.get(JAVA_LONG, 24), buffer.get(JAVA_LONG, 32));
        } catch (Throwable t) {
            return new MemoryInfo(-1, -1, -1, -1);
        }
    }

    /** SYSTEM_POWER_STATUS: AC@0, flags@1, percent@2, status@3, lifeSecs@4 — 12 bytes. */
    @Override
    public PowerInfo power() {
        if (powerStatus == null) return new PowerInfo(true, Optional.empty());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(12);
            int ok = (int) powerStatus.invokeExact(buffer);
            if (ok == 0) return new PowerInfo(true, Optional.empty());
            int ac = buffer.get(JAVA_BYTE, 0) & 0xFF;
            int flag = buffer.get(JAVA_BYTE, 1) & 0xFF;
            int percent = buffer.get(JAVA_BYTE, 2) & 0xFF;
            int lifeSecs = buffer.get(JAVA_INT, 4);
            boolean hasBattery = (flag & 128) == 0 && percent != 255;
            boolean charging = (flag & 8) != 0 || ac == 1;
            Battery battery = hasBattery
                    ? new Battery(percent > 100 ? -1 : percent, charging, lifeSecs > 0 ? lifeSecs : -1)
                    : null;
            return new PowerInfo(ac != 0, Optional.ofNullable(battery));
        } catch (Throwable t) {
            return new PowerInfo(true, Optional.empty());
        }
    }

    @Override
    public List<DiskVolume> volumes() {
        List<DiskVolume> volumes = new ArrayList<>();
        for (File rootDir : File.listRoots()) {
            String mount = rootDir.getPath();
            String label = "", fs = "";
            if (volumeInfo != null) {
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment root = wide(arena, mount);
                    MemorySegment nameBuf = arena.allocate(512);
                    MemorySegment fsBuf = arena.allocate(256);
                    int ok = (int) volumeInfo.invokeExact(root, nameBuf, 256, MemorySegment.NULL,
                            MemorySegment.NULL, MemorySegment.NULL, fsBuf, 128);
                    if (ok != 0) {
                        label = nameBuf.getString(0, StandardCharsets.UTF_16LE);
                        fs = fsBuf.getString(0, StandardCharsets.UTF_16LE);
                    }
                } catch (Throwable ignored) {
                }
            }
            volumes.add(new DiskVolume(mount, label, fs, rootDir.getTotalSpace(), rootDir.getFreeSpace()));
        }
        return volumes;
    }

    @Override
    public Optional<long[]> diskIoCounters() {
        return Optional.empty();
    }

    /** OSVERSIONINFOEXW via RtlGetVersion — the real build {@code os.version} can't say. */
    @Override
    public HostInfo host() {
        String name = System.getProperty("os.name", "Windows");
        String version = System.getProperty("os.version", "");
        if (rtlVersion != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(284);
                buffer.set(JAVA_INT, 0, 284);
                int status = (int) rtlVersion.invokeExact(buffer);
                if (status == 0) {
                    int major = buffer.get(JAVA_INT, 4), minor = buffer.get(JAVA_INT, 8), build = buffer.get(JAVA_INT, 12);
                    version = major + "." + minor + "." + build;
                    if (major == 10 && build >= 22000) name = "Windows 11";
                    else if (major == 10) name = "Windows 10";
                }
            } catch (Throwable ignored) {
            }
        }
        String hostname = System.getenv("COMPUTERNAME");
        if (hostname == null || hostname.isBlank()) hostname = Providers.hostname();
        return new HostInfo(name, version, System.getProperty("os.arch", ""), hostname);
    }

    @Override
    public long uptimeSeconds() {
        if (tickCount == null) return -1;
        try {
            return (long) tickCount.invokeExact() / 1000;
        } catch (Throwable t) {
            return -1;
        }
    }

    @Override
    public List<ThermalSensor> thermal() {
        return List.of();
    }

    private static MemorySegment wide(Arena arena, String text) {
        byte[] bytes = (text + "\0").getBytes(StandardCharsets.UTF_16LE);
        MemorySegment segment = arena.allocate(bytes.length);
        segment.asByteBuffer().put(bytes);
        return segment;
    }
}
