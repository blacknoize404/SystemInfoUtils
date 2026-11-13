package io.github.blacknoize404.systemInfoUtils.power;

/**
 * The state of the system battery.
 *
 * @param percent          charge left in {@code [0, 100]}, or {@code -1} when unknown
 * @param charging         whether it is taking charge right now
 * @param secondsRemaining estimated battery life left in seconds; {@code -1}
 *                         when the system gives no estimate (usually while charging)
 */
public record Battery(double percent, boolean charging, long secondsRemaining) {
}
