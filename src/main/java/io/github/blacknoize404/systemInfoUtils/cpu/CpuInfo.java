package io.github.blacknoize404.systemInfoUtils.cpu;

/**
 * Static facts about the processor.
 *
 * @param model          the brand name ({@code "AMD Ryzen 7 5800X"}), best effort
 * @param physicalCores  real cores; equals {@code logicalCores} when the platform can't tell them apart
 * @param logicalCores   hardware threads seen by the OS (≥ {@code physicalCores})
 */
public record CpuInfo(String model, int physicalCores, int logicalCores) {
}
