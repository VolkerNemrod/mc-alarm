package pl.volkernemrod.mcalarm.core;

import org.bukkit.Location;
import org.bukkit.Sound;
import pl.volkernemrod.mcalarm.model.Urzadzenie;

/**
 * Syrena centrali — dźwiękowy sygnał alarmu. Plan działania — Etap 5.
 * Każde wywołanie aktywuj() to jeden "puls" dźwięku (sterowane cyklicznie
 * przez AlarmEngine, nie przez samo urządzenie).
 */
public class Syrena implements Urzadzenie {

    private final Location lokalizacja;
    private boolean aktywna;

    public Syrena(Location lokalizacja) {
        this.lokalizacja = lokalizacja;
    }

    @Override
    public String getNazwa() {
        return "Syrena";
    }

    @Override
    public void aktywuj() {
        aktywna = true;
        if (lokalizacja.getWorld() != null) {
            lokalizacja.getWorld().playSound(lokalizacja, Sound.BLOCK_BELL_USE, 3.0f, 0.6f);
            lokalizacja.getWorld().playSound(lokalizacja, Sound.ENTITY_WITHER_HURT, 1.0f, 1.4f);
        }
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
