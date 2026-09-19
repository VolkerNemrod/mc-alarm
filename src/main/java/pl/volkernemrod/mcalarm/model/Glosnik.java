package pl.volkernemrod.mcalarm.model;

import java.util.UUID;

/**
 * Głośnik VolkerNemrodAlarm — NOTE_BLOCK postawiony w strefie alarmowej.
 * Emituje dźwięk i/lub efekty wizualne podczas alarmu.
 * Konfiguracja per głośnik: dzwiek i efekty osobno ON/OFF.
 * Działa tylko gdy jest w aktywnej strefie alarmowej.
 *
 * Plan działania — punkt B z mem-palace (Etap GUI rozszerzeń).
 */
public class Glosnik {

    private final UUID id;
    private final UUID centralaId;
    private final String swiat;
    private final int x, y, z;

    private boolean dzwiekWlaczony;
    private boolean efektyWlaczone;

    public Glosnik(UUID id, UUID centralaId, String swiat, int x, int y, int z,
                   boolean dzwiekWlaczony, boolean efektyWlaczone) {
        this.id = id;
        this.centralaId = centralaId;
        this.swiat = swiat;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dzwiekWlaczony = dzwiekWlaczony;
        this.efektyWlaczone = efektyWlaczone;
    }

    public UUID getId() { return id; }
    public UUID getCentralaId() { return centralaId; }
    public String getSwiat() { return swiat; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public boolean isDzwiekWlaczony() { return dzwiekWlaczony; }
    public void setDzwiekWlaczony(boolean dzwiekWlaczony) { this.dzwiekWlaczony = dzwiekWlaczony; }

    public boolean isEfektyWlaczone() { return efektyWlaczone; }
    public void setEfektyWlaczone(boolean efektyWlaczone) { this.efektyWlaczone = efektyWlaczone; }

    /** Klucz lokalizacji do szybkiego wyszukiwania w rejestrze. */
    public String kluczLokalizacji() {
        return swiat + ";" + x + ";" + y + ";" + z;
    }
}
