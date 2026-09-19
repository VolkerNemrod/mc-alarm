package pl.volkernemrod.mcalarm.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.core.AlarmEngine;
import pl.volkernemrod.mcalarm.core.CentralaRejestr;
import pl.volkernemrod.mcalarm.core.IncydentManager;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Rola;
import pl.volkernemrod.mcalarm.model.Strefa;
import pl.volkernemrod.mcalarm.model.TrybPracy;
import pl.volkernemrod.mcalarm.model.TypZdarzenia;
import pl.volkernemrod.mcalarm.model.Waznosc;
import pl.volkernemrod.mcalarm.storage.KonfiguracjaReakcjiRepository;
import pl.volkernemrod.mcalarm.storage.ZdarzenieRepository;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Silnik zdarzeń — Etap 4 planu działania. Rejestruje: wejście/wyjście gracza
 * ze strefy, niszczenie/stawianie bloków w strefie (poza samym blokiem
 * centrali — ten obsługuje CentralaBlockListener), otwieranie kontenerów
 * i realną zmianę ich zawartości (diff przed/po).
 *
 * Klasyfikacja ważności (obliczWaznosc*) to ROBOCZE ZAŁOŻENIE Claude —
 * do zweryfikowania z Volkerem względem dokładnej tabeli w GAMEPLAY_SPEC
 * rozdz. 74, gdy będziemy robić Etap 5 (konfiguracja_reakcji). Na razie:
 * właściciel/zaufany = zawsze INFO; obcy = wyższa ważność, gdy centrala
 * jest uzbrojona (CZUWANIE/ALARM) niż gdy rozbrojona/monitoring.
 *
 * "Sprawca: NIEZNANY" (gracz=null) w tej klasie NIE występuje — wszystkie
 * obsługiwane tu zdarzenia mają jednoznacznego gracza. Przypadki naprawdę
 * niejednoznaczne (hoppery, redstone, eksplozje) to V1.1+, patrz
 * TypZdarzenia i GAMEPLAY_SPEC rozdz. 24, 66 — nieobsługiwane w V1.
 */
public class StrefaZdarzeniaListener implements Listener {

    private final VolkerNemrodAlarmPlugin plugin;
    private final CentralaRejestr rejestr;
    private final ZdarzenieRepository zdarzenieRepository;
    private final KonfiguracjaReakcjiRepository konfiguracjaReakcji;
    private final AlarmEngine alarmEngine;
    private final IncydentManager incydentManager;

    /** Zestaw stref, w których gracz aktualnie przebywa — do wykrywania wejść/wyjść. */
    private final Map<UUID, Set<UUID>> aktualneStrefyGracza = new HashMap<>();

    /** Zrzut zawartości kontenera przy otwarciu: klucz "gracz|swiat;x;y;z" -> zawartość. */
    private final Map<String, ItemStack[]> snapshotKontenerow = new HashMap<>();

    /**
     * Etap 9.2 — cooldown wejścia do strefy (GAMEPLAY_SPEC rozdz. 65).
     * Gracz oscylujący na granicy strefy mógłby generować szybkie sekwencje
     * ENTER→EXIT→ENTER w ułamku sekundy. Cooldown 2s na ENTER per gracz+strefa
     * eliminuje zduplikowane wpisy bez utraty faktycznych wejść.
     * Klucz: graczId + "|" + strefaId. Wartość: System.nanoTime() ostatniego ENTER.
     */
    private final Map<String, Long> ostatnieWejscie = new HashMap<>();
    private static final long COOLDOWN_WEJSCIA_NS = TimeUnit.SECONDS.toNanos(2);

    /**
     * Etap 9.3 — cooldown niszczenia/stawiania bloków (GAMEPLAY_SPEC rozdz. 65).
     * Gracz niszczący wiele bloków pod rząd generuje N zdarzeń w ułamkach sekundy.
     * Cooldown 500ms per gracz+strefa+typ ogranicza to do 1 wpisu co pół sekundy.
     * Plugin NIE blokuje akcji Bukkit — tylko pomija zapis zdarzenia.
     * Klucz: graczId + "|" + strefaId + "|" + typ. Wartość: System.nanoTime() ostatniego zapisu.
     */
    private final Map<String, Long> ostatniBlok = new HashMap<>();
    private static final long COOLDOWN_BLOKU_NS = TimeUnit.MILLISECONDS.toNanos(500);

