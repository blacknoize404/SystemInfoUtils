package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.storage.DiskActivity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacOsShellTest {

    @Test
    void topSecondSampleIsTheLiveOne() {
        String out = """
                Processes: 450 total
                CPU usage: 12.3% user, 8.1% sys, 79.6% idle
                PhysMem: 16G used
                CPU usage: 33.3% user, 16.7% sys, 50.0% idle
                """;
        Optional<CpuLoad> load = MacOsShell.parseTop(out);
        assertTrue(load.isPresent());
        assertEquals(0.5, load.get().total(), 0.001);
    }

    @Test
    void topWithoutCpuLineIsEmpty() {
        assertTrue(MacOsShell.parseTop("Processes: 1 total\n").isEmpty());
        assertTrue(MacOsShell.parseTop(null).isEmpty());
    }

    @Test
    void vmStatReadsPageCounts() {
        String out = """
                Mach Virtual Memory Statistics: (page size of 16384 bytes)
                Pages free:                             120000.
                Pages active:                           640000.
                Pages inactive:                         200000.
                Pages speculative:                       40000.
                """;
        assertEquals(120000, MacOsShell.vmStat(out, "Pages free"));
        assertEquals(200000, MacOsShell.vmStat(out, "Pages inactive"));
        assertEquals(40000, MacOsShell.vmStat(out, "Pages speculative"));
    }

    @Test
    void swapusageFieldsParse() {
        String out = "total = 1024.00M  used = 512.25M  free = 511.75M";
        assertEquals(1024.0, MacOsShell.mib(out, "total"), 0.001);
        assertEquals(511.75, MacOsShell.mib(out, "free"), 0.001);
    }

    @Test
    void pmsetDischargingBattery() {
        String out = """
                Now drawing from 'Battery Power'
                 -InternalBattery-0 (id=12345)	63%; discharging; 4:30 remaining present: true
                """;
        PowerInfo power = MacOsShell.parsePmset(out);
        assertTrue(!power.onAcPower());
        assertTrue(power.battery().isPresent());
        assertEquals(63, power.battery().get().percent());
        assertTrue(!power.battery().get().charging());
        assertEquals(4 * 3600 + 30 * 60, power.battery().get().secondsRemaining());
    }

    @Test
    void pmsetChargingOnAc() {
        String out = """
                Now drawing from 'AC Power'
                 -InternalBattery-0 (id=12345)	80%; charging; 0:00 remaining present: true
                """;
        PowerInfo power = MacOsShell.parsePmset(out);
        assertTrue(power.onAcPower());
        assertTrue(power.battery().get().charging());
    }

    @Test
    void iostatLastSampleWins() {
        String out = """
                          disk0       cpu     load average
                    KB/t tps  MB/s  us sy id   1m    5m   15m
                    4.00  10  0.04   5  3 92  1.50  1.60  1.70
                    8.00  20  0.16   8  4 88  1.40  1.55  1.65
                """;
        Optional<DiskActivity> rate = MacOsShell.parseIostat(out);
        assertTrue(rate.isPresent());
        assertEquals(0.16 * 1024 * 1024, rate.get().readBytesPerSecond(), 1);
    }

    @Test
    void windowsRegParser() {
        String out = """
                
                HKEY_LOCAL_MACHINE\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0
                    ProcessorNameString    REG_SZ    AMD Ryzen 7 5800X 8-Core Processor
                
                """;
        assertEquals("AMD Ryzen 7 5800X 8-Core Processor",
                WindowsFfm.parseRegSz(out, "ProcessorNameString"));
    }
}
