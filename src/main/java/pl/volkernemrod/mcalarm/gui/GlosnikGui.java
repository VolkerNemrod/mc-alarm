package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Glosnik;

import java.util.List;

/**
 * Ekran GUI głośnika — 27 slotów.
 *
 * Układ:
 *   Slot 11 — toggle dźwięku (JUKEBOX / BARRIER)
 *   Slot 13 — info (NOTE_BLOCK)
 *   Slot 15 — toggle efektów wizualnych (BLAZE_POWDER / GRAY_DYE)
 *   Slot 22 — zamknij (ARROW)
 *
 * Plan działania — punkt B (głośnik).
 */
public final class GlosnikGui {

    public static final int SLOT_DZWIEK  = 11;
    public static final int SLOT_INFO    = 13;
    public static final int SLOT_EFEKTY  = 15;
    public static final int SLOT_ZAMKNIJ = 22;

    private GlosnikGui() {}

    public static Inventory zbuduj(Glosnik glosnik) {
        GlosnikGuiHolder holder = new GlosnikGuiHolder(glosnik.getId(), glosnik.getCentralaId());
        Inventory inv = Bukkit.createInventory(holder, 27, "§6Głośnik VolkerNemrodAlarm");
        holder.setInventory(inv);

        inv.setItem(SLOT_DZWIEK, itemDzwiek(glosnik.isDzwiekWlaczony()));
        inv.setItem(SLOT_INFO, itemInfo(glosnik));
        inv.setItem(SLOT_EFEKTY, itemEfekty(glosnik.isEfektyWlaczone()));
        inv.setItem(SLOT_ZAMKNIJ, itemZamknij());

        return inv;
    }

    public static ItemStack itemDzwiek(boolean wlaczony) {
        ItemStack item = new ItemStack(wlaczony ? Material.JUKEBOX : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(wlaczony ? "§aDźwięk: §lWŁĄCZONY" : "§cDźwięk: §lWYŁĄCZONY");
        meta.setLore(List.of("§7Kliknij, by przełączyć.",
                "§7Głośnik emituje dźwięk podczas alarmu."));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack itemEfekty(boolean wlaczone) {
        ItemStack item = new ItemStack(wlaczone ? Material.BLAZE_POWDER : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(wlaczone ? "§aEfekty wizualne: §lWŁĄCZONE" : "§cEfekty wizualne: §lWYŁĄCZONE");
        meta.setLore(List.of("§7Kliknij, by przełączyć.",
                "§7Cząsteczki i błyski podczas alarmu."));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemInfo(Glosnik glosnik) {
        ItemStack item = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bGłośnik");
        meta.setLore(List.of(
                "§7Lokalizacja: §f" + glosnik.getSwiat()
                        + " " + glosnik.getX() + "/" + glosnik.getY() + "/" + glosnik.getZ(),
                "§7Działa gdy jest w aktywnej strefie alarmowej."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemZamknij() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7Zamknij");
        item.setItemMeta(meta);
        return item;
    }
}
