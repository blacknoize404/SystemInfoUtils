package io.github.blacknoize404.systemInfoUtils;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuInfo;
import io.github.blacknoize404.systemInfoUtils.host.HostInfo;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.providers.CpuTimes;
import io.github.blacknoize404.systemInfoUtils.providers.SystemInfoProvider;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskVolume;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMonitorTest {

    /** Counters grow on every read; the second tick must show load and rates. */
    private static final class Script implements SystemInfoProvider {
        final AtomicInteger reads = new AtomicInteger();

        @Override
        public CpuInfo cpuInfo() {
            return new CpuInfo("fake", 1, 1);
        }

        @Override
        public Optional<CpuTimes> cpuTimes() {
            int n = reads.incrementAndGet();
            return Optional.of(new CpuTimes(new long[]{50L * n}, new long[]{50L * n}));
        }

        @Override
        public double cpuFrequencyMhz() {
            return 3000;
        }

        @Override
        public MemoryInfo memory() {
            return new MemoryInfo(1000, 500, 0, 0);
        }

        @Override
        public List<DiskVolume> volumes() {
            return List.of();
        }

        @Override
        public Optional<long[]> diskIoCounters() {
            int n = reads.get();
            return Optional.of(new long[]{1000L * n, 2000L * n});
        }

        @Override
        public PowerInfo power() {
            return new PowerInfo(true, Optional.empty());
        }

        @Override
        public HostInfo host() {
            return new HostInfo("test", "1", "x86", "host");
        }

        @Override
        public long uptimeSeconds() {
            return 1;
        }

        @Override
        public List<ThermalSensor> thermal() {
            return List.of();
        }
    }

    @Test
    void secondSampleShowsLoadAndDiskRates() throws InterruptedException {
        Script script = new Script();
        try (SystemMonitor monitor = new SystemMonitor(script)) {
            monitor.setIntervalSeconds(1);
            monitor.start();
            // let at least two ticks happen
            long deadline = System.currentTimeMillis() + 4000;
            while (monitor.history().size() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            assertTrue(monitor.history().size() >= 2);
            SystemSample s = monitor.sample();
            assertNotNull(s);
            // each read adds 50 busy / 50 idle → 0.5
            assertEquals(0.5, s.cpu().total(), 0.001);
            assertEquals(3000, s.cpu().frequencyMhz());
            assertTrue(s.disk().isPresent());
            assertTrue(s.disk().get().readBytesPerSecond() > 0);
            assertEquals(0.5, s.memory().usedPercent(), 0.001);
        }
    }

    @Test
    void stopHaltsSampling() throws InterruptedException {
        try (SystemMonitor monitor = new SystemMonitor(new Script())) {
            monitor.start();
            Thread.sleep(1500);
            monitor.stop();
            int size = monitor.history().size();
            Thread.sleep(1300);
            assertEquals(size, monitor.history().size());
        }
    }
}
