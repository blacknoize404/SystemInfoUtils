package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.Battery;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskActivity;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * macOS: userland tools, no privileges needed. {@code sysctl} answers the
 * static facts; the live rates come from tools that measure an interval
 * themselves — {@code top -l 2} (CPU) and {@code iostat -c 2} (disk) — because
 * mach's raw counters are not reachable without a native bridge.
 * Every parser is a static method so tests feed it canned output.
 */
final class MacOsShell implements SystemInfoProvider {

    @Override
    public CpuInfo cpuInfo() {
        String model = Shell.output("sysctl", "-n", "machdep.cpu.brand_string");
        if (model == null || model.isBlank()) model = Shell.output("sysctl", "-n", "hw.model");
        int logical = (int) number(Shell.output("sysctl", "-n", "hw.logicalcpu"));
        int physical = (int) number(Shell.output("sysctl", "-n", "hw.physicalcpu"));
        if (logical <= 0) logical = Runtime.getRuntime().availableProcessors();
        if (physical <= 0) physical = logical;
        return new CpuInfo(model == null ? "" : model.trim(), physical, logical);
    }

    @Override
    public Optional<CpuTimes> cpuTimes() {
        return Optional.empty();
    }

    /**
     * {@code top -l 2 -n 0 -s 0}: the second "CPU usage" line carries the live
     * interval; the first is the since-boot average and is skipped.
     */
    @Override
    public Optional<CpuLoad> cpuLoadNow() {
        return parseTop(Shell.output(15, "top", "-l", "2", "-n", "0", "-s", "0"));
    }

    static Optional<CpuLoad> parseTop(String out) {
        if (out == null) return Optional.empty();
        double last = -1;
        for (String line : out.split("\\R")) {
            if (!line.startsWith("CPU usage:")) continue;
            String rest = line.substring("CPU usage:".length());
            double user = percentBefore(rest, "user");
            double sys = percentBefore(rest, "sys");
            double idle = percentBefore(rest, "idle");
            if (user >= 0 && sys >= 0 && idle >= 0) last = (user + sys) / 100.0;
        }
        return last < 0 ? Optional.empty() : Optional.of(new CpuLoad(last, new double[0], -1));
    }

