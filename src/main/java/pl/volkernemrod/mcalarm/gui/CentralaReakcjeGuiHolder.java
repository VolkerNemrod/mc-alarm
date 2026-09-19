package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Znacznik GUI ekranu ustawień reakcji centrali — analogicznie do
 * CentralaHistoriaGuiHolder. Mapowanie slot→TypZdarzenia jest stałe
 * (patrz CentralaReakcjeGui.TYPY / SLOTY), więc holder trzyma tylko id
 * centrali. Plan działania — Etap 7 c.d.
 */
public class CentralaReakcjeGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private Inventory inventory;

    public CentralaReakcjeGuiHolder(UUID centralaId) {
        this.centralaId = centralaId;
    }

    public UUID getCentralaId() {
        return centralaId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
