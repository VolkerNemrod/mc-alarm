package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Incydent;
import pl.volkernemrod.mcalarm.model.TypZdarzenia;
import pl.volkernemrod.mcalarm.model.Waznosc;
import pl.volkernemrod.mcalarm.model.Zdarzenie;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ekran szczegółów incydentu — lista zdarzeń tej konkretnej "wizyty" obcego.
 * Otwierany po kliknięciu incydentu w CentralaIncydentyGui.
 *
 * Rozmiar: 36 slotów. Zdarzenia w slotach 0–26 (max 27).
 * Slot 31 = wróć do listy incydentów, slot 29 = nagłówek incydentu.
 *
 * Plan działania — punkt E.
 */
public final class CentralaIncydentSzczegolyGui {

    public static final int SLOT_WROC = 31;

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private CentralaIncydentSzczegolyGui() {}

    public static Inventory zbuduj(Centrala centrala, Incydent incydent, List<Zdarzenie> zdarzenia) {
        CentralaIncydentSzczegolyGuiHolder holder =
                new CentralaIncydentSzczegolyGuiHolder(centrala.getId(), incydent.getId());

        String graczNazwa = incydent.getGracz() == null ? "NIEZNANY" : nazwaGracza(incydent.getGracz());
        Inventory inv = Bukkit.createInventory(holder, 36,
                "§6Incydent: §f" + graczNazwa);
        holder.setInventory(inv);

        // Slot 29 — nagłówek z info o incydencie
        inv.setItem(29, itemNaglowek(incydent, graczNazwa));

        if (zdarzenia.isEmpty()) {
            ItemStack brak = new ItemStack(Material.BARRIER);
            ItemMeta m = brak.getItemMeta();
            m.setDisplayName("§7Brak zdarzeń przypisanych do tego incydentu.");
            brak.setItemMeta(m);
            inv.setItem(13, brak);
        } else {
            int slot = 0;
            for (Zdarzenie z : zdarzenia) {
                if (slot >= 27) break;
                if (slot == 29) slot++; // pomiń slot nagłówka
                inv.setItem(slot++, itemZdarzenia(z));
            }
        }

        ItemStack wroc = new ItemStack(Material.ARROW);
        ItemMeta wm = wroc.getItemMeta();
        wm.setDisplayName("§eWróć do listy incydentów");
        wroc.setItemMeta(wm);
        inv.setItem(SLOT_WROC, wroc);

        return inv;
    }

    private static ItemStack itemNaglowek(Incydent inc, String graczNazwa) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        String koniec = inc.getKoniec() == null ? "trwa..." : FORMAT.format(inc.getKoniec());
        meta.setDisplayName("§6Incydent — §f" + graczNazwa);
        meta.setLore(List.of(
                "§7Start:  §f" + FORMAT.format(inc.getStart()),
                "§7Koniec: §f" + koniec,
                "§7Status: §f" + inc.getStatus()
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemZdarzenia(Zdarzenie z) {
        Material mat = materialDlaTypu(z.getTyp());
        String kolor = switch (z.getWaznosc()) {
            case CRITICAL -> "§c";
            case SUSPICIOUS -> "§6";
            case WARNING -> "§e";
            case INFO -> "§7";
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kolor + z.getTyp().name().replace('_', ' '));

        List<String> lore = new ArrayList<>();
        lore.add("§7Czas: §f" + FORMAT.format(z.getCzas()));
        lore.add("§7Ważność: " + kolor + z.getWaznosc());
        if (z.getSzczegoly() != null && !z.getSzczegoly().isBlank()) {
            lore.add("§7Info: §f" + z.getSzczegoly());
        }
        lore.add("§7Lokalizacja: §f" + z.getSwiat()
                + " " + z.getX() + "/" + z.getY() + "/" + z.getZ());
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Material materialDlaTypu(TypZdarzenia typ) {
        return switch (typ) {
            case PLAYER_ENTER_ZONE -> Material.LIME_DYE;
            case PLAYER_EXIT_ZONE  -> Material.RED_DYE;
            case BLOCK_BREAK       -> Material.IRON_PICKAXE;
            case BLOCK_PLACE       -> Material.BRICKS;
            case CONTAINER_OPEN    -> Material.CHEST;
            case CONTAINER_ITEM_REMOVE -> Material.RED_DYE;
            case CONTAINER_ITEM_ADD    -> Material.GREEN_DYE;
            default -> Material.PAPER; // pozostałe typy (centrala, interakcje, zarezerwowane) — na razie nierejestrowane w strefie
        };
    }

    private static String nazwaGracza(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String nazwa = op.getName();
        return nazwa != null ? nazwa : uuid.toString().substring(0, 8);
    }
}
