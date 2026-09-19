package pl.volkernemrod.mcalarm.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Centrala alarmowa — główny blok systemu. GAMEPLAY_SPEC rozdz. 4,
 * README rozdz. 3.1.
 */
public class Centrala {

    private final UUID id;
    private final UUID wlasciciel;
    private String nazwa;
    private UUID strefaId; // null dopóki strefa nie zostanie zdefiniowana
    private TrybPracy tryb;
    private final Set<UUID> zaufani = new HashSet<>();

    // Lokalizacja fizycznego bloku centrali w świecie — potrzebna, żeby
    // listener zniszczenia bloku mógł rozpoznać, która centrala to jaki blok.
    private String swiat;
    private int x, y, z;

    public Centrala(UUID id, UUID wlasciciel, String nazwa) {
        this.id = id;
        this.wlasciciel = wlasciciel;
        this.nazwa = nazwa;
        this.tryb = TrybPracy.ROZBROJONY; // domyślny stan po utworzeniu — GAMEPLAY_SPEC rozdz. 73
    }

    /** Rola gracza względem tej centrali (właściciel/zaufany/obcy). */
    public Rola rolaGracza(UUID gracz) {
        if (gracz == null) {
            return Rola.OBCY;
        }
        if (gracz.equals(wlasciciel)) {
            return Rola.WLASCICIEL;
        }
        if (zaufani.contains(gracz)) {
            return Rola.ZAUFANY;
        }
        return Rola.OBCY;
    }

    public void dodajZaufanego(UUID gracz) {
        zaufani.add(gracz);
    }

    public void usunZaufanego(UUID gracz) {
        zaufani.remove(gracz);
    }

    public void setLokalizacja(String swiat, int x, int y, int z) {
        this.swiat = swiat;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean maLokalizacje() {
        return swiat != null;
    }

    public UUID getId() { return id; }
    public UUID getWlasciciel() { return wlasciciel; }
    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }
    public UUID getStrefaId() { return strefaId; }
    public void setStrefaId(UUID strefaId) { this.strefaId = strefaId; }
    public TrybPracy getTryb() { return tryb; }
    public void setTryb(TrybPracy tryb) { this.tryb = tryb; }
    public Set<UUID> getZaufani() { return zaufani; }
    public String getSwiat() { return swiat; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}
