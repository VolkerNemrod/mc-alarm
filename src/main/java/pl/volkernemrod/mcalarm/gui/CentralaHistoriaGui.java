package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.TypZdarzenia;
import pl.volkernemrod.mcalarm.model.Zdarzenie;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Buduje ekran historii centrali — lista zdarzeń jako itemy z opisem w lore,
 * filtr typu, filtr gracza (D4), stronicowanie i przycisk powrotu.
 * Etap 5 (ostatni) planu ROZSZERZENIE GUI.
 *
 * Rozmiar 54 (6 rzędów): zdarzenia w slotach 0-44 (max 45), dolny rząd
 * (45-53) nawigacja: 45=◀ poprzednia, 46=filtr TYP, 47=filtr GRACZ,
 * 48=wyczyść filtry, 49=info (strona/aktywne filtry), 52=▶ następna, 53=Wróć.
 */
public final class CentralaHistoriaGui {

    public static final int SLOT_POPRZEDNIA = 45;
    public static final int SLOT_FILTR_TYP = 46;
    public static final int SLOT_FILTR_GRACZ = 47;
    public static final int SLOT_WYCZYSC = 48;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NASTEPNA = 52;
    public static final int SLOT_WROC = 53;

    public static final int ROZMIAR_STRONY = 45;

    /** D4 (2026-09-18, potwierdzone) — kolejność przewijania filtra typu; null = wszystkie. */
    public static final TypZdarzenia[] FILTROWALNE_TYPY = {
            TypZdarzenia.PLAYER_ENTER_ZONE, TypZdarzenia.PLAYER_EXIT_ZONE,
            TypZdarzenia.BLOCK_BREAK, TypZdarzenia.BLOCK_PLACE,
            TypZdarzenia.CONTAINER_OPEN, TypZdarzenia.CONTAINER_ITEM_ADD, TypZdarzenia.CONTAINER_ITEM_REMOVE
    };

    private CentralaHistoriaGui() {
    }

    public static Inventory zbuduj(Centrala centrala, CentralaHistoriaGuiHolder holder, List<Zdarzenie> zdarzenia) {
        Inventory inv = Bukkit.createInventory(holder, 54, "§6Historia: " + centrala.getNazwa());
        holder.setInventory(inv);
        odswiez(inv, holder, zdarzenia);
        return inv;
    }

    public static void odswiez(Inventory inv, CentralaHistoriaGuiHolder holder, List<Zdarzenie> zdarzenia) {
        for (int i = 0; i < ROZMIAR_STRONY; i++) {
            inv.setItem(i, null);
        }
        if (zdarzenia.isEmpty()) {
            ItemStack brak = new ItemStack(Material.BARRIER);
            ustawNazwe(brak, "§7Brak zdarzeń spełniających filtry.");
            inv.setItem(22, brak);
        } else {
            int slot = 0;
            for (Zdarzenie z : zdarzenia) {
                if (slot >= ROZMIAR_STRONY) break;
                inv.setItem(slot++, itemZdarzenia(z));
            }
        }

        inv.setItem(SLOT_POPRZEDNIA, itemNawigacji(Material.ARROW,
                "§e◀ Poprzednia strona", holder.getStrona() > 0));
        inv.setItem(SLOT_NASTEPNA, itemNawigacji(Material.ARROW,
                "§e▶ Następna strona", holder.isMaNastepnaStrona()));

        ItemStack filtrTyp = new ItemStack(Material.HOPPER);
        ItemMeta filtrTypMeta = filtrTyp.getItemMeta();
        filtrTypMeta.setDisplayName("§bFiltr typu: §f"
                + (holder.getFiltrTyp() == null ? "wszystkie" : holder.getFiltrTyp().toString()));
        filtrTypMeta.setLore(List.of("§7Klik: następny typ.", "§7Shift+klik: poprzedni typ."));
        filtrTyp.setItemMeta(filtrTypMeta);
        inv.setItem(SLOT_FILTR_TYP, filtrTyp);

        ItemStack filtrGracz = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta filtrGraczMeta = filtrGracz.getItemMeta();
        filtrGraczMeta.setDisplayName("§bFiltr gracza: §f"
                + (holder.getFiltrGracz() == null ? "wszyscy" : nazwaGracza(holder.getFiltrGracz())));
        filtrGraczMeta.setLore(holder.getOstatniGracze().isEmpty()
                ? List.of("§7Brak zarejestrowanych graczy do wyboru.")
                : List.of("§7Klik: następny gracz (" + holder.getOstatniGracze().size() + " ostatnich).",
                          "§7Shift+klik: poprzedni gracz."));
        filtrGracz.setItemMeta(filtrGraczMeta);
        inv.setItem(SLOT_FILTR_GRACZ, filtrGracz);

        ItemStack wyczysc = new ItemStack(Material.WATER_BUCKET);
        ustawNazwe(wyczysc, "§cWyczyść filtry");
        inv.setItem(SLOT_WYCZYSC, wyczysc);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6Strona §f" + (holder.getStrona() + 1));
        infoMeta.setLore(List.of(
                "§7Typ: §f" + (holder.getFiltrTyp() == null ? "wszystkie" : holder.getFiltrTyp().toString()),
                "§7Gracz: §f" + (holder.getFiltrGracz() == null ? "wszyscy" : nazwaGracza(holder.getFiltrGracz()))
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(SLOT_INFO, info);

        ItemStack wroc = new ItemStack(Material.ARROW);
        ustawNazwe(wroc, "§eWróć do panelu centrali");
        inv.setItem(SLOT_WROC, wroc);
    }

    private static ItemStack itemNawigacji(Material mat, String nazwa, boolean dostepna) {
        ItemStack item = new ItemStack(dostepna ? mat : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(dostepna ? nazwa : "§8" + nazwa.replaceAll("§.", ""));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemZdarzenia(Zdarzenie z) {
        Material material = switch (z.getTyp()) {
            case PLAYER_ENTER_ZONE, PLAYER_EXIT_ZONE -> Material.COMPASS;
            case BLOCK_BREAK -> Material.IRON_PICKAXE;
            case BLOCK_PLACE -> Material.GRASS_BLOCK;
            case CONTAINER_OPEN -> Material.CHEST;
            case CONTAINER_ITEM_REMOVE -> Material.RED_DYE;
            case CONTAINER_ITEM_ADD -> Material.GREEN_DYE;
            case CENTRAL_CREATED, CENTRAL_REMOVED_LEGALNIE, CENTRAL_DESTROYED_FIZYCZNIE -> Material.LECTERN;
            default -> Material.PAPER;
        };

        String kolor = switch (z.getWaznosc()) {
            case INFO -> "§7";
            case WARNING -> "§e";
            case SUSPICIOUS -> "§6";
            case CRITICAL -> "§c";
        };

        String gracz = z.getGracz() == null ? "NIEZNANY" : nazwaGracza(z.getGracz());

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kolor + z.getTyp() + " §7— " + gracz);
        meta.setLore(List.of(
                "§7Czas: §f" + FORMAT_CZASU.format(z.getCzas()),
                "§7Ważność: " + kolor + z.getWaznosc(),
                "§7" + z.getSzczegoly()
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static String nazwaGracza(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String nazwa = op.getName();
        return nazwa != null ? nazwa : uuid.toString().substring(0, 8);
    }

    private static void ustawNazwe(ItemStack item, String nazwa) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nazwa);
        item.setItemMeta(meta);
    }
}
