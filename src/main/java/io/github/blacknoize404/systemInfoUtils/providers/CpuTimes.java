package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;

import java.util.Arrays;

/**
 * Raw, cumulative CPU tick counters the way the OS reports them. Element
 * {@code 0} is the whole processor; {@code 1..n} are the logical cores. A load
 * percentage only exists as the ratio between two reads — {@link #loadTo(CpuTimes)}
 * diffs two samples.
 *
 * @param idle ticks spent idle
 * @param busy ticks spent working
 */
public record CpuTimes(long[] idle, long[] busy) {

    /** The busy fraction in {@code [0, 1]} between this read and {@code later}. */
    public CpuLoad loadTo(CpuTimes later) {
        int n = Math.min(Math.min(idle.length, later.idle.length), Math.min(busy.length, later.busy.length));
        double[] fractions = new double[Math.max(0, n - 1)];
        double total = 0;
        for (int i = 0; i < n; i++) {
            long dIdle = later.idle[i] - idle[i];
            long dBusy = later.busy[i] - busy[i];
            long dAll = dIdle + dBusy;
            double f = dAll <= 0 ? 0 : (double) dBusy / dAll;
            if (i == 0) total = f;
            else fractions[i - 1] = f;
        }
        return new CpuLoad(total, fractions, -1);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CpuTimes other
                && Arrays.equals(idle, other.idle) && Arrays.equals(busy, other.busy);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(idle) + Arrays.hashCode(busy);
    }
}