    /**
     * Etap 9.4 — cooldown otwierania kontenera (GAMEPLAY_SPEC rozdz. 65).
     * Gracz wielokrotnie otwierający tę samą skrzynię generuje N wpisów CONTAINER_OPEN.
     * Cooldown 5s per gracz+kontener (pozycja) ogranicza to do 1 wpisu co 5 sekund.
     * Diff zawartości (ITEM_REMOVE/ADD) nie wymaga cooldownu — jest z natury jednorazowy
     * na jedno zamknięcie kontenera.
     * Klucz: graczId + "|" + swiat;x;y;z kontenera. Wartość: System.nanoTime() ostatniego zapisu.
     */
    private final Map<String, Long> ostatnieOtwarcieKontenera = new HashMap<>();
    private static final long COOLDOWN_KONTENERA_NS = TimeUnit.SECONDS.toNanos(5);

    public StrefaZdarzeniaListener(VolkerNemrodAlarmPlugin plugin, CentralaRejestr rejestr,
                                    ZdarzenieRepository zdarzenieRepository,
                                    KonfiguracjaReakcjiRepository konfiguracjaReakcji, AlarmEngine alarmEngine,
                                    IncydentManager incydentManager) {
        this.plugin = plugin;
        this.rejestr = rejestr;
        this.zdarzenieRepository = zdarzenieRepository;
        this.konfiguracjaReakcji = konfiguracjaReakcji;
        this.alarmEngine = alarmEngine;
        this.incydentManager = incydentManager;
    }

