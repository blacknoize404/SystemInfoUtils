# SystemInfoUtils

> A small Java library that reads the machine in real time — CPU load per core, memory, disks,
> battery and temperatures — on Windows, macOS and Linux, without extra privileges.

## What it does

- **One-shot snapshots** through `SystemInfo`: host facts (OS name and real version, architecture,
  hostname), CPU model and core counts, RAM and swap, mounted volumes, power and thermal zones.
- **Live sampling** through `SystemMonitor`: a background thread publishes a `SystemSample` every
  interval — CPU busy fraction (total and per core), memory, disk throughput and battery — via
  `sampleProperty()`, keeping a bounded history for late-attaching views.
- **Honest about gaps**: metrics a platform can't report come back as empty `Optional`s instead of
  fabricated numbers — Windows disk counters (PDH names are localized; `IOCTL_DISK_PERFORMANCE`
  needs admin) and temperatures outside Linux (`/sys` zones) are not exposed in this version.

## How it reads each OS

| Metric | Windows | macOS | Linux |
|---|---|---|---|
| CPU load | `NtQuerySystemInformation` per core | `top -l 2` (interval) | `/proc/stat` per core |
| CPU model / cores | registry + `GetLogicalProcessorInformation` | `sysctl` | `/proc/cpuinfo` |
| Memory | `GlobalMemoryStatusEx` | `vm_stat` + `hw.memsize` | `/proc/meminfo` |
| Volumes | `GetVolumeInformationW` | `df -kP` | `/proc/mounts` |
| Disk I/O | — | `iostat -c 2` | `/proc/diskstats` |
| Battery | `GetSystemPowerStatus` | `pmset -g batt` | `/sys/class/power_supply` |
| Uptime | `GetTickCount64` | `kern.boottime` | `/proc/uptime` |
| Temperatures | PDH thermal-zone counter → LibreHardwareMonitor/OpenHardwareMonitor WMI → `MSAcpi_ThermalZoneTemperature` (needs admin) | — | `/sys/class/thermal` |

On Windows the thermal sources are probed in order once, then refreshed every ten seconds:
the PDH counter `PdhAddEnglishCounter` keeps the path language-neutral; the hardware-monitor
namespaces only exist while those apps run; the ACPI zone answers only for elevated processes
on machines whose firmware fills it. Boxes with none of them get an empty list.

On Windows the native calls go through the foreign-function API — no native library ships — so run
with `--enable-native-access=SystemInfoUtils` (like `NetworkUtils`'s `TrafficMonitor`).

## Using it

```java
import io.github.blacknoize404.systemInfoUtils.*;

// Static facts, read once
HostInfo host = SystemInfo.host();          // "Windows 11", 10.0.26200, amd64, DESKTOP-…
CpuInfo cpu = SystemInfo.cpu();             // "AMD Ryzen 7 6800H…", 8 cores / 16 threads

// One-shot readings
MemoryInfo mem = SystemInfo.memory();
long usedPercent = Math.round(mem.usedPercent() * 100);
PowerInfo power = SystemInfo.power();       // battery 44%, discharging, ~48 min left

// Continuous sampling (1 s by default, adjustable while running)
try (SystemMonitor monitor = new SystemMonitor()) {
    monitor.setIntervalSeconds(2);
    monitor.sampleProperty().addListener((obs, old, s) ->
            System.out.printf("cpu %.0f%% | mem %.0f%%%n",
                    s.cpu().total() * 100, s.memory().usedPercent() * 100));
    monitor.start();
}
```

`Main` prints a console demo: static facts, then one live line per second.

## Module

`module SystemInfoUtils` — `javafx.base` (headless, for the property) and `org.jetbrains.annotations`.
Java 27. The `io.github.blacknoize404.systemInfoUtils.providers` package is internal: providers
return `Optional.empty()` for anything the platform cannot offer.

## Not in this version

Per-process CPU/RAM listings, GPU usage, fan speeds, Windows disk I/O counters, macOS/Linux
per-core load (macOS reports the total only). The provider seam is there — these slot in without
changing the public API.
