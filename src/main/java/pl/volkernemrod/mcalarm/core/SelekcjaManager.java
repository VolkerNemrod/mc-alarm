package pl.volkernemrod.mcalarm.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trzyma tymczasowe zaznaczenie 2 narożników strefy dla każdego gracza
 * (podobnie jak WorldEdit) — tylko w pamięci, nie w bazie. Plan działania
 * — Etap 2/8, komenda /alarm zone.
 */
public class SelekcjaManager {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public void ustawPos1(Player gracz, Location loc) {
        pos1.put(gracz.getUniqueId(), loc);
    }

    public void ustawPos2(Player gracz, Location loc) {
        pos2.put(gracz.getUniqueId(), loc);
    }

    public Location getPos1(Player gracz) {
        return pos1.get(gracz.getUniqueId());
    }

    public Location getPos2(Player gracz) {
        return pos2.get(gracz.getUniqueId());
    }

    public void wyczysc(Player gracz) {
        pos1.remove(gracz.getUniqueId());
        pos2.remove(gracz.getUniqueId());
    }
}
