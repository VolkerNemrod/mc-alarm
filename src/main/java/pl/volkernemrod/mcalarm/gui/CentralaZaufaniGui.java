package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Buduje ekran zaufanych graczy centrali — lista jako itemy PLAYER_HEAD,
 * kliknięcie usuwa gracza z zaufanych (patrz CentralaGuiListener) + przycisk
 * dodania nowego gracza (D1: tylko dla właściciela) + przycisk powrotu do
 * panelu głównego. Wzorowane na CentralaHistoriaGui/CentralaIncydentyGui.
 *
 * Etap 4 (dawny) planu ROZSZERZENIE GUI: dodawanie NOWYCH zaufanych działa
 * teraz też z GUI (prompt na czacie, wzorzec jak zmiana nazwy centrali) —
 * obok komendy /alarm trusted add <nick>, która zostaje jako alternatywa.
 *
 * Rozmiar 36 (4 rzędy): zaufani w slotach 0-26 (max 27, resztę ucinamy),
 * dolny rząd zarezerwowany na nawigację — SLOT_DODAJ = 27, SLOT_WROC = 31.
 */
public final class CentralaZaufaniGui {

    public static final int SLOT_DODAJ = 27;
    public static final int SLOT_WROC = 31;
    private static final int MAX_ZAUFANYCH_NA_EKRANIE = 27;

    private CentralaZaufaniGui() {
    }

    public static Inventory zbuduj(Centrala centrala) {
        Set<UUID> zaufaniSet = centrala.getZaufani();
        List<UUID> kolejnosc = new ArrayList<>(zaufaniSet);
        if (kolejnosc.size() > MAX_ZAUFANYCH_NA_EKRANIE) {
            kolejnosc = kolejnosc.subList(0, MAX_ZAUFANYCH_NA_EKRANIE);
        }

        CentralaZaufaniGuiHolder holder = new CentralaZaufaniGuiHolder(centrala.getId(), kolejnosc);
        Inventory inv = Bukkit.createInventory(holder, 36, "§6Zaufani: " + centrala.getNazwa());
        holder.setInventory(inv);

        if (kolejnosc.isEmpty()) {
            ItemStack brak = new ItemStack(Material.BARRIER);
            ItemMeta meta = brak.getItemMeta();
            meta.setDisplayName("§7Brak zaufanych graczy.");
            meta.setLore(List.of("§7Kliknij „Dodaj gracza” poniżej."));
            brak.setItemMeta(meta);
            inv.setItem(13, brak);
        } else {
            int slot = 0;
            for (UUID gracz : kolejnosc) {
                inv.setItem(slot++, itemZaufanego(gracz));
            }
        }

        ItemStack dodaj = new ItemStack(Material.LIME_DYE);
        ItemMeta dodajMeta = dodaj.getItemMeta();
        dodajMeta.setDisplayName("§aDodaj gracza");
        dodajMeta.setLore(List.of("§7Wpiszesz nick na czacie.", "§7Tylko właściciel może dodawać zaufanych."));
        dodaj.setItemMeta(dodajMeta);
        inv.setItem(SLOT_DODAJ, dodaj);

        ItemStack wroc = new ItemStack(Material.ARROW);
        ustawNazwe(wroc, "§eWróć do panelu centrali");
        inv.setItem(SLOT_WROC, wroc);

        return inv;
    }

    private static ItemStack itemZaufanego(UUID gracz) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + nazwaGracza(gracz));
        meta.setLore(List.of("§7Kliknij, aby usunąć z zaufanych."));
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
