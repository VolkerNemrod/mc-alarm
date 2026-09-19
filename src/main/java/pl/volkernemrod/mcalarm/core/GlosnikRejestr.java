package pl.volkernemrod.mcalarm.core;

import pl.volkernemrod.mcalarm.model.Glosnik;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rejestr wszystkich głośników w pamięci — cache żeby listener nie odpytywał
 * SQLite przy każdym kliknięciu bloku. Plan działania — punkt B (głośnik).
 */
public class GlosnikRejestr {

    /** Klucz: "swiat;x;y;z" → głośnik. */
    private final Map<String, Glosnik> poLokalizacji = new HashMap<>();

    /** Klucz: UUID głośnika → głośnik. */
    private final Map<UUID, Glosnik> poId = new HashMap<>();

    public void dodaj(Glosnik glosnik) {
        poLokalizacji.put(glosnik.kluczLokalizacji(), glosnik);
        poId.put(glosnik.getId(), glosnik);
    }

    public Optional<Glosnik> znajdzPoLokalizacji(String swiat, int x, int y, int z) {
        return Optional.ofNullable(poLokalizacji.get(swiat + ";" + x + ";" + y + ";" + z));
    }

    public Optional<Glosnik> znajdzPoId(UUID id) {
        return Optional.ofNullable(poId.get(id));
    }

    public void usunPoLokalizacji(String swiat, int x, int y, int z) {
        Glosnik g = poLokalizacji.remove(swiat + ";" + x + ";" + y + ";" + z);
        if (g != null) {
            poId.remove(g.getId());
        }
    }

    public Collection<Glosnik> wszystkie() {
        return Collections.unmodifiableCollection(poId.values());
    }

    public void wczytaj(java.util.List<Glosnik> lista) {
        poLokalizacji.clear();
        poId.clear();
        for (Glosnik g : lista) {
            dodaj(g);
        }
    }
}
