package io.github.blacknoize404.systemInfoUtils;

import io.github.blacknoize404.systemInfoUtils.cpu.CpuLoad;
import io.github.blacknoize404.systemInfoUtils.providers.CpuTimes;
import io.github.blacknoize404.systemInfoUtils.providers.SystemInfoProvider;
import io.github.blacknoize404.systemInfoUtils.storage.DiskActivity;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;

import java.time.Instant;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Samples the machine on a schedule and publishes each {@link SystemSample}
 * through {@link #sampleProperty()} — from the sampling thread, so UI
 * consumers must hop to their own thread. The latest ones are kept in
 * {@link #history()} so late-attaching views can rehydrate.
 * <p>
 * Rates are the delta between consecutive reads of the OS's cumulative
 * counters (CPU times, disk bytes); on platforms that keep no counters the
 * provider's own interval reading is used instead. The interval is adjustable
 * while running ({@link #intervalSecondsProperty()}).
 */
public final class SystemMonitor implements AutoCloseable {

    private static final int HISTORY_LIMIT = 2048;

    private final SystemInfoProvider provider;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "SystemInfoUtils-monitor");
                thread.setDaemon(true);
                return thread;
            });
    private final ReadOnlyObjectWrapper<SystemSample> sample = new ReadOnlyObjectWrapper<>();
    private final IntegerProperty intervalSeconds = new SimpleIntegerProperty(1);
    private final Deque<SystemSample> history = new ConcurrentLinkedDeque<>();

    private volatile ScheduledFuture<?> task;
    private volatile boolean running;
    /** The first sampling failure, kept for diagnosis — reads keep retrying. */
    volatile Throwable debug;
    private CpuTimes lastCpuTimes;
    private long[] lastDiskCounters;
    private Instant lastDiskRead;

    public SystemMonitor() {
        this(SystemInfo.provider());
    }

    /** For tests: any provider. */
    public SystemMonitor(SystemInfoProvider provider) {
        this.provider = provider;
        intervalSeconds.addListener((observable, oldValue, newValue) -> {
            if (running) schedule(newValue.intValue());
        });
    }

    /** Starts sampling; the first sample is taken immediately. Idempotent. */
    public synchronized void start() {
        if (running) return;
        running = true;
        schedule(intervalSeconds.get());
    }

    /** Stops sampling; the history and the last sample stay available. */
    public synchronized void stop() {
        running = false;
        ScheduledFuture<?> current = task;
        if (current != null) current.cancel(false);
    }

    /** Whether the sampler is running. */
    public boolean isRunning() {
        return running;
    }

    private void schedule(int seconds) {
        ScheduledFuture<?> current = task;
        if (current != null) current.cancel(false);
        task = executor.scheduleAtFixedRate(this::tick, 0, Math.max(1, seconds), TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            Instant now = Instant.now();
            CpuLoad cpu = cpuLoad(now);
            Optional<DiskActivity> disk = diskActivity(now);
            double mhz = provider.cpuFrequencyMhz();
            if (mhz > 0) cpu = new CpuLoad(cpu.total(), cpu.perCore(), mhz);
            publish(new SystemSample(now, cpu, provider.memory(), disk, provider.power(), provider.thermal()));
        } catch (Throwable t) {
            // a broken read must not kill the scheduler thread
            if (debug == null) {
                debug = t;
                t.printStackTrace();
            }
        }
    }

    private CpuLoad cpuLoad(Instant now) {
        Optional<CpuTimes> times = provider.cpuTimes();
        if (times.isPresent()) {
            CpuTimes previous = lastCpuTimes;
            lastCpuTimes = times.get();
            if (previous == null) return new CpuLoad(0, new double[0], -1);
            return previous.loadTo(times.get());
        }
        lastCpuTimes = null;
        return provider.cpuLoadNow().orElse(new CpuLoad(-1, new double[0], -1));
    }

    private Optional<DiskActivity> diskActivity(Instant now) {
        Optional<long[]> counters = provider.diskIoCounters();
        if (counters.isPresent()) {
            long[] previous = lastDiskCounters;
            Instant previousRead = lastDiskRead;
            lastDiskCounters = counters.get();
            lastDiskRead = now;
            if (previous == null || previousRead == null) return Optional.empty();
            double seconds = Math.max(0.001, (now.toEpochMilli() - previousRead.toEpochMilli()) / 1000.0);
            double read = Math.max(0, counters.get()[0] - previous[0]) / seconds;
            double write = Math.max(0, counters.get()[1] - previous[1]) / seconds;
            return Optional.of(new DiskActivity(read, write));
        }
        lastDiskCounters = null;
        return provider.diskRatesNow();
    }

    private void publish(SystemSample next) {
        history.addLast(next);
        while (history.size() > HISTORY_LIMIT) history.pollFirst();
        sample.set(next);
    }

    /** The latest sample; changes on every tick. */
    public ReadOnlyObjectProperty<SystemSample> sampleProperty() {
        return sample.getReadOnlyProperty();
    }

    /** The latest sample, or {@code null} before the first tick. */
    public SystemSample sample() {
        return sample.get();
    }

    /** Recent samples, oldest first; capped at {@value #HISTORY_LIMIT}. */
    public Deque<SystemSample> history() {
        return history;
    }

    /** Seconds between samples; applies while running. Minimum 1. */
    public IntegerProperty intervalSecondsProperty() {
        return intervalSeconds;
    }

    public int getIntervalSeconds() {
        return intervalSeconds.get();
    }

    public void setIntervalSeconds(int seconds) {
        intervalSeconds.set(seconds);
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }
}