    // ===================== WEJŚCIE / WYJŚCIE ZE STREFY =====================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Optymalizacja: reagujemy tylko na zmianę bloku, nie na obrót głowy.
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld().equals(to.getWorld())) {
            return;
        }
        przeliczStrefyGracza(event.getPlayer(), to);
    }

    /**
     * Etap 9.5 — obsługa teleportacji (GAMEPLAY_SPEC rozdz. 65).
     * PlayerTeleportEvent nie przechodzi przez PlayerMoveEvent — bez tego listenera
     * gracz teleportowany do/ze strefy nie generowałby ENTER/EXIT.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        przeliczStrefyGracza(event.getPlayer(), to);
    }

    /**
     * Wspólna logika wykrywania wejść/wyjść ze stref — wywoływana zarówno
     * z onPlayerMove jak i z onPlayerTeleport (Etap 9.5).
     */
    private void przeliczStrefyGracza(Player player, Location to) {
        UUID graczId = player.getUniqueId();
        Set<UUID> byloW = aktualneStrefyGracza.computeIfAbsent(graczId, k -> new HashSet<>());
        Set<UUID> terazW = new HashSet<>();

        for (Strefa strefa : rejestr.wszystkieStrefy()) {
            if (strefa.isAktywna() && strefa.zawiera(to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ())) {
                terazW.add(strefa.getId());
            }
        }

        for (UUID strefaId : terazW) {
            if (!byloW.contains(strefaId)) {
                // Etap 9.2 — cooldown wejścia: ignoruj jeśli ostatnie wejście było < 2s temu.
                String kluczCooldown = graczId + "|" + strefaId;
                long teraz = System.nanoTime();
                Long ostatnie = ostatnieWejscie.get(kluczCooldown);
                if (ostatnie != null && (teraz - ostatnie) < COOLDOWN_WEJSCIA_NS) {
                    byloW.add(strefaId); // traktuj jak "nadal w strefie" — nie rejestruj ponownego wejścia
                    continue;
                }
                ostatnieWejscie.put(kluczCooldown, teraz);
                obsluzPrzejscie(player, strefaId, true, to);
            }
        }
        for (UUID strefaId : byloW) {
            if (!terazW.contains(strefaId)) {
                obsluzPrzejscie(player, strefaId, false, to);
            }
        }

        aktualneStrefyGracza.put(graczId, terazW);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID graczId = player.getUniqueId();
        Set<UUID> strefyGraczaWMomencieWyjscia = aktualneStrefyGracza.remove(graczId);
        // Etap 9.2 — czyścimy cooldowny wejścia po wylogowaniu gracza.
        ostatnieWejscie.entrySet().removeIf(e -> e.getKey().startsWith(graczId + "|"));
        if (strefyGraczaWMomencieWyjscia == null) {
            return;
        }
        // Gracz zniknął (/kick, crash, disconnect) będąc w środku strefy — zamykamy
        // wszystkie jego otwarte incydenty, żeby nie zostały otwarte w nieskończoność.
        for (UUID strefaId : strefyGraczaWMomencieWyjscia) {
            rejestr.getStrefa(strefaId).ifPresent(strefa -> rejestr.getCentrala(strefa.getCentralaId()).ifPresent(centrala -> {
                if (centrala.rolaGracza(graczId) == Rola.OBCY) {
                    incydentManager.zamknij(graczId, centrala.getId());
                }
            }));
        }
    }

    private void obsluzPrzejscie(Player player, UUID strefaId, boolean wejscie, Location pozycja) {
        rejestr.getStrefa(strefaId).ifPresent(strefa -> rejestr.getCentrala(strefa.getCentralaId()).ifPresent(centrala -> {
            Rola rola = centrala.rolaGracza(player.getUniqueId());

            if (wejscie && rola == Rola.OBCY && centrala.getTryb().isUzbrojona()) {
                incydentManager.otworzLubPobierz(player.getUniqueId(), centrala.getId(), strefa.getId());
            }

            Waznosc waznosc = obliczWaznoscRuchu(centrala, rola);
            TypZdarzenia typ = wejscie ? TypZdarzenia.PLAYER_ENTER_ZONE : TypZdarzenia.PLAYER_EXIT_ZONE;
            String szczegoly = (wejscie ? "wszedł do strefy " : "opuścił strefę ") + strefa.getNazwa();

            zapiszZdarzenie(centrala, strefa, player.getUniqueId(), typ, waznosc, szczegoly,
                    pozycja.getWorld().getName(), pozycja.getBlockX(), pozycja.getBlockY(), pozycja.getBlockZ());

            if (wejscie) {
                sprawdzAlarm(centrala, strefa, rola, typ);
            } else if (rola == Rola.OBCY) {
                incydentManager.zamknij(player.getUniqueId(), centrala.getId());
            }
        }));
    }

    private Waznosc obliczWaznoscRuchu(Centrala centrala, Rola rola) {
        if (rola != Rola.OBCY) {
            return Waznosc.INFO;
        }
        return centrala.getTryb().isUzbrojona() ? Waznosc.WARNING : Waznosc.INFO;
    }

    // ===================== BLOKI W STREFIE =====================

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        obslugaBloku(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType(), TypZdarzenia.BLOCK_BREAK, "zniszczył");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        obslugaBloku(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType(), TypZdarzenia.BLOCK_PLACE, "postawił");
    }

    private void obslugaBloku(Player player, Location loc, Material typBloku, TypZdarzenia typZdarzenia, String czasownik) {
        String swiat = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        // Sam blok centrali obsługuje CentralaBlockListener — tu go pomijamy,
        // żeby nie dublować/zaśmiecać historii (i tak zostanie skasowana przy zniszczeniu).
        if (rejestr.znajdzCentralaIdPoLokalizacji(swiat, x, y, z).isPresent()) {
            return;
        }

        for (Strefa strefa : rejestr.wszystkieStrefy()) {
            if (!strefa.isAktywna() || !strefa.zawiera(swiat, x, y, z)) {
                continue;
            }
            rejestr.getCentrala(strefa.getCentralaId()).ifPresent(centrala -> {
                // Etap 9.3 — cooldown bloków: pomijamy zapis jeśli poprzedni był < 500ms temu.
                String kluczCooldown = player.getUniqueId() + "|" + strefa.getId() + "|" + typZdarzenia.name();
                long teraz = System.nanoTime();
                Long ostatni = ostatniBlok.get(kluczCooldown);
                if (ostatni != null && (teraz - ostatni) < COOLDOWN_BLOKU_NS) {
                    return; // pomijamy zapis — nie blokujemy eventu Bukkit
                }
                ostatniBlok.put(kluczCooldown, teraz);

                Rola rola = centrala.rolaGracza(player.getUniqueId());
                Waznosc waznosc = obliczWaznoscBloku(centrala, rola);
                String szczegoly = czasownik + " blok " + typBloku;
                zapiszZdarzenie(centrala, strefa, player.getUniqueId(), typZdarzenia, waznosc, szczegoly, swiat, x, y, z);
                sprawdzAlarm(centrala, strefa, rola, typZdarzenia);
            });
        }
    }

    private Waznosc obliczWaznoscBloku(Centrala centrala, Rola rola) {
        if (rola != Rola.OBCY) {
            return Waznosc.INFO;
        }
        return centrala.getTryb().isUzbrojona() ? Waznosc.SUSPICIOUS : Waznosc.WARNING;
    }

    // ===================== KONTENERY (otwarcie + diff zawartości) =====================

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity kto = event.getPlayer();
        if (!(kto instanceof Player player)) {
            return;
        }
        Location loc = event.getInventory().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return; // to nie jest kontener przypisany do bloku w świecie (np. ekwipunek, crafting)
        }

        Strefa strefa = znajdzStrefe(loc);
        if (strefa == null) {
            return;
        }
        Centrala centrala = rejestr.getCentrala(strefa.getCentralaId()).orElse(null);
        if (centrala == null) {
            return;
        }

        // Zrzut zawartości do porównania przy zamknięciu.
        snapshotKontenerow.put(kluczSnapshotu(player, loc), sklonuj(event.getInventory().getContents()));

        // Etap 9.4 — cooldown CONTAINER_OPEN: pomijamy zapis jeśli poprzednie otwarcie
        // tej samej skrzyni było < 5s temu. Snapshot robimy zawsze (dla diffu przy zamknięciu).
        String kluczCooldown = player.getUniqueId() + "|" + loc.getWorld().getName()
                + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
        long teraz = System.nanoTime();
        Long ostatnie = ostatnieOtwarcieKontenera.get(kluczCooldown);
        if (ostatnie != null && (teraz - ostatnie) < COOLDOWN_KONTENERA_NS) {
            return; // pomijamy tylko zapis zdarzenia CONTAINER_OPEN
        }
        ostatnieOtwarcieKontenera.put(kluczCooldown, teraz);

        Rola rola = centrala.rolaGracza(player.getUniqueId());
        Waznosc waznosc = rola != Rola.OBCY ? Waznosc.INFO
                : (centrala.getTryb().isUzbrojona() ? Waznosc.WARNING : Waznosc.INFO);

        zapiszZdarzenie(centrala, strefa, player.getUniqueId(), TypZdarzenia.CONTAINER_OPEN, waznosc,
                "otworzył kontener (" + loc.getBlock().getType() + ")",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity kto = event.getPlayer();
        if (!(kto instanceof Player player)) {
            return;
        }
        Location loc = event.getInventory().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        ItemStack[] przed = snapshotKontenerow.remove(kluczSnapshotu(player, loc));
        if (przed == null) {
            return; // nie był śledzony (nie był w strefie w momencie otwarcia)
        }

        Strefa strefa = znajdzStrefe(loc);
        if (strefa == null) {
            return;
        }
        Centrala centrala = rejestr.getCentrala(strefa.getCentralaId()).orElse(null);
        if (centrala == null) {
            return;
        }

        Map<Material, Integer> liczbaPrzed = zlicz(przed);
        Map<Material, Integer> liczbaPo = zlicz(event.getInventory().getContents());

        Map<Material, Integer> usuniete = new HashMap<>();
        Map<Material, Integer> dodane = new HashMap<>();

        Set<Material> wszystkieMaterialy = new HashSet<>();
        wszystkieMaterialy.addAll(liczbaPrzed.keySet());
        wszystkieMaterialy.addAll(liczbaPo.keySet());

        for (Material m : wszystkieMaterialy) {
            int delta = liczbaPo.getOrDefault(m, 0) - liczbaPrzed.getOrDefault(m, 0);
            if (delta < 0) {
                usuniete.put(m, -delta);
            } else if (delta > 0) {
                dodane.put(m, delta);
            }
        }

        Rola rola = centrala.rolaGracza(player.getUniqueId());
        String swiat = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        if (!usuniete.isEmpty()) {
            Waznosc waznosc = rola != Rola.OBCY ? Waznosc.INFO
                    : (centrala.getTryb().isUzbrojona() ? Waznosc.CRITICAL : Waznosc.SUSPICIOUS);
            zapiszZdarzenie(centrala, strefa, player.getUniqueId(), TypZdarzenia.CONTAINER_ITEM_REMOVE, waznosc,
                    "wyjął: " + formatuj(usuniete), swiat, x, y, z);
            sprawdzAlarm(centrala, strefa, rola, TypZdarzenia.CONTAINER_ITEM_REMOVE);
        }
        if (!dodane.isEmpty()) {
            Waznosc waznosc = rola != Rola.OBCY ? Waznosc.INFO
                    : (centrala.getTryb().isUzbrojona() ? Waznosc.WARNING : Waznosc.INFO);
            zapiszZdarzenie(centrala, strefa, player.getUniqueId(), TypZdarzenia.CONTAINER_ITEM_ADD, waznosc,
                    "włożył: " + formatuj(dodane), swiat, x, y, z);
            sprawdzAlarm(centrala, strefa, rola, TypZdarzenia.CONTAINER_ITEM_ADD);
        }
    }

    private Strefa znajdzStrefe(Location loc) {
        for (Strefa strefa : rejestr.wszystkieStrefy()) {
            if (strefa.isAktywna() && strefa.zawiera(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                return strefa;
            }
        }
        return null;
    }

    private static String kluczSnapshotu(Player player, Location loc) {
        return player.getUniqueId() + "|" + loc.getWorld().getName() + ";"
                + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private static ItemStack[] sklonuj(ItemStack[] zrodlo) {
        ItemStack[] kopia = new ItemStack[zrodlo.length];
        for (int i = 0; i < zrodlo.length; i++) {
            kopia[i] = zrodlo[i] == null ? null : zrodlo[i].clone();
        }
        return kopia;
    }

    private static Map<Material, Integer> zlicz(ItemStack[] items) {
        Map<Material, Integer> wynik = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            wynik.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return wynik;
    }

    private static String formatuj(Map<Material, Integer> pozycje) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> wpis : pozycje.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(wpis.getValue()).append("x ").append(wpis.getKey());
        }
        return sb.toString();
    }

    // ===================== WSPÓLNE =====================

    private void sprawdzAlarm(Centrala centrala, Strefa strefa, Rola rola, TypZdarzenia typ) {
        if (rola != Rola.OBCY || centrala.getTryb() != TrybPracy.CZUWANIE) {
            return;
        }
        try {
            if (konfiguracjaReakcji.czyWywolujeAlarm(centrala.getId(), typ)) {
                alarmEngine.wywolajAlarm(centrala, strefa);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu konfiguracji reakcji.", e);
        }
    }

    private void zapiszZdarzenie(Centrala centrala, Strefa strefa, UUID gracz, TypZdarzenia typ,
                                  Waznosc waznosc, String szczegoly, String swiat, int x, int y, int z) {
        UUID incydentId = gracz != null ? incydentManager.pobierzOtwarty(gracz, centrala.getId()) : null;
        try {
            zdarzenieRepository.zarejestruj(centrala.getId(), strefa == null ? null : strefa.getId(), incydentId,
                    gracz, typ, waznosc, szczegoly, swiat, x, y, z);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu zdarzenia do bazy.", e);
        }
    }
}
