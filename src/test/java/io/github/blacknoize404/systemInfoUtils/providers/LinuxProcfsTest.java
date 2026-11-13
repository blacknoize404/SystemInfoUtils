package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.Battery;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxProcfsTest {

    @TempDir
    Path root;

    private LinuxProcfs provider;

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path.substring(1));
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @BeforeEach
    void setUp() {
        provider = new LinuxProcfs(root);
    }

    @Test
    void cpuInfoParsesModelAndCoreCounts() throws IOException {
        write("/proc/cpuinfo", """
                processor   : 0
                physical id : 0
                core id     : 0
                model name  : AMD Ryzen 5 5600X
                processor   : 1
                physical id : 0
                core id     : 1
                model name  : AMD Ryzen 5 5600X
                processor   : 2
                physical id : 0
                core id     : 0
                model name  : AMD Ryzen 5 5600X
                processor   : 3
                physical id : 0
                core id     : 1
                model name  : AMD Ryzen 5 5600X
                """);
        CpuInfo info = provider.cpuInfo();
        assertEquals("AMD Ryzen 5 5600X", info.model());
        assertEquals(4, info.logicalCores());
        assertEquals(2, info.physicalCores());
    }

    @Test
    void cpuTimesSplitIdleAndBusy() throws IOException {
        write("/proc/stat", """
                cpu  100 0 50 200 30 10 5 2 0 0
                cpu0 50 0 25 100 15 5 3 1 0 0
                cpu1 50 0 25 100 15 5 2 1 0 0
                intr 1234
                """);
        Optional<CpuTimes> times = provider.cpuTimes();
        assertTrue(times.isPresent());
        CpuTimes t = times.get();
        // cpu: busy = 100+0+50+10+5+2 = 167, idle = 200+30 = 230
        assertEquals(230, t.idle()[0]);
        assertEquals(167, t.busy()[0]);
        assertEquals(115, t.idle()[1]);
        assertEquals(84, t.busy()[1]);
    }

    @Test
    void memoryReadsKbFields() throws IOException {
        write("/proc/meminfo", """
                MemTotal:       16384000 kB
                MemFree:         8192000 kB
                MemAvailable:   12288000 kB
                SwapTotal:       2097152 kB
                SwapFree:        1048576 kB
                """);
        MemoryInfo mem = provider.memory();
        assertEquals(16384000L * 1024, mem.totalBytes());
        assertEquals(12288000L * 1024, mem.availableBytes());
        assertEquals(2097152L * 1024, mem.swapTotalBytes());
        assertEquals(1048576L * 1024, mem.swapFreeBytes());
    }

    @Test
    void hostReadsOsReleaseAndHostname() throws IOException {
        write("/etc/os-release", """
                NAME="Fedora Linux"
                VERSION_ID="41"
                PRETTY_NAME="Fedora Linux 41 (Workstation Edition)"
                """);
        write("/proc/sys/kernel/hostname", "thinkpad");
        var host = provider.host();
        assertEquals("Fedora Linux 41 (Workstation Edition)", host.osName());
        assertEquals("41", host.osVersion());
        assertEquals("thinkpad", host.hostname());
    }

    @Test
    void uptimeParsesSeconds() throws IOException {
        write("/proc/uptime", "12345.67 98765.43\n");
        assertEquals(12345, provider.uptimeSeconds());
    }

    @Test
    void diskIoSumsRealDisksAndSkipsVirtualOnes() throws IOException {
        write("/proc/diskstats", """
                   8       0 sda 100 0 2048 0 200 0 4096 0 0 0 0 0
                   8       1 sda1 50 0 1024 0 100 0 2048 0 0 0 0 0
                 259       0 nvme0n1 300 0 8192 0 400 0 16384 0 0 0 0 0
                 259       1 nvme0n1p1 150 0 4096 0 200 0 8192 0 0 0 0 0
                   7       0 loop0 10 0 64 0 10 0 64 0 0 0 0 0
                """);
        Optional<long[]> counters = provider.diskIoCounters();
        assertTrue(counters.isPresent());
        // sda 2048 sectors + nvme0n1 8192 sectors, partitions and loop skipped
        assertEquals((2048 + 8192) * 512L, counters.get()[0]);
        assertEquals((4096 + 16384) * 512L, counters.get()[1]);
    }

    @Test
    void batteryReadsCapacityStatusAndEstimate() throws IOException {
        write("/sys/class/power_supply/AC/type", "Mains\n");
        write("/sys/class/power_supply/AC/online", "0\n");
        write("/sys/class/power_supply/BAT0/type", "Battery\n");
        write("/sys/class/power_supply/BAT0/capacity", "63\n");
        write("/sys/class/power_supply/BAT0/status", "Discharging\n");
        write("/sys/class/power_supply/BAT0/charge_now", "3000000\n");
        write("/sys/class/power_supply/BAT0/current_now", "1500000\n");
        PowerInfo power = provider.power();
        assertTrue(power.battery().isPresent());
        Battery b = power.battery().get();
        assertEquals(63, b.percent());
        assertTrue(!b.charging());
        assertEquals(7200, b.secondsRemaining());
    }

    @Test
    void thermalZonesGiveCelsius() throws IOException {
        write("/sys/class/thermal/thermal_zone0/type", "x86_pkg_temp\n");
        write("/sys/class/thermal/thermal_zone0/temp", "52300\n");
        List<ThermalSensor> sensors = provider.thermal();
        assertEquals(1, sensors.size());
        assertEquals("x86_pkg_temp", sensors.getFirst().name());
        assertEquals(52.3, sensors.getFirst().celsius(), 0.01);
    }

    @Test
    void volumesComeFromMounts() throws IOException {
        write("/proc/mounts", """
                /dev/nvme0n1p2 / ext4 rw,relatime 0 0
                proc /proc proc rw,nosuid 0 0
                tmpfs /run tmpfs rw 0 0
                """);
        List<DiskVolume> volumes = provider.volumes();
        // only "/" resolves to a real filesystem on the test machine
        assertTrue(volumes.stream().anyMatch(v -> v.mount().equals("/") && v.fileSystem().equals("ext4")));
        assertTrue(volumes.stream().noneMatch(v -> v.fileSystem().equals("proc")));
    }
}
