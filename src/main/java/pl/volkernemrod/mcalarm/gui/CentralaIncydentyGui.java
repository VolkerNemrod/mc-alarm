package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Incydent;
import pl.volkernemrod.mcalarm.model.StatusIncydentu;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Buduje ekran incydentów centrali — lista ostatnich incydentów ("wizyt"
 * obcych w strefie) jako itemy z opisem w lore + przycisk powrotu do panelu
 * głównego (CentralaGui). Wzorowane 1:1 na CentralaHistoriaGui.
 * Plan działania — Etap 6 c.d.
 *
 * Rozmiar 36 (4 rzędy): incydenty w slotach 0-26 (max 27, resztę ucinamy),
 * dolny rząd zarezerwowany na nawigację — SLOT_WROC = 31.
 */
public final class CentralaIncydentyGui {

    public static final int SLOT_WROC = 31;
    private static final int MAX_INCYDENTOW_NA_EKRANIE = 27;

    private static final DateTimeFormatter FORMAT_CZASU =
            DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private CentralaIncydentyGui() {
    }

    public static Inventory zbuduj(Centrala centrala, List<Incydent> incydenty) {
        CentralaIncydentyGuiHolder holder = new CentralaIncydentyGuiHolder(centrala.getId());
        Inventory inv = Bukkit.createInventory(holder, 36, "§6Incydenty: " + centrala.getNazwa());
        holder.setInventory(inv);

        if (incydenty.isEmpty()) {
            ItemStack brak = new ItemStack(Material.BARRIER);
            ustawNazwe(brak, "§7Brak zarejestrowanych incydentów.");
            inv.setItem(13, brak);
        } else {
            int slot = 0;
            for (Incydent inc : incydenty) {
                if (slot >= MAX_INCYDENTOW_NA_EKRANIE) {
                    break;
                }
                inv.setItem(slot, itemIncydentu(inc));
                holder.rejestrujIncydent(slot, inc.getId());
                slot++;
            }
        }

        ItemStack wroc = new ItemStack(Material.ARROW);
        ustawNazwe(wroc, "§eWróć do panelu centrali");
        inv.setItem(SLOT_WROC, wroc);

        return inv;
    }

    private static ItemStack itemIncydentu(Incydent inc) {
        boolean otwarty = inc.getStatus() == StatusIncydentu.OTWARTY;
        Material material = otwarty ? Material.REDSTONE_TORCH : Material.PAPER;
        String kolor = otwarty ? "§c" : "§7";

        String gracz = inc.getGracz() == null ? "NIEZNANY" : nazwaGracza(inc.getGracz());
        String koniec = inc.getKoniec() == null ? "trwa..." : FORMAT_CZASU.format(inc.getKoniec());

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(kolor + "Incydent — " + gracz);
        meta.setLore(List.of(
                "§7Start: §f" + FORMAT_CZASU.format(inc.getStart()),
                "§7Koniec: §f" + koniec,
                "§7Status: " + kolor + inc.getStatus(),
                "§e» Kliknij, by zobaczyć szczegóły"
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
