package pl.volkernemrod.mcalarm.model;

import java.util.UUID;

/**
 * Chroniony obszar (prostokątny/kubiczny) przypisany do jednej centrali.
 * GAMEPLAY_SPEC rozdz. 6. V1: definiowana przez 2 przeciwległe narożniki.
 */
public class Strefa {

    private final UUID id;
    private UUID centralaId;
    private String nazwa;
    private String swiat;
    private int x1, y1, z1;
    private int x2, y2, z2;
    private boolean aktywna;

    public Strefa(UUID id, UUID centralaId, String nazwa, String swiat,
                  int x1, int y1, int z1, int x2, int y2, int z2) {
        this.id = id;
        this.centralaId = centralaId;
        this.nazwa = nazwa;
        this.swiat = swiat;
        this.x1 = Math.min(x1, x2);
        this.x2 = Math.max(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.y2 = Math.max(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.z2 = Math.max(z1, z2);
        this.aktywna = true;
    }

    /** Czy podany punkt (świat + współrzędne całkowite) leży w strefie. */
    public boolean zawiera(String swiat, int x, int y, int z) {
        return this.swiat.equals(swiat)
                && x >= x1 && x <= x2
                && y >= y1 && y <= y2
                && z >= z1 && z <= z2;
    }

    public UUID getId() { return id; }
    public UUID getCentralaId() { return centralaId; }
    public String getNazwa() { return nazwa; }
    public void setNazwa(String nazwa) { this.nazwa = nazwa; }
    public String getSwiat() { return swiat; }
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getZ2() { return z2; }
    public boolean isAktywna() { return aktywna; }
    public void setAktywna(boolean aktywna) { this.aktywna = aktywna; }

    /** Etap GUI-strefa: podmiana obu narożników (nowe x1≤x2, y1≤y2, z1≤z2 wymuszane przez konstruktor). */
    public void setNarożniki(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = Math.min(x1, x2);
        this.x2 = Math.max(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.y2 = Math.max(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.z2 = Math.max(z1, z2);
    }

    /**
     * Kopia niezależna (ten sam id/centralaId/nazwa/świat, te same granice) — do edycji
     * "na kopii" w GUI (Etap 1 planu ROZSZERZENIE GUI), żeby zmiany w ekranie NIE mutowały
     * obiektu z CentralaRejestr na żywo, dopóki gracz nie kliknie "Zapisz".
     */
    public Strefa kopia() {
        Strefa k = new Strefa(id, centralaId, nazwa, swiat, x1, y1, z1, x2, y2, z2);
        k.setAktywna(aktywna);
        return k;
    }
}
