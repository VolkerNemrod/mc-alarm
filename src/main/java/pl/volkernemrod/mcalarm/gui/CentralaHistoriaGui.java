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
 * Buduje ekran historii centrali — lista ostatnich zdarzeń jako itemy
 * z opisem w lore + przycisk powrotu do panelu głównego (CentralaGui).
 * Plan działania — Etap 6/7 (start, bez filtrów/stronicowania — to później).
 *
 * Rozmiar 36 (4 rzędy): zdarzenia w slotach 0-26 (max 27, resztę ucinamy),
 * dolny rząd zarezerwowany na nawigację — SLOT_WROC = 31.
 */
public final class CentralaHistoriaGui {

    public static final int SLOT_WROC = 31;
    private static final int MAX_ZDARZEN_NA_EKRANIE = 27;

    private static final DateTimeFormatter FORMAT_CZASU =
            DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private CentralaHistoriaGui() {
    }

    public static Inventory zbuduj(Centrala centrala, List<Zdarzenie> zdarzenia) {
        CentralaHistoriaGuiHolder holder = new CentralaHistoriaGuiHolder(centrala.getId());
        Inventory inv = Bukkit.createInventory(holder, 36, "§6Historia: " + centrala.getNazwa());
        holder.setInventory(inv);

        if (zdarzenia.isEmpty()) {
            ItemStack brak = new ItemStack(Material.BARRIER);
            ustawNazwe(brak, "§7Brak zarejestrowanych zdarzeń.");
            inv.setItem(13, brak);
        } else {
            int slot = 0;
            for (Zdarzenie z : zdarzenia) {
                if (slot >= MAX_ZDARZEN_NA_EKRANIE) {
                    break; // V1: bez stronicowania — tylko pierwsze N, reszta przez /alarm history <liczba>
                }
                inv.setItem(slot++, itemZdarzenia(z));
            }
        }

        ItemStack wroc = new ItemStack(Material.ARROW);
        ustawNazwe(wroc, "§eWróć do panelu centrali");
        inv.setItem(SLOT_WROC, wroc);

        return inv;
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
