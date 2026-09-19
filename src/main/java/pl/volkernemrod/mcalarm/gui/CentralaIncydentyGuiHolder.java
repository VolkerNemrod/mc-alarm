package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Znacznik GUI incydentów centrali. Trzyma także mapę slot→incydentId
 * żeby CentralaGuiListener wiedział który incydent kliknięto (do szczegółów).
 * Plan działania — Etap 6 c.d., punkt E.
 */
public class CentralaIncydentyGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private final Map<Integer, UUID> slotDoIncydentu = new HashMap<>();
    private Inventory inventory;

    public CentralaIncydentyGuiHolder(UUID centralaId) {
        this.centralaId = centralaId;
    }

    public UUID getCentralaId() { return centralaId; }

    public void rejestrujIncydent(int slot, UUID incydentId) {
        slotDoIncydentu.put(slot, incydentId);
    }

    public UUID getIncydentWSlocie(int slot) {
        return slotDoIncydentu.get(slot);
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
