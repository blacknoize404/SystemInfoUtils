package io.github.blacknoize404.systemInfoUtils.cpu;

/**
 * A CPU load reading: the fraction of the interval each core was busy, in
 * {@code [0, 1]}.
 *
 * @param total        busy fraction of the whole processor (all cores combined)
 * @param perCore      busy fraction per logical core; empty when the platform
 *                     only reports the total (macOS)
 * @param frequencyMhz clock speed at the instant of the sample, or {@code -1}
 *                     when unknown
 */
public record CpuLoad(double total, double[] perCore, double frequencyMhz) {
}