    /** Reads the number before {@code "N.NN% word"} fragments like {@code "8.2% user"}. */
    private static double percentBefore(String text, String word) {
        int idx = text.indexOf(word);
        if (idx < 0) return -1;
        String before = text.substring(0, idx).trim();
        int pct = before.lastIndexOf('%');
        if (pct < 0) return -1;
        int start = before.substring(0, pct).lastIndexOf(' ') + 1;
        try {
            return Double.parseDouble(before.substring(start, pct));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** ARM Macs don't publish a frequency; Intel's hw.cpufrequency is the max, not current. */
    @Override
    public double cpuFrequencyMhz() {
        return -1;
    }

    /**
     * {@code hw.memsize} for the total; {@code vm_stat} pages × page size for the
     * available estimate (free + inactive + speculative is what the OS would
     * hand out); swap from {@code sysctl vm.swapusage}.
     */
    @Override
    public MemoryInfo memory() {
        long total = (long) number(Shell.output("sysctl", "-n", "hw.memsize"));
        long pageSize = (long) number(Shell.output("sysctl", "-n", "vm.pagesize"));
        if (pageSize <= 0) pageSize = 16384;
        long freePages = 0, inactivePages = 0, speculative = 0;
        String vm = Shell.output("vm_stat");
        if (vm != null) {
            freePages = vmStat(vm, "Pages free");
            inactivePages = vmStat(vm, "Pages inactive");
            speculative = vmStat(vm, "Pages speculative");
        }
        long available = (freePages + inactivePages + speculative) * pageSize;
        long swapTotal = 0, swapFree = 0;
        String swap = Shell.output("sysctl", "-n", "vm.swapusage");
        if (swap != null) {
            swapTotal = (long) (mib(swap, "total") * 1024 * 1024);
            swapFree = (long) (mib(swap, "free") * 1024 * 1024);
        }
        return new MemoryInfo(total, available, swapTotal, swapFree);
    }

    static long vmStat(String out, String key) {
        for (String line : out.split("\\R")) {
            if (line.startsWith(key)) {
                String digits = line.replaceAll("[^0-9]", "");
                return digits.isEmpty() ? 0 : Long.parseLong(digits);
            }
        }
        return 0;
    }

    /** {@code vm.swapusage} fields: {@code "total = 1024.00M  used = 512.00M  free = 512.00M"}. */
    static double mib(String text, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(field + "\\s*=\\s*([0-9.]+)M").matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    /** {@code df -k -P}: POSIX columns; the last column is the mount point. */
    @Override
    public List<DiskVolume> volumes() {
        String out = Shell.output("df", "-k", "-P");
        List<DiskVolume> volumes = new ArrayList<>();
        if (out == null) return volumes;
        boolean first = true;
        for (String line : out.split("\\R")) {
            if (first) {
                first = false;
                continue;
            }
            String[] f = line.trim().split("\\s+");
            if (f.length < 6) continue;
            if (f[0].equals("devfs") || f[0].startsWith("map ")) continue;
            volumes.add(new DiskVolume(f[f.length - 1], f[0], "",
                    parseLong(f[1]) * 1024, parseLong(f[3]) * 1024));
        }
        return volumes;
    }

    @Override
    public Optional<long[]> diskIoCounters() {
        return Optional.empty();
    }

    /** {@code iostat -d -c 2 -w 1}: second sample has MB/s per disk; summed. */
    @Override
    public Optional<DiskActivity> diskRatesNow() {
        return parseIostat(Shell.output(15, "iostat", "-d", "-c", "2", "-w", "1"));
    }

    static Optional<DiskActivity> parseIostat(String out) {
        if (out == null) return Optional.empty();
        // Data rows: <KB/t tps MB/s> per disk, then <us sy id> cpu, then <1m 5m 15m> load avg.
        double mbPerSec = -1;
        for (String line : out.split("\\R")) {
            String[] f = line.trim().split("\\s+");
            if (f.length < 9 || !f[0].matches("[0-9.]+")) continue;
            double sum = 0;
            int diskColumns = f.length - 6;
            for (int i = 2; i < diskColumns; i += 3) sum += Math.max(0, parseDouble(f[i]));
            mbPerSec = sum;
        }
        return mbPerSec < 0 ? Optional.empty()
                : Optional.of(new DiskActivity(mbPerSec * 1024 * 1024, 0));
    }

    /** {@code pmset -g batt}: "Now drawing from 'X'"; "-Battery0  85%; discharging; 4:30 remaining". */
    @Override
    public PowerInfo power() {
        return parsePmset(Shell.output("pmset", "-g", "batt"));
    }

    static PowerInfo parsePmset(String out) {
        if (out == null) return new PowerInfo(true, Optional.empty());
        boolean onAc = !out.contains("Battery Power");
        Battery battery = null;
        java.util.regex.Pattern percentPattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)%");
        for (String line : out.split("\\R")) {
            java.util.regex.Matcher pct = percentPattern.matcher(line);
            if (!pct.find()) continue;
            double percent = Double.parseDouble(pct.group(1));
            boolean charging = (line.contains("charging") && !line.contains("discharging")
                    && !line.contains("not charging")) || line.contains("charged");
            long seconds = -1;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+):(\\d+)\\s+remaining").matcher(line);
            if (m.find()) {
                seconds = (Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 60;
            }
            battery = new Battery(percent, charging, seconds);
        }
        return new PowerInfo(onAc, Optional.ofNullable(battery));
    }

    /** {@code sw_vers} fields; uptime from {@code kern.boottime}. */
    @Override
    public HostInfo host() {
        String name = "macOS", version = System.getProperty("os.version", "");
        String sw = Shell.output("sw_vers");
        if (sw != null) {
            for (String line : sw.split("\\R")) {
                if (line.startsWith("ProductName:")) name = line.substring(12).trim();
                else if (line.startsWith("ProductVersion:")) version = line.substring(15).trim();
            }
        }
        String hostname = Shell.output("hostname");
        if (hostname == null || hostname.isBlank()) hostname = Providers.hostname();
        return new HostInfo(name, version, System.getProperty("os.arch", ""), hostname.trim());
    }

    /** {@code kern.boottime}: "{ sec = 1699000000, usec = 0 }". */
    @Override
    public long uptimeSeconds() {
        String out = Shell.output("sysctl", "-n", "kern.boottime");
        if (out == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("sec\\s*=\\s*(\\d+)").matcher(out);
        if (!m.find()) return -1;
        return System.currentTimeMillis() / 1000 - Long.parseLong(m.group(1));
    }

    @Override
    public List<ThermalSensor> thermal() {
        return List.of();
    }

    private static double number(String s) {
        return s == null ? -1 : parseDouble(s.trim());
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
