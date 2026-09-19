package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Znacznik GUI centrali — pozwala CentralaGuiListener rozpoznać, że kliknięty
 * ekwipunek to nasze GUI (a nie skrzynia gracza), i do której centrali należy.
 * Plan działania — Etap 7 (na razie tylko panel główny, uzbrój/rozbrój).
 */
public class CentralaGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private Inventory inventory;

    public CentralaGuiHolder(UUID centralaId) {
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
