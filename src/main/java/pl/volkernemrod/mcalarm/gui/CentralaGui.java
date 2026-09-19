package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.TrybPracy;

import java.util.List;

/**
 * Buduje panel główny GUI centrali. Plan działania — Etap 7 (V1: status,
 * przełącznik uzbrój/rozbrój, historia, zaufani, ustawienia reakcji).
 *
 * Sloty (rozmiar 27): 4 = status, 2 = strefa, 9 = zaufani, 11 = informacje,
 * 13 = przełącznik uzbrój/rozbrój, 15 = historia, 17 = ustawienia reakcji,
 * 20 = incydenty, 22 = zamknij.
 */
public final class CentralaGui {

    public static final int SLOT_STATUS = 4;
    public static final int SLOT_STREFA = 2;
    public static final int SLOT_ZAUFANI = 9;
    public static final int SLOT_INFO = 11;
    public static final int SLOT_PRZELACZNIK = 13;
    public static final int SLOT_HISTORIA = 15;
    public static final int SLOT_REAKCJE = 17;
    public static final int SLOT_INCYDENTY = 20;
    public static final int SLOT_ZAMKNIJ = 22;

    private CentralaGui() {
    }

    public static Inventory zbuduj(Centrala centrala) {
        CentralaGuiHolder holder = new CentralaGuiHolder(centrala.getId());
        Inventory inv = Bukkit.createInventory(holder, 27, "§6" + centrala.getNazwa());
        holder.setInventory(inv);

        inv.setItem(SLOT_STATUS, itemStatusu(centrala));
        inv.setItem(SLOT_STREFA, itemStrefy(centrala));
        inv.setItem(SLOT_ZAUFANI, itemZaufanych(centrala));
        inv.setItem(SLOT_INFO, itemInfo(centrala));
        inv.setItem(SLOT_PRZELACZNIK, itemPrzelacznika(centrala));
        inv.setItem(SLOT_HISTORIA, itemHistorii());
        inv.setItem(SLOT_REAKCJE, itemReakcji());
        inv.setItem(SLOT_INCYDENTY, itemIncydentow());
        inv.setItem(SLOT_ZAMKNIJ, itemZamknij());

        return inv;
    }

    private static ItemStack itemStrefy(Centrala centrala) {
        boolean maStrefe = centrala.getStrefaId() != null;
        ItemStack item = new ItemStack(maStrefe ? Material.GRASS_BLOCK : Material.STRUCTURE_VOID);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(maStrefe
                ? "§bStrefa §7(kliknij, by zmienić rozmiar)"
                : "§eUtwórz strefę §7(kliknij)");
        meta.setLore(maStrefe
                ? List.of("§7Strefa jest ustawiona.", "§7Kliknij, by regulować rozmiar.")
                : List.of("§cBrak strefy.", "§7Kliknij, by utworzyć domyślną strefę 7×7×7", "§7wokół centrali (możesz ją potem powiększyć)."));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemStatusu(Centrala centrala) {
        Material material = switch (centrala.getTryb()) {
            case ROZBROJONY -> Material.GRAY_WOOL;
            case MONITORING -> Material.YELLOW_WOOL;
            case CZUWANIE -> Material.ORANGE_WOOL;
            case ALARM -> Material.RED_WOOL;
        };
        ItemStack item = new ItemStack(material);
        ustawNazwe(item, "§eStatus: §f" + centrala.getTryb());
        return item;
    }

    private static ItemStack itemInfo(Centrala centrala) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bInformacje o centrali");
        meta.setLore(List.of(
                "§7Nazwa: §f" + centrala.getNazwa(),
                "§7Tryb: §f" + centrala.getTryb(),
                "§7Strefa: §f" + (centrala.getStrefaId() != null ? "ustawiona" : "brak — kliknij „Strefa” w panelu, by utworzyć"),
                "§7Zaufani: §f" + centrala.getZaufani().size(),
                "§e» Kliknij, by zmienić nazwę centrali"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemPrzelacznika(Centrala centrala) {
        boolean uzbrojona = centrala.getTryb().isUzbrojona();
        ItemStack item = new ItemStack(uzbrojona ? Material.RED_DYE : Material.LIME_DYE);
        ustawNazwe(item, uzbrojona ? "§c§lKliknij, by ROZBROIĆ" : "§a§lKliknij, by UZBROIĆ");
        return item;
    }

    private static ItemStack itemHistorii() {
        ItemStack item = new ItemStack(Material.BOOK);
        ustawNazwe(item, "§bHistoria §7(kliknij, by zobaczyć ostatnie zdarzenia)");
        return item;
    }

    private static ItemStack itemZaufanych(Centrala centrala) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bZaufani gracze §7(kliknij)");
        meta.setLore(List.of("§7Liczba: §f" + centrala.getZaufani().size()));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemReakcji() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ustawNazwe(item, "§bUstawienia reakcji §7(kliknij)");
        return item;
    }

    private static ItemStack itemIncydentow() {
        ItemStack item = new ItemStack(Material.REDSTONE_TORCH);
        ustawNazwe(item, "§bIncydenty §7(kliknij, by zobaczyć ostatnie wizyty obcych)");
        return item;
    }

    private static ItemStack itemZamknij() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ustawNazwe(item, "§cZamknij");
        return item;
    }

    private static void ustawNazwe(ItemStack item, String nazwa) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nazwa);
        item.setItemMeta(meta);
    }
}
