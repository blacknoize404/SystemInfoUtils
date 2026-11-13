package io.github.blacknoize404.systemInfoUtils.providers;

import io.github.blacknoize404.systemInfoUtils.sensors.ThermalSensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsers de las vías WMI de temperatura + llamada real (debe no lanzar nunca). */
class WindowsFfmTest {

    @Test
    void pipeSensorsParsesNameAndValue() {

        List<ThermalSensor> sensors = WindowsFfm.parsePipeSensors(
                "CPU Core|65.4\nGPU Core|71.0\nbasura-sin-separador\nSSD|42.25\n");

        assertEquals(3, sensors.size());
        assertEquals("CPU Core", sensors.get(0).name());
        assertEquals(65.4, sensors.get(0).celsius(), 0.01);
        assertEquals("GPU Core", sensors.get(1).name());
        assertEquals(42.25, sensors.get(2).celsius(), 0.01);

    }

    @Test
    void acpiZonesConvertTenthsOfKelvin() {

        List<ThermalSensor> sensors = WindowsFfm.parseAcpiZones(
                "ACPI\\ThermalZone\\TZ00_0|2982\nACPI\\ThermalZone\\TZ01_0|3105\n");

        assertEquals(2, sensors.size());
        assertEquals("TZ00_0", sensors.get(0).name());
        assertEquals(25.05, sensors.get(0).celsius(), 0.01);
        assertEquals("TZ01_0", sensors.get(1).name());
        assertEquals(37.35, sensors.get(1).celsius(), 0.01);

    }

    @Test
    void nullOrEmptyOutputYieldsNoSensors() {

        assertTrue(WindowsFfm.parsePipeSensors(null).isEmpty());
        assertTrue(WindowsFfm.parseAcpiZones(null).isEmpty());
        assertTrue(WindowsFfm.parsePipeSensors("\n \n").isEmpty());

    }

    /** La lectura real no lanza aunque el equipo no exponga ninguna vía (este devuelve vacío). */
    @Test
    void thermalNeverThrows() {

        List<ThermalSensor> sensors = SystemInfoProvider.create().thermal();
        assertNotNull(sensors);
        sensors.forEach(s -> assertTrue(s.celsius() > -100 && s.celsius() < 200));

    }

}
