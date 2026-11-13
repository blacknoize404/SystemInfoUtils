package io.github.blacknoize404.systemInfoUtils;

import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;

/**
 * A small console demo: prints the static facts, then one live line per
 * second with CPU, memory, disk and battery. {@code Main 10} runs ten samples;
 * no argument runs five.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        int samples = args.length > 0 ? Integer.parseInt(args[0]) : 5;

        var host = SystemInfo.host();
        var cpu = SystemInfo.cpu();
        System.out.printf("%s %s (%s) — %s%n", host.osName(), host.osVersion(), host.architecture(), host.hostname());
        System.out.printf("CPU: %s — %d núcleos, %d hilos @ %.0f MHz%n",
                cpu.model(), cpu.physicalCores(), cpu.logicalCores(), SystemInfo.cpuFrequencyMhz());
        for (var v : SystemInfo.volumes()) {
            System.out.printf("  %-6s %-20s %-8s %5.1f / %5.1f GB%n", v.mount(), v.label(), v.fileSystem(),
                    (v.totalBytes() - v.freeBytes()) / 1e9, v.totalBytes() / 1e9);
        }
        for (ThermalSensor t : SystemInfo.thermal()) {
            System.out.printf("  %-24s %.1f °C%n", t.name(), t.celsius());
        }
        System.out.println("— en vivo —");

        try (SystemMonitor monitor = new SystemMonitor()) {
            monitor.start();
            for (int i = 0; i < samples; i++) {
                Thread.sleep(1000);
                var s = monitor.sample();
                if (s == null) continue;
                var mem = s.memory();
                var power = s.power();
                String disk = s.disk().map(d -> String.format("disco %5.1f/%5.1f MB/s",
                        d.readBytesPerSecond() / 1e6, d.writeBytesPerSecond() / 1e6)).orElse("disco —    ");
                String batt = power.battery().map(b -> String.format("bat %3.0f%%%s",
                        b.percent(), b.charging() ? "⚡" : " ")).orElse("AC ");
                System.out.printf("cpu %5.1f%% | mem %5.1f%% | %s | %s%n",
                        s.cpu().total() * 100, mem.usedPercent() * 100, disk, batt);
            }
        }
    }
}
