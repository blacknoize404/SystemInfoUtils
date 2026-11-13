package io.github.blacknoize404.systemInfoUtils;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live smoke test: reads the real machine through the platform provider. On
 * Windows this exercises the FFM calls for real.
 */
class SystemInfoTest {

    @Test
    void platformIsDetected() {
        assertNotEquals(Platform.OTHER, SystemInfo.platform());
    }

    @Test
    void hostHasOsAndHostname() {
        HostInfo host = SystemInfo.host();
        assertFalse(host.osName().isBlank());
        assertFalse(host.hostname().isBlank());
        assertFalse(host.architecture().isBlank());
    }

    @Test
    void cpuReportsModelAndCores() {
        CpuInfo cpu = SystemInfo.cpu();
        assertFalse(cpu.model().isBlank());
        assertTrue(cpu.logicalCores() > 0);
        assertTrue(cpu.physicalCores() > 0);
        assertTrue(cpu.logicalCores() >= cpu.physicalCores());
    }

    @Test
    void cpuLoadIsAFraction() {
        CpuLoad load = SystemInfo.cpuLoad();
        assertTrue(load.total() >= 0 && load.total() <= 1,
                "total load " + load.total() + " out of range");
    }

    @Test
    void memoryIsPositive() {
        MemoryInfo mem = SystemInfo.memory();
        assertTrue(mem.totalBytes() > 0);
        assertTrue(mem.availableBytes() > 0);
        assertTrue(mem.availableBytes() <= mem.totalBytes());
    }

    @Test
    void volumesExistAndHaveSpace() {
        assertFalse(SystemInfo.volumes().isEmpty());
        assertTrue(SystemInfo.volumes().stream().allMatch(v -> v.totalBytes() > 0));
    }

    @Test
    void uptimeIsPositive() {
        assertTrue(SystemInfo.uptimeSeconds() > 0);
    }

    @Test
    void powerAnswers() {
        assertNotNull(SystemInfo.power());
    }
}
