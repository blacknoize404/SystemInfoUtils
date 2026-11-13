package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Unknown platforms: only what plain Java can answer — cores, the filesystem
 * roots and the {@code os.*} properties. Everything else reports empty.
 */
final class Fallback implements SystemInfoProvider {

    @Override
    public CpuInfo cpuInfo() {
        int cores = Runtime.getRuntime().availableProcessors();
        return new CpuInfo(System.getProperty("os.arch", ""), cores, cores);
    }

    @Override
    public Optional<CpuTimes> cpuTimes() {
        return Optional.empty();
    }

    @Override
    public double cpuFrequencyMhz() {
        return -1;
    }

    @Override
    public MemoryInfo memory() {
        return new MemoryInfo(-1, -1, -1, -1);
    }

    @Override
    public List<DiskVolume> volumes() {
        return java.util.Arrays.stream(File.listRoots())
                .map(root -> new DiskVolume(root.getPath(), "", "", root.getTotalSpace(), root.getFreeSpace()))
                .toList();
    }

    @Override
    public Optional<long[]> diskIoCounters() {
        return Optional.empty();
    }

    @Override
    public PowerInfo power() {
        return new PowerInfo(true, Optional.empty());
    }

    @Override
    public HostInfo host() {
        return new HostInfo(System.getProperty("os.name", ""), System.getProperty("os.version", ""),
                System.getProperty("os.arch", ""), Providers.hostname());
    }

    @Override
    public long uptimeSeconds() {
        return -1;
    }

    @Override
    public List<ThermalSensor> thermal() {
        return List.of();
    }
}
