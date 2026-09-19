package pl.volkernemrod.mcalarm.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/**
 * Znacznik GUI ekranu zaufanych centrali — analogicznie do
 * CentralaHistoriaGuiHolder. Dodatkowo trzyma kolejność UUID-ów zaufanych
 * graczy w takiej samej kolejności, w jakiej zostały wstawione jako itemy
 * (slot 0 = zaufaniSlotOrder.get(0) itd.) — CentralaGuiListener potrzebuje
 * tego, żeby po numerze klikniętego slotu wiedzieć, którego gracza usunąć.
 * Plan działania — Etap 7 c.d.
 */
public class CentralaZaufaniGuiHolder implements InventoryHolder {

    private final UUID centralaId;
    private final List<UUID> zaufaniSlotOrder;
    private Inventory inventory;

    public CentralaZaufaniGuiHolder(UUID centralaId, List<UUID> zaufaniSlotOrder) {
        this.centralaId = centralaId;
        this.zaufaniSlotOrder = zaufaniSlotOrder;
    }

    public UUID getCentralaId() {
        return centralaId;
    }

    /** Gracz przypisany do danego slotu, albo null jeśli slot poza zakresem listy. */
    public UUID getGraczWSlocie(int slot) {
        if (slot < 0 || slot >= zaufaniSlotOrder.size()) {
            return null;
        }
        return zaufaniSlotOrder.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
