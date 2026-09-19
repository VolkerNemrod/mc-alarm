package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Znacznik GUI głośnika (punkt B — Etap GUI rozszerzeń).
 */
public class GlosnikGuiHolder implements InventoryHolder {

    private final UUID glosnikId;
    private final UUID centralaId;
    private Inventory inventory;

    public GlosnikGuiHolder(UUID glosnikId, UUID centralaId) {
        this.glosnikId = glosnikId;
        this.centralaId = centralaId;
    }

    public UUID getGlosnikId() { return glosnikId; }
    public UUID getCentralaId() { return centralaId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
