package io.github.blacknoize404.systemInfoUtils.power;

import java.util.Optional;

/**
 * Where the machine's power comes from.
 *
 * @param onAcPower whether it is plugged to the mains
 * @param battery   the battery state; empty on desktops and when the platform
 *                  reports no battery
 */
public record PowerInfo(boolean onAcPower, Optional<Battery> battery) {
}
