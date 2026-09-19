package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Znacznik GUI szczegółów incydentu (punkt E).
 * Trzyma centralaId (do powrotu) oraz incydentId (do odczytu zdarzeń).
 */
public class CentralaIncydentSzczegolyGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private final UUID incydentId;
    private Inventory inventory;

    public CentralaIncydentSzczegolyGuiHolder(UUID centralaId, UUID incydentId) {
        this.centralaId = centralaId;
        this.incydentId = incydentId;
    }

    public UUID getCentralaId() { return centralaId; }
    public UUID getIncydentId() { return incydentId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
