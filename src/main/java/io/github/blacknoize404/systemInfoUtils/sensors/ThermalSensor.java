package io.github.blacknoize404.systemInfoUtils.sensors;

/**
 * One temperature reading. Only Linux exposes zones without extra privileges
 * today; other platforms report an empty list.
 *
 * @param name    what it measures ({@code x86_pkg_temp}, {@code nvme})
 * @param celsius the reading
 */
public record ThermalSensor(String name, double celsius) {
}
