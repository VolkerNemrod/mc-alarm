package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Znacznik GUI historii centrali — analogicznie do CentralaGuiHolder, żeby
 * CentralaGuiListener rozróżnił kliknięcia w panelu głównym od kliknięć
 * w liście historii. Plan działania — Etap 6/7 (start).
 */
public class CentralaHistoriaGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private Inventory inventory;

    public CentralaHistoriaGuiHolder(UUID centralaId) {
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
