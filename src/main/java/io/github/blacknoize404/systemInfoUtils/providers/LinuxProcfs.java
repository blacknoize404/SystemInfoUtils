package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.Battery;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Linux: everything lives in {@code /proc} and {@code /sys}, plain files that
 * need no privileges. The root directory is injectable ({@link #LinuxProcfs(Path)})
 * so the parsers run against fixtures in tests.
 */
final class LinuxProcfs implements SystemInfoProvider {

    private final Path root;

    LinuxProcfs(Path root) {
        this.root = root;
    }

    private Path file(String path) {
        return root.resolve(path.startsWith("/") ? path.substring(1) : path);
    }

    private static List<String> lines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String value(String text) {
        int colon = text.indexOf(':');
        return colon < 0 ? "" : text.substring(colon + 1).trim();
    }

    @Override
    public CpuInfo cpuInfo() {
        List<String> cpuinfo = lines(file("/proc/cpuinfo"));
        String model = "";
        Set<String> physical = new LinkedHashSet<>();
        int logical = 0;
        String physicalId = "0";
        for (String line : cpuinfo) {
            if (line.startsWith("model name") && model.isEmpty()) model = value(line);
            else if (line.startsWith("physical id")) physicalId = value(line);
            else if (line.startsWith("core id")) physical.add(physicalId + ":" + value(line));
            else if (line.startsWith("processor")) logical++;
        }
        if (logical == 0) logical = Runtime.getRuntime().availableProcessors();
        if (model.isEmpty()) model = System.getProperty("os.arch", "");
        return new CpuInfo(model, physical.isEmpty() ? logical : physical.size(), logical);
    }

    /**
     * {@code /proc/stat}: {@code cpu user nice system idle iowait irq softirq steal}.
     * Element 0 is the aggregate {@code cpu} line, then one per {@code cpuN}.
     */
    @Override
    public Optional<CpuTimes> cpuTimes() {
        List<long[]> idles = new ArrayList<>();
        List<long[]> busies = new ArrayList<>();
        for (String line : lines(file("/proc/stat"))) {
            if (!line.startsWith("cpu")) break;
            if (line.startsWith("cpu ") || line.matches("cpu\\d+ .*")) {
                String[] f = line.trim().split("\\s+");
                if (f.length < 5) continue;
                long user = parseLong(f[1]), nice = parseLong(f[2]), system = parseLong(f[3]);
                long idle = parseLong(f[4]);
                long iowait = f.length > 5 ? parseLong(f[5]) : 0;
                long irq = f.length > 6 ? parseLong(f[6]) : 0;
                long softirq = f.length > 7 ? parseLong(f[7]) : 0;
                long steal = f.length > 8 ? parseLong(f[8]) : 0;
                idles.add(new long[]{idle + iowait});
                busies.add(new long[]{user + nice + system + irq + softirq + steal});
            } else break;
        }
        if (idles.isEmpty()) return Optional.empty();
        long[] idle = new long[idles.size()], busy = new long[idles.size()];
        for (int i = 0; i < idles.size(); i++) {
            idle[i] = idles.get(i)[0];
            busy[i] = busies.get(i)[0];
        }
        return Optional.of(new CpuTimes(idle, busy));
    }

    /** Mean of {@code scaling_cur_freq} across cores (kHz → MHz); falls back to cpuinfo. */
    @Override
    public double cpuFrequencyMhz() {
        Path cpufreq = file("/sys/devices/system/cpu");
        double sum = 0;
        int count = 0;
        try (Stream<Path> cpus = Files.list(cpufreq)) {
            for (Path dir : cpus.filter(p -> p.getFileName().toString().matches("cpu\\d+")).toList()) {
                for (String line : lines(dir.resolve("cpufreq/scaling_cur_freq"))) {
                    sum += parseLong(line.trim()) / 1000.0;
                    count++;
                }
            }
        } catch (IOException ignored) {
        }
        if (count > 0) return sum / count;
        for (String line : lines(file("/proc/cpuinfo"))) {
            if (line.startsWith("cpu MHz")) {
                try {
                    return Double.parseDouble(value(line));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** {@code /proc/meminfo}, kB values. */
    @Override
    public MemoryInfo memory() {
        long total = -1, available = -1, free = -1, swapTotal = 0, swapFree = 0;
        for (String line : lines(file("/proc/meminfo"))) {
            if (line.startsWith("MemTotal:")) total = kb(line);
            else if (line.startsWith("MemAvailable:")) available = kb(line);
            else if (line.startsWith("MemFree:")) free = kb(line);
            else if (line.startsWith("SwapTotal:")) swapTotal = kb(line);
            else if (line.startsWith("SwapFree:")) swapFree = kb(line);
        }
        if (available < 0) available = free;
        return new MemoryInfo(total, available, swapTotal, swapFree);
    }

    private static long kb(String line) {
        String[] f = line.split("\\s+");
        return f.length > 1 ? parseLong(f[1]) * 1024 : -1;
    }

    /** Mounts from {@code /proc/mounts}, sizes via {@link File}. Pseudo filesystems skipped. */
    @Override
    public List<DiskVolume> volumes() {
        List<DiskVolume> volumes = new ArrayList<>();
        for (String line : lines(file("/proc/mounts"))) {
            String[] f = line.split("\\s+");
            if (f.length < 3) continue;
            String fs = f[2];
            if (PSEUDO_FS.contains(fs)) continue;
            String mount = f[1].replace("\\040", " ");
            File dir = new File(mount);
            long total = dir.getTotalSpace(), freeBytes = dir.getUsableSpace();
            if (total <= 0) continue;
            volumes.add(new DiskVolume(mount, f[0], fs, total, freeBytes));
        }
        if (volumes.isEmpty()) {
            for (File r : File.listRoots()) {
                volumes.add(new DiskVolume(r.getPath(), "", "", r.getTotalSpace(), r.getFreeSpace()));
            }
        }
        return volumes;
    }

    private static final Set<String> PSEUDO_FS = Set.of(
            "proc", "sysfs", "devtmpfs", "devpts", "tmpfs", "cgroup", "cgroup2", "pstore",
            "securityfs", "debugfs", "tracefs", "configfs", "fusectl", "mqueue", "hugetlbfs",
            "bpf", "autofs", "binfmt_misc", "overlay", "squashfs", "ramfs", "nsfs", "efivarfs",
            "rpc_pipefs", "selinuxfs", "smackfs", "fuse.gvfsd-fuse", "fuse.snapfuse");

    /**
     * {@code /proc/diskstats}, summed over real disks (partitions and virtual
     * devices skipped): sectors read/written × 512 bytes.
     */
    @Override
    public Optional<long[]> diskIoCounters() {
        long read = 0, written = 0;
        boolean any = false;
        for (String line : lines(file("/proc/diskstats"))) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 14) continue;
            String name = f[2];
            if (SKIP_DISK.matcher(name).matches()) continue;
            read += parseLong(f[5]) * 512L;
            written += parseLong(f[9]) * 512L;
            any = true;
        }
        return any ? Optional.of(new long[]{read, written}) : Optional.empty();
    }

    private static final java.util.regex.Pattern SKIP_DISK = java.util.regex.Pattern.compile(
            "loop.*|ram.*|dm-.*|md.*|sr.*|zram.*|nvme\\d+n\\d+p\\d+|sd[a-z]\\d+|mmcblk\\d+p\\d+|vd[a-z]\\d+|hd[a-z]\\d+");

    /** {@code /sys/class/power_supply}: batteries and the mains adapter. */
    @Override
    public PowerInfo power() {
        boolean onAc = true;
        Battery battery = null;
        try (Stream<Path> supplies = Files.list(file("/sys/class/power_supply"))) {
            for (Path dir : supplies.toList()) {
                String type = firstLine(dir.resolve("type"));
                if (type.equalsIgnoreCase("Mains") || type.equalsIgnoreCase("USB")) {
                    String online = firstLine(dir.resolve("online"));
                    if (online.equals("1")) onAc = true;
                } else if (type.equalsIgnoreCase("Battery")) {
                    double percent = number(firstLine(dir.resolve("capacity")));
                    String status = firstLine(dir.resolve("status"));
                    boolean charging = status.equalsIgnoreCase("Charging") || status.equalsIgnoreCase("Full");
                    if (status.equalsIgnoreCase("Discharging")) onAc = false;
                    long seconds = -1;
                    double chargeNow = number(firstLine(dir.resolve("charge_now")));   // µAh
                    double currentNow = number(firstLine(dir.resolve("current_now"))); // µA
                    if (chargeNow > 0 && currentNow > 0) seconds = (long) (chargeNow / currentNow * 3600);
                    battery = new Battery(percent, charging, seconds);
                }
            }
        } catch (IOException ignored) {
        }
        return new PowerInfo(onAc, Optional.ofNullable(battery));
    }

    /** {@code /etc/os-release} for the pretty name; hostname and uptime from proc. */
    @Override
    public HostInfo host() {
        String name = System.getProperty("os.name", "Linux");
        String version = System.getProperty("os.version", "");
        for (String line : lines(file("/etc/os-release"))) {
            if (line.startsWith("PRETTY_NAME=")) name = unquote(line.substring(12));
            else if (line.startsWith("VERSION_ID=")) version = unquote(line.substring(11));
        }
        String hostname = firstLine(file("/proc/sys/kernel/hostname"));
        if (hostname.isEmpty()) hostname = Providers.hostname();
        return new HostInfo(name, version, System.getProperty("os.arch", ""), hostname);
    }

    @Override
    public long uptimeSeconds() {
        for (String line : lines(file("/proc/uptime"))) {
            String[] f = line.trim().split("\\s+");
            if (f.length > 0) return (long) parseDouble(f[0]);
        }
        return -1;
    }

    /** {@code /sys/class/thermal/thermal_zone*}: name + milli-degrees. */
    @Override
    public List<ThermalSensor> thermal() {
        List<ThermalSensor> sensors = new ArrayList<>();
        try (Stream<Path> zones = Files.list(file("/sys/class/thermal"))) {
            for (Path zone : zones.filter(p -> p.getFileName().toString().startsWith("thermal_zone")).toList()) {
                double milli = number(firstLine(zone.resolve("temp")));
                if (milli <= 0) continue;
                String name = firstLine(zone.resolve("type"));
                sensors.add(new ThermalSensor(name.isEmpty() ? zone.getFileName().toString() : name, milli / 1000.0));
            }
        } catch (IOException ignored) {
        }
        return sensors;
    }

    private static String firstLine(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            return lines.isEmpty() ? "" : lines.getFirst().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String unquote(String s) {
        s = s.trim();
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double number(String s) {
        return s.isEmpty() ? -1 : parseDouble(s);
    }
}
