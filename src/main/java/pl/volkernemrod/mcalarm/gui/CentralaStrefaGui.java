package pl.volkernemrod.mcalarm.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Strefa;

import java.util.List;

/**
 * Ekran GUI regulacji rozmiaru strefy — przebudowa Etap 1 (plan ROZSZERZENIE GUI,
 * mem-palace drawer plan-dzialania 9267c9472d0902996490d983).
 *
 * Naprawia nieintuicyjność poprzedniej wersji (rozmiar widoczny tylko w nazwie itemu,
 * IRON_BARS bez zmiany wizualnej) pomysłem Volkera: pasek wypełnionych pól wełny
 * w gradiencie kolorów od zielonego do czerwonego, który ROŚNIE wraz z rozmiarem.
 *
 * Strefa przechowuje dwa narożniki (x1/y1/z1, x2/y2/z2). W GUI operujemy na promieniu
 * (połowie rozmiaru) per oś: promień = (x2-x1)/2, centrum = x1+promień. Model zakłada
 * rozmiar nieparzysty (2r+1) — rozrost jest symetryczny względem środka STREFY, nie
 * względem bloku centrali (może być przesunięty, jeśli strefa nie była wycentrowana).
 *
 * Limity: MIN_PROMIEN = 1 (strefa 3x3x3), MAX_PROMIEN = 7 (strefa 15x15x15) — D3:
 * ten sam limit jest teraz wymuszany również w /alarm zone save (patrz AlarmCommand).
 *
 * Układ slotów (54, 6 rzędów):
 *   Rząd 1 (0–8):    X: [etykieta][pasek promień 1..7 = sloty 1–7][wolne]
 *   Rząd 2 (9–17):   Y: [etykieta][pasek][wolne]
 *   Rząd 3 (18–26):  Z: [etykieta][pasek][wolne]
 *   Rząd 4 (27–35):  [klasa rozmiaru: slot 29][podsumowanie: slot 31]
 *   Rząd 5 (36–44):  (puste)
 *   Rząd 6 (45–53):  [Reset: 45][Zapisz: 47][Pokaż granice: 49][Wróć: 51]
 *
 * Kliknięcie w pole paska = ustawia promień TEJ osi bezpośrednio na wartość kolumny
 * (kolumna 1..7 → promień 1..7), zamiast dawnych przycisków ±1/±5.
 */
public final class CentralaStrefaGui {

    // Etykiety osi (kolumna 0 każdego rzędu)
    public static final int SLOT_LABEL_X = 0;
    public static final int SLOT_LABEL_Y = 9;
    public static final int SLOT_LABEL_Z = 18;

    // Pierwszy slot paska każdej osi (kolumna 1); pasek zajmuje 7 slotów (kolumny 1–7)
    public static final int SLOT_PASEK_X_START = 1;
    public static final int SLOT_PASEK_Y_START = 10;
    public static final int SLOT_PASEK_Z_START = 19;

    public static final int SLOT_KLASA_ROZMIARU = 29;
    public static final int SLOT_PODSUMOWANIE   = 31;

    // Akcje
    public static final int SLOT_RESET  = 45;
    public static final int SLOT_ZAPISZ = 47;
    public static final int SLOT_GRANICE = 49;
    public static final int SLOT_WROC   = 51;

    public static final int MIN_PROMIEN = 1;   // strefa 3x3x3
    public static final int MAX_PROMIEN = 7;   // strefa 15x15x15

    private CentralaStrefaGui() {}

    public static Inventory zbuduj(Centrala centrala, CentralaStrefaGuiHolder holder) {
        Strefa strefa = holder.getRoboczaStrefa();
        Inventory inv = Bukkit.createInventory(holder, 54, "§6Strefa: §f" + centrala.getNazwa());
        holder.setInventory(inv);
        odswiez(inv, holder);
        return inv;
    }

    /** Prze-rysowuje cały ekran na podstawie aktualnego stanu roboczej kopii w holderze. */
    public static void odswiez(Inventory inv, CentralaStrefaGuiHolder holder) {
        Strefa strefa = holder.getRoboczaStrefa();
        int promienX = obliczPromien(strefa.getX1(), strefa.getX2());
        int promienY = obliczPromien(strefa.getY1(), strefa.getY2());
        int promienZ = obliczPromien(strefa.getZ1(), strefa.getZ2());

        inv.setItem(SLOT_LABEL_X, itemEtykieta(Material.RED_BANNER, "§cSzerokość X", promienX));
        inv.setItem(SLOT_LABEL_Y, itemEtykieta(Material.GREEN_BANNER, "§aWysokość Y", promienY));
        inv.setItem(SLOT_LABEL_Z, itemEtykieta(Material.BLUE_BANNER, "§9Głębokość Z", promienZ));

        odswiezPasek(inv, SLOT_PASEK_X_START, promienX);
        odswiezPasek(inv, SLOT_PASEK_Y_START, promienY);
        odswiezPasek(inv, SLOT_PASEK_Z_START, promienZ);

        inv.setItem(SLOT_KLASA_ROZMIARU, itemKlasaRozmiaru(promienX, promienY, promienZ));
        inv.setItem(SLOT_PODSUMOWANIE, itemPodsumowanie(promienX, promienY, promienZ, holder.maNiezapisaneZmiany()));

        inv.setItem(SLOT_RESET, itemAkcji(Material.CLOCK, "§eReset do zapisanej",
                List.of("§7Przywraca ostatnio zapisany rozmiar,", "§7odrzuca niezapisane zmiany na tym ekranie.")));
        inv.setItem(SLOT_ZAPISZ, itemAkcji(Material.EMERALD, "§aZapisz rozmiar strefy",
                List.of("§7Kliknij, by zachować zmiany w bazie.")));
        inv.setItem(SLOT_GRANICE, itemAkcji(Material.SPYGLASS, "§bPokaż granice w świecie",
                List.of("§7Obrys strefy cząsteczkami", "§7(widoczny ok. 10 sekund).")));
        inv.setItem(SLOT_WROC, itemAkcji(Material.ARROW, "§7Wróć do panelu",
                List.of("§7Anuluje niezapisane zmiany.")));
    }

