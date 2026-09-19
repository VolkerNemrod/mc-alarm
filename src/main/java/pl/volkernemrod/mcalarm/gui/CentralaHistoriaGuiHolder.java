package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import pl.volkernemrod.mcalarm.model.TypZdarzenia;

import java.util.List;
import java.util.UUID;

/**
 * Znacznik GUI historii centrali — analogicznie do CentralaGuiHolder, żeby
 * CentralaGuiListener rozróżnił kliknięcia w panelu głównym od kliknięć
 * w liście historii.
 *
 * Etap 5 planu ROZSZERZENIE GUI — trzyma też stan stronicowania i filtrów:
 * {@code strona} (od 0), opcjonalny {@code filtrTyp}, opcjonalny {@code filtrGracz}
 * (D4: przewijany po liście ostatnich graczy, nie wpisywany na czacie — lista
 * {@code ostatniGracze} jest pobierana raz przy otwarciu ekranu i przechowywana
 * tutaj, żeby każdy klik w filtr GRACZ nie odpytywał bazy o nową listę).
 */
public class CentralaHistoriaGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private Inventory inventory;

    private int strona = 0;
    private boolean maNastepnaStrona = false;
    private TypZdarzenia filtrTyp = null;
    private UUID filtrGracz = null;
    private List<UUID> ostatniGracze = List.of();

    public CentralaHistoriaGuiHolder(UUID centralaId) {
        this.centralaId = centralaId;
    }

    public UUID getCentralaId() {
        return centralaId;
    }

    public int getStrona() { return strona; }
    public void setStrona(int strona) { this.strona = strona; }

    public boolean isMaNastepnaStrona() { return maNastepnaStrona; }
    public void setMaNastepnaStrona(boolean maNastepnaStrona) { this.maNastepnaStrona = maNastepnaStrona; }

    public TypZdarzenia getFiltrTyp() { return filtrTyp; }
    public void setFiltrTyp(TypZdarzenia filtrTyp) { this.filtrTyp = filtrTyp; }

    public UUID getFiltrGracz() { return filtrGracz; }
    public void setFiltrGracz(UUID filtrGracz) { this.filtrGracz = filtrGracz; }

    public List<UUID> getOstatniGracze() { return ostatniGracze; }
    public void setOstatniGracze(List<UUID> ostatniGracze) { this.ostatniGracze = ostatniGracze; }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
