package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpuTimesTest {

    @Test
    void loadIsTheDeltaBetweenReads() {
        CpuTimes first = new CpuTimes(new long[]{100, 50, 50}, new long[]{100, 50, 50});
        CpuTimes later = new CpuTimes(new long[]{150, 75, 75}, new long[]{200, 150, 50});
        CpuLoad load = first.loadTo(later);
        // total: +50 idle, +100 busy → 2/3
        assertEquals(2.0 / 3, load.total(), 0.001);
        // core0: +25 idle, +100 busy → 0.8; core1: +25, +0 → 0
        assertEquals(2, load.perCore().length);
        assertEquals(0.8, load.perCore()[0], 0.001);
        assertEquals(0.0, load.perCore()[1], 0.001);
    }

    @Test
    void identicalReadsGiveZero() {
        CpuTimes t = new CpuTimes(new long[]{10}, new long[]{10});
        assertEquals(0, t.loadTo(t).total());
    }

    @Test
    void shrinkingCounterSetsAreTruncated() {
        CpuTimes first = new CpuTimes(new long[]{0, 0, 0, 0}, new long[]{0, 0, 0, 0});
        CpuTimes later = new CpuTimes(new long[]{10}, new long[]{10});
        CpuLoad load = first.loadTo(later);
        assertEquals(0.5, load.total(), 0.001);
        assertEquals(0, load.perCore().length);
    }
}
