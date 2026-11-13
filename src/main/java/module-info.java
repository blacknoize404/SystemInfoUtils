/**
 * Información del computador en tiempo real: uso de la CPU por núcleo, memoria,
 * discos, batería, temperaturas y datos del sistema, en Windows, macOS y Linux.
 */
module SystemInfoUtils {
    requires javafx.base;
    requires org.jetbrains.annotations;
    exports io.github.blacknoize404.systemInfoUtils;
    exports io.github.blacknoize404.systemInfoUtils.cpu;
    exports io.github.blacknoize404.systemInfoUtils.memory;
    exports io.github.blacknoize404.systemInfoUtils.storage;
    exports io.github.blacknoize404.systemInfoUtils.power;
    exports io.github.blacknoize404.systemInfoUtils.host;
    exports io.github.blacknoize404.systemInfoUtils.sensors;
}
