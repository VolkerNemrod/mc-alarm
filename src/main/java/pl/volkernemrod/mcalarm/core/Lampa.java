package pl.volkernemrod.mcalarm.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import pl.volkernemrod.mcalarm.model.Urzadzenie;

/**
 * Lampa centrali — wizualny sygnał alarmu (cząsteczki). Plan działania —
 * Etap 5. Docelowo (V1.1+) mogłaby zmieniać stan bloku (zielony/żółty/
 * czerwony) — na razie tylko cząsteczki przy alarmie, sterowane przez
 * AlarmEngine.
 */
public class Lampa implements Urzadzenie {

    private final Location lokalizacja;
    private boolean aktywna;

    public Lampa(Location lokalizacja) {
        this.lokalizacja = lokalizacja;
    }

    @Override
    public String getNazwa() {
        return "Lampa";
    }

    @Override
    public void aktywuj() {
        aktywna = true;
        // Etap B — efekty wizualne emitują wyłącznie głośniki (GlosnikListener).
        // Lampa nie emituje żadnych cząsteczek przy bloku centrali.
    }

    @Override
    public void dezaktywuj() {
        aktywna = false;
    }

    @Override
    public boolean isAktywne() {
        return aktywna;
    }
}
