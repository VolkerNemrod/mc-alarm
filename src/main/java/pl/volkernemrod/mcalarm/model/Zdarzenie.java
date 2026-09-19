package pl.volkernemrod.mcalarm.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Pojedyncze zarejestrowane zdarzenie. GAMEPLAY_SPEC rozdz. 14 i dalsze,
 * README rozdz. 8.
 *
 * gracz == null oznacza "Sprawca: NIEZNANY" (GAMEPLAY_SPEC rozdz. 24, 66) —
 * plugin nigdy nie zgaduje sprawcy, jeśli nie ma wiarygodnej informacji.
 */
public class Zdarzenie {

    private final UUID id;
    private final UUID centralaId;
    private final UUID strefaId;
    private final UUID incydentId; // może być null — zdarzenie poza incydentem
    private final UUID gracz;      // może być null — sprawca nieznany
    private final TypZdarzenia typ;
    private final Waznosc waznosc;
    private final String szczegoly; // np. "wyjął 4 × DIAMENT", pozycja, nazwa bloku
    private final String swiat;
    private final int x, y, z;
    private final Instant czas;

    public Zdarzenie(UUID id, UUID centralaId, UUID strefaId, UUID incydentId,
                      UUID gracz, TypZdarzenia typ, Waznosc waznosc,
                      String szczegoly, String swiat, int x, int y, int z,
                      Instant czas) {
        this.id = id;
        this.centralaId = centralaId;
        this.strefaId = strefaId;
        this.incydentId = incydentId;
        this.gracz = gracz;
        this.typ = typ;
        this.waznosc = waznosc;
        this.szczegoly = szczegoly;
        this.swiat = swiat;
        this.x = x;
        this.y = y;
        this.z = z;
        this.czas = czas;
    }

    public boolean sprawcaNieznany() {
        return gracz == null;
    }

    public UUID getId() { return id; }
    public UUID getCentralaId() { return centralaId; }
    public UUID getStrefaId() { return strefaId; }
    public UUID getIncydentId() { return incydentId; }
    public UUID getGracz() { return gracz; }
    public TypZdarzenia getTyp() { return typ; }
    public Waznosc getWaznosc() { return waznosc; }
    public String getSzczegoly() { return szczegoly; }
    public String getSwiat() { return swiat; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public Instant getCzas() { return czas; }
}
