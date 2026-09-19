package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.TypZdarzenia;

import java.util.List;
import java.util.Map;

/**
 * Buduje ekran ustawień reakcji centrali — czy dany typ zdarzenia wywołuje
 * alarm (patrz KonfiguracjaReakcjiRepository). Jeden item na typ, kolor
 * (LIME_DYE = włączone / GRAY_DYE = wyłączone), kliknięcie togluje stan
 * (patrz CentralaGuiListener) + przycisk powrotu do panelu głównego.
 * Plan działania — Etap 7 c.d.
 *
 * TYPY = te same 7 typów co KonfiguracjaReakcjiRepository.DOMYSLNE (jedyne
 * typy z sensowną domyślną wartością / obsługiwane przez /alarm reaction set
 * w V1). SLOTY — stałe mapowanie 1:1 z TYPY, używane też przez
 * CentralaGuiListener do odczytania, który typ odpowiada klikniętemu slotowi.
 *
 * Rozmiar 27: 7 przełączników w środkowym rzędzie (sloty 10-16), SLOT_WROC = 22.
 */
public final class CentralaReakcjeGui {

    public static final List<TypZdarzenia> TYPY = List.of(
            TypZdarzenia.PLAYER_ENTER_ZONE,
            TypZdarzenia.PLAYER_EXIT_ZONE,
            TypZdarzenia.BLOCK_BREAK,
            TypZdarzenia.BLOCK_PLACE,
            TypZdarzenia.CONTAINER_OPEN,
            TypZdarzenia.CONTAINER_ITEM_ADD,
            TypZdarzenia.CONTAINER_ITEM_REMOVE
    );

    public static final int[] SLOTY = {10, 11, 12, 13, 14, 15, 16};
    public static final int SLOT_WROC = 22;

    private CentralaReakcjeGui() {
    }

    /** @param stan mapa typ→czy wywołuje alarm, dla wszystkich pozycji z TYPY (odczytana wcześniej z repozytorium). */
    public static Inventory zbuduj(Centrala centrala, Map<TypZdarzenia, Boolean> stan) {
        CentralaReakcjeGuiHolder holder = new CentralaReakcjeGuiHolder(centrala.getId());
        Inventory inv = Bukkit.createInventory(holder, 27, "§6Reakcje: " + centrala.getNazwa());
        holder.setInventory(inv);

        for (int i = 0; i < TYPY.size(); i++) {
            TypZdarzenia typ = TYPY.get(i);
            boolean wlaczone = stan.getOrDefault(typ, false);
            inv.setItem(SLOTY[i], itemTypu(typ, wlaczone));
        }

        ItemStack wroc = new ItemStack(Material.ARROW);
        ustawNazwe(wroc, "§eWróć do panelu centrali");
        inv.setItem(SLOT_WROC, wroc);

        return inv;
    }

    private static ItemStack itemTypu(TypZdarzenia typ, boolean wlaczone) {
        ItemStack item = new ItemStack(wlaczone ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((wlaczone ? "§a" : "§7") + typ);
        meta.setLore(List.of(
                "§7Wywołuje alarm: " + (wlaczone ? "§aTAK" : "§cNIE"),
                "§7Kliknij, aby przełączyć."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static void ustawNazwe(ItemStack item, String nazwa) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nazwa);
        item.setItemMeta(meta);
    }
}
