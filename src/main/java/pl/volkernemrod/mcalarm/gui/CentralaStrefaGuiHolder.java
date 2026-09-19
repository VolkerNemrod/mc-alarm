package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import pl.volkernemrod.mcalarm.model.Strefa;

import java.util.UUID;

/**
 * Znacznik GUI ekranu regulacji strefy (Etap 1 planu ROZSZERZENIE GUI, przebudowa).
 *
 * EDYCJA NA KOPII (naprawa bugu 1.0.b z planu): holder trzyma DWA obiekty Strefa —
 * {@code roboczaStrefa} to niezależna kopia edytowana kliknięciami w GUI (NIE jest
 * tym samym obiektem co w CentralaRejestr, więc nie wpływa na wykrywanie wejścia/wyjścia
 * dopóki gracz nie kliknął "Zapisz"), a {@code zapisanaStrefa} to niezmienny snapshot
 * z momentu otwarcia ekranu, używany do wykrycia niezapisanych zmian i do przycisku Reset.
 */
public class CentralaStrefaGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private final Strefa roboczaStrefa;
    private final Strefa zapisanaStrefa;
    private Inventory inventory;

    public CentralaStrefaGuiHolder(UUID centralaId, Strefa roboczaStrefa, Strefa zapisanaStrefa) {
        this.centralaId = centralaId;
        this.roboczaStrefa = roboczaStrefa;
        this.zapisanaStrefa = zapisanaStrefa;
    }

    public UUID getCentralaId() { return centralaId; }
    public UUID getStrefaId() { return roboczaStrefa.getId(); }
    public Strefa getRoboczaStrefa() { return roboczaStrefa; }
    public Strefa getZapisanaStrefa() { return zapisanaStrefa; }

    /** Czy robocza kopia różni się granicami od ostatnio zapisanej w bazie. */
    public boolean maNiezapisaneZmiany() {
        return roboczaStrefa.getX1() != zapisanaStrefa.getX1() || roboczaStrefa.getX2() != zapisanaStrefa.getX2()
                || roboczaStrefa.getY1() != zapisanaStrefa.getY1() || roboczaStrefa.getY2() != zapisanaStrefa.getY2()
                || roboczaStrefa.getZ1() != zapisanaStrefa.getZ1() || roboczaStrefa.getZ2() != zapisanaStrefa.getZ2();
    }

    /** Przywraca roboczą kopię do granic ostatnio zapisanych w bazie (przycisk Reset). */
    public void resetujDoZapisanej() {
        roboczaStrefa.setNarożniki(zapisanaStrefa.getX1(), zapisanaStrefa.getY1(), zapisanaStrefa.getZ1(),
                zapisanaStrefa.getX2(), zapisanaStrefa.getY2(), zapisanaStrefa.getZ2());
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