    private static void odswiezPasek(Inventory inv, int slotStart, int aktualnyPromien) {
        for (int kolumna = 1; kolumna <= MAX_PROMIEN; kolumna++) {
            boolean wypelnione = kolumna <= aktualnyPromien;
            boolean aktualny = kolumna == aktualnyPromien;
            inv.setItem(slotStart + (kolumna - 1), itemPolaPaska(kolumna, wypelnione, aktualny));
        }
    }

    /** Jedno pole paska: wełna w gradiencie (wypełnione) albo szara szyba (puste). */
    private static ItemStack itemPolaPaska(int kolumna, boolean wypelnione, boolean aktualny) {
        int rozmiarTegoPola = kolumna * 2 + 1;
        Material mat = wypelnione ? materialGradientu(kolumna) : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat, Math.max(1, rozmiarTegoPola));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(wypelnione
                ? "§fPromień ±" + kolumna + " §7(" + rozmiarTegoPola + " bloków)"
                : "§8Promień ±" + kolumna + " §7(" + rozmiarTegoPola + " bloków)");
        meta.setLore(aktualny
                ? List.of("§aAktualny rozmiar tej osi")
                : List.of("§eKliknij, by ustawić rozmiar na " + rozmiarTegoPola));
        if (aktualny) {
            // "Błysk" (glint) na ostatnim wypełnionym polu, bez pokazywania nazwy zaklęcia.
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Gradient r1–2 LIME, r3–4 YELLOW, r5–6 ORANGE, r7 RED — pomysł Volkera (2026-09-18). */
    private static Material materialGradientu(int kolumna) {
        if (kolumna <= 2) return Material.LIME_WOOL;
        if (kolumna <= 4) return Material.YELLOW_WOOL;
        if (kolumna <= 6) return Material.ORANGE_WOOL;
        return Material.RED_WOOL;
    }

    private static ItemStack itemEtykieta(Material banner, String nazwa, int promien) {
        ItemStack item = new ItemStack(banner);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nazwa);
        meta.setLore(List.of("§7Rozmiar: §f" + (promien * 2 + 1) + " bloków"));
        item.setItemMeta(meta);
        return item;
    }

    /** D5 — ikona klasy rozmiaru wg objętości (mała/średnia/duża), zrobione TERAZ w Etapie 1. */
    private static ItemStack itemKlasaRozmiaru(int promienX, int promienY, int promienZ) {
        long objetosc = (long) (promienX * 2 + 1) * (promienY * 2 + 1) * (promienZ * 2 + 1);
        Material mat;
        String klasa;
        if (objetosc <= 125) {
            mat = Material.CRAFTING_TABLE;
            klasa = "§fMała";
        } else if (objetosc <= 729) {
            mat = Material.OAK_DOOR;
            klasa = "§fŚrednia";
        } else {
            mat = Material.BEACON;
            klasa = "§fDuża";
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Klasa rozmiaru: " + klasa);
        meta.setLore(List.of("§7Objętość: §f" + objetosc + " bloków"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemPodsumowanie(int promienX, int promienY, int promienZ, boolean niezapisane) {
        int szer = promienX * 2 + 1, wys = promienY * 2 + 1, glob = promienZ * 2 + 1;
        long objetosc = (long) szer * wys * glob;
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§bStrefa §f" + szer + "×" + wys + "×" + glob + " §7= §f" + objetosc + " bloków");
        meta.setLore(niezapisane
                ? List.of("§e● Niezapisane zmiany — kliknij Zapisz, by je zachować.")
                : List.of("§aWszystkie zmiany zapisane."));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack itemAkcji(Material mat, String nazwa, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nazwa);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Oblicza promień symetryczny z narożników, przycięty do [MIN_PROMIEN, MAX_PROMIEN]. */
    public static int obliczPromien(int min, int max) {
        return Math.min(MAX_PROMIEN, Math.max(MIN_PROMIEN, (max - min) / 2));
    }

    /** Przelicza nowe narożniki symetrycznie względem centrum. */
    public static int[] przeliczNarozniki(int min, int max, int nowyPromien) {
        int centrum = min + (max - min) / 2;
        return new int[]{centrum - nowyPromien, centrum + nowyPromien};
    }

    /**
     * Rozpoznaje kliknięcie w pasek osi: zwraca promień [1..7] odpowiadający kolumnie,
     * albo -1 gdy slot nie jest częścią żadnego paska.
     */
    public static int promienZSlotuPaska(int slot, int slotStart) {
        int kolumna = slot - slotStart + 1;
        return (kolumna >= 1 && kolumna <= MAX_PROMIEN) ? kolumna : -1;
    }
}
