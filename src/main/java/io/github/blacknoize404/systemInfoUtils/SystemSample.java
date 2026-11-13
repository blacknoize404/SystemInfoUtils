package io.github.blacknoize404.systemInfoUtils;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.memory.MemoryInfo;
import io.github.blacknoize404.systemInfoUtils.power.PowerInfo;
import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import io.github.blacknoize404.systemInfoUtils.storage.DiskActivity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One live reading of the whole machine.
 *
 * @param time         when the sample was taken
 * @param cpu          load over the interval that ended at {@code time}
 * @param memory       RAM and swap at that instant
 * @param disk         disk throughput over the interval; empty where the OS
 *                     keeps no readable counters (Windows in this version)
 * @param power        AC line and battery
 * @param thermal      temperature zones; empty where they need privileges
 */
public record SystemSample(Instant time, CpuLoad cpu, MemoryInfo memory,
                           Optional<DiskActivity> disk, PowerInfo power,
                           List<ThermalSensor> thermal) {
}
