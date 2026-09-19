package pl.volkernemrod.mcalarm.core;

import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Strefa;
import pl.volkernemrod.mcalarm.storage.CentralaRepository;
import pl.volkernemrod.mcalarm.storage.StrefaRepository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rejestr wszystkich central i stref w pamięci — cache, żeby listenery
 * zdarzeń (Etap 4: ruch gracza, bloki, kontenery) NIE odpytywały SQLite
 * przy każdym ticku/ruchu. Aktualizowany po każdej zmianie w bazie
 * (komendy /alarm, postawienie/zniszczenie bloku centrali).
 *
 * Wczytywany od zera z bazy przy starcie pluginu (wczytajZBazy). Plan
 * działania — Etap 4.
 */
public class CentralaRejestr {

    private final Map<UUID, Centrala> centrale = new HashMap<>();
    private final Map<UUID, Strefa> strefy = new HashMap<>();

    /** Odwrotny indeks: "swiat;x;y;z" bloku centrali -> id centrali. Do szybkiego onBlockBreak/onInteract. */
    private final Map<String, UUID> lokalizacjaDoCentrali = new HashMap<>();

    public void wczytajZBazy(CentralaRepository centralaRepository, StrefaRepository strefaRepository) throws SQLException {
        centrale.clear();
        strefy.clear();
        lokalizacjaDoCentrali.clear();

        for (Centrala c : centralaRepository.wszystkie()) {
            odswiezCentrala(c);
            if (c.getStrefaId() != null) {
                strefaRepository.znajdzPoId(c.getStrefaId()).ifPresent(this::odswiezStrefa);
            }
        }
    }

    public void odswiezCentrala(Centrala centrala) {
        centrale.put(centrala.getId(), centrala);
        if (centrala.maLokalizacje()) {
            lokalizacjaDoCentrali.put(kluczLokalizacji(centrala.getSwiat(), centrala.getX(), centrala.getY(), centrala.getZ()),
                    centrala.getId());
        }
    }

    public void odswiezStrefa(Strefa strefa) {
        strefy.put(strefa.getId(), strefa);
    }

    public void usunCentrala(UUID centralaId) {
        Centrala c = centrale.remove(centralaId);
        if (c != null) {
            if (c.getStrefaId() != null) {
                strefy.remove(c.getStrefaId());
            }
            if (c.maLokalizacje()) {
                lokalizacjaDoCentrali.remove(kluczLokalizacji(c.getSwiat(), c.getX(), c.getY(), c.getZ()));
            }
        }
    }

    public Optional<Centrala> getCentrala(UUID id) {
        return Optional.ofNullable(centrale.get(id));
    }

    public Optional<UUID> znajdzCentralaIdPoLokalizacji(String swiat, int x, int y, int z) {
        return Optional.ofNullable(lokalizacjaDoCentrali.get(kluczLokalizacji(swiat, x, y, z)));
    }

    public Optional<Strefa> getStrefaCentrali(UUID centralaId) {
        Centrala c = centrale.get(centralaId);
        if (c == null || c.getStrefaId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(strefy.get(c.getStrefaId()));
    }

    public Optional<Strefa> getStrefa(UUID strefaId) {
        return Optional.ofNullable(strefy.get(strefaId));
    }

    /** Wszystkie strefy w rejestrze — do sprawdzania wejścia/wyjścia gracza. */
    public Collection<Strefa> wszystkieStrefy() {
        return Collections.unmodifiableCollection(strefy.values());
    }

    /** Wszystkie centrale w rejestrze, niezależnie od właściciela — do użytku przez /alarm admin list (Etap 8). */
    public Collection<Centrala> wszystkieCentrale() {
        return Collections.unmodifiableCollection(centrale.values());
    }

    private static String kluczLokalizacji(String swiat, int x, int y, int z) {
        return swiat + ";" + x + ";" + y + ";" + z;
    }
}
