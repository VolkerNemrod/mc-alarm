package pl.volkernemrod.mcalarm.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.core.CentralaRejestr;
import pl.volkernemrod.mcalarm.gui.CentralaGui;
import pl.volkernemrod.mcalarm.gui.CentralaGuiHolder;
import pl.volkernemrod.mcalarm.gui.CentralaHistoriaGui;
import pl.volkernemrod.mcalarm.gui.CentralaHistoriaGuiHolder;
import pl.volkernemrod.mcalarm.gui.CentralaIncydentyGui;
import pl.volkernemrod.mcalarm.gui.CentralaIncydentyGuiHolder;
import pl.volkernemrod.mcalarm.gui.CentralaIncydentSzczegolyGui;
import pl.volkernemrod.mcalarm.gui.CentralaIncydentSzczegolyGuiHolder;
import pl.volkernemrod.mcalarm.gui.CentralaReakcjeGui;
import pl.volkernemrod.mcalarm.gui.CentralaReakcjeGuiHolder;
import pl.volkernemrod.mcalarm.gui.CentralaStrefaGui;
import pl.volkernemrod.mcalarm.gui.CentralaStrefaGuiHolder;
import pl.volkernemrod.mcalarm.gui.CentralaZaufaniGui;
import pl.volkernemrod.mcalarm.gui.CentralaZaufaniGuiHolder;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Incydent;
import pl.volkernemrod.mcalarm.model.Rola;
import pl.volkernemrod.mcalarm.model.Strefa;
import pl.volkernemrod.mcalarm.model.TrybPracy;
import pl.volkernemrod.mcalarm.model.TypZdarzenia;
import pl.volkernemrod.mcalarm.model.Zdarzenie;
import pl.volkernemrod.mcalarm.storage.CentralaRepository;
import pl.volkernemrod.mcalarm.storage.IncydentRepository;
import pl.volkernemrod.mcalarm.storage.KonfiguracjaReakcjiRepository;
import pl.volkernemrod.mcalarm.storage.StrefaRepository;
import pl.volkernemrod.mcalarm.storage.ZdarzenieRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Otwiera GUI centrali po prawoklikowaniu bloku centrali i obsługuje kliknięcia
 * wewnątrz wszystkich ekranów GUI. Plan działania — Etapy 7–9 c.d.
 */
public class CentralaGuiListener implements Listener {

    private final VolkerNemrodAlarmPlugin plugin;
    private final CentralaRejestr rejestr;
    private final CentralaRepository centralaRepository;
    private final ZdarzenieRepository zdarzenieRepository;
    private final KonfiguracjaReakcjiRepository konfiguracjaReakcjiRepository;
    private final IncydentRepository incydentRepository;
    private final StrefaRepository strefaRepository;

    /** Etap GUI-nazwa/Etap 4: gracze oczekujący na wpis na czacie (nowa nazwa LUB nick zaufanego). Klucz: graczId. */
    private final Map<UUID, OczekiwanyWpis> oczekujacyNaWpis = new HashMap<>();

    private enum TypWpisu { NAZWA, ZAUFANY }

    private record OczekiwanyWpis(UUID centralaId, TypWpisu typ) {}

    private static final int LICZBA_INCYDENTOW_W_GUI = 27;
    private static final int LICZBA_OSTATNICH_GRACZY_HISTORIA = 15;

    public CentralaGuiListener(VolkerNemrodAlarmPlugin plugin, CentralaRejestr rejestr,
                                CentralaRepository centralaRepository, ZdarzenieRepository zdarzenieRepository,
                                KonfiguracjaReakcjiRepository konfiguracjaReakcjiRepository,
                                IncydentRepository incydentRepository) {
        this.plugin = plugin;
        this.rejestr = rejestr;
        this.centralaRepository = centralaRepository;
        this.zdarzenieRepository = zdarzenieRepository;
        this.konfiguracjaReakcjiRepository = konfiguracjaReakcjiRepository;
        this.incydentRepository = incydentRepository;
        this.strefaRepository = plugin.getStrefaRepository();
    }

    // ===================== OTWARCIE GUI (klik na blok centrali) =====================

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Optional<UUID> centralaId = rejestr.znajdzCentralaIdPoLokalizacji(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (centralaId.isEmpty()) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("alarm.use")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.use).");
            return;
        }

        rejestr.getCentrala(centralaId.get()).ifPresentOrElse(
                centrala -> {
                    // Etap GUI-A: tylko właściciel i zaufani mogą otwierać GUI.
                    // Obcy może jedynie fizycznie zniszczyć blok.
                    Rola rola = centrala.rolaGracza(player.getUniqueId());
                    if (rola == Rola.OBCY) {
                        player.sendMessage("§c[VolkerNemrodAlarm] To nie jest twoja centrala.");
                        return;
                    }
                    player.openInventory(CentralaGui.zbuduj(centrala));
                },
                () -> player.sendMessage("§c[VolkerNemrodAlarm] Błąd — centrala nie znaleziona w rejestrze.")
        );
    }

    // ===================== ROUTING KLIKNIĘĆ =====================

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CentralaGuiHolder holder) {
            obslugaKlikuPanel(event, holder);
        } else if (event.getInventory().getHolder() instanceof CentralaHistoriaGuiHolder holder) {
            obslugaKlikuHistoria(event, holder);
        } else if (event.getInventory().getHolder() instanceof CentralaZaufaniGuiHolder holder) {
            obslugaKlikuZaufani(event, holder);
        } else if (event.getInventory().getHolder() instanceof CentralaReakcjeGuiHolder holder) {
            obslugaKlikuReakcje(event, holder);
        } else if (event.getInventory().getHolder() instanceof CentralaIncydentyGuiHolder holder) {
            obslugaKlikuIncydenty(event, holder);
        } else if (event.getInventory().getHolder() instanceof CentralaIncydentSzczegolyGuiHolder holder) {
            obslugaKlikuIncydentSzczegoly(event, holder);
        } else if (event.getInventory().getHolder() instanceof CentralaStrefaGuiHolder holder) {
            obslugaKlikuStrefa(event, holder);
        }
    }

    // ===================== PANEL GŁÓWNY =====================

    private void obslugaKlikuPanel(InventoryClickEvent event, CentralaGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }

        int slot = event.getSlot();
        if (slot == CentralaGui.SLOT_PRZELACZNIK) {
            przelaczTryb(player, centrala);
        } else if (slot == CentralaGui.SLOT_HISTORIA) {
            otworzHistorie(player, centrala);
        } else if (slot == CentralaGui.SLOT_ZAUFANI) {
            otworzZaufani(player, centrala);
        } else if (slot == CentralaGui.SLOT_REAKCJE) {
            otworzReakcje(player, centrala);
        } else if (slot == CentralaGui.SLOT_STREFA) {
            otworzStrefe(player, centrala);
        } else if (slot == CentralaGui.SLOT_INFO) {
            otworzZmianaNazwy(player, centrala);
        } else if (slot == CentralaGui.SLOT_INCYDENTY) {
            otworzIncydenty(player, centrala);
        } else if (slot == CentralaGui.SLOT_ZAMKNIJ) {
            player.closeInventory();
        }
    }

    // ===================== HISTORIA (Etap 5 — filtry i stronicowanie) =====================

    private void obslugaKlikuHistoria(InventoryClickEvent event, CentralaHistoriaGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }

        int slot = event.getSlot();
        if (slot == CentralaHistoriaGui.SLOT_WROC) {
            player.openInventory(CentralaGui.zbuduj(centrala));
            return;
        }
        if (slot == CentralaHistoriaGui.SLOT_POPRZEDNIA) {
            if (holder.getStrona() > 0) {
                holder.setStrona(holder.getStrona() - 1);
                odswiezHistorie(player, holder, event.getInventory());
            }
            return;
        }
        if (slot == CentralaHistoriaGui.SLOT_NASTEPNA) {
            if (holder.isMaNastepnaStrona()) {
                holder.setStrona(holder.getStrona() + 1);
                odswiezHistorie(player, holder, event.getInventory());
            }
            return;
        }
        if (slot == CentralaHistoriaGui.SLOT_WYCZYSC) {
            holder.setFiltrTyp(null);
            holder.setFiltrGracz(null);
            holder.setStrona(0);
            odswiezHistorie(player, holder, event.getInventory());
            return;
        }
        if (slot == CentralaHistoriaGui.SLOT_FILTR_TYP) {
            holder.setFiltrTyp(nastepnyTypFiltra(holder.getFiltrTyp(), event.isShiftClick()));
            holder.setStrona(0);
            odswiezHistorie(player, holder, event.getInventory());
            return;
        }
        if (slot == CentralaHistoriaGui.SLOT_FILTR_GRACZ) {
            holder.setFiltrGracz(nastepnyGraczFiltra(holder, event.isShiftClick()));
            holder.setStrona(0);
            odswiezHistorie(player, holder, event.getInventory());
        }
    }

    private void otworzHistorie(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.history")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.history).");
            return;
        }
        try {
            CentralaHistoriaGuiHolder holder = new CentralaHistoriaGuiHolder(centrala.getId());
            holder.setOstatniGracze(zdarzenieRepository.znajdzOstatnichGraczy(centrala.getId(), LICZBA_OSTATNICH_GRACZY_HISTORIA));
            List<Zdarzenie> zdarzenia = pobierzStroneHistorii(holder);
            player.openInventory(CentralaHistoriaGui.zbuduj(centrala, holder, zdarzenia));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu historii do GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    /** Pobiera bieżącą stronę wg filtrów z holdera; prosi o ROZMIAR_STRONY+1, żeby wykryć kolejną stronę. */
    private List<Zdarzenie> pobierzStroneHistorii(CentralaHistoriaGuiHolder holder) throws SQLException {
        int limit = CentralaHistoriaGui.ROZMIAR_STRONY;
        int offset = holder.getStrona() * limit;
        List<Zdarzenie> wynik = zdarzenieRepository.znajdzZFiltrami(
                holder.getCentralaId(), holder.getFiltrGracz(), holder.getFiltrTyp(), limit + 1, offset);
        boolean maNastepna = wynik.size() > limit;
        holder.setMaNastepnaStrona(maNastepna);
        return maNastepna ? wynik.subList(0, limit) : wynik;
    }

    private void odswiezHistorie(Player player, CentralaHistoriaGuiHolder holder, org.bukkit.inventory.Inventory inv) {
        try {
            List<Zdarzenie> zdarzenia = pobierzStroneHistorii(holder);
            CentralaHistoriaGui.odswiez(inv, holder, zdarzenia);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu historii do GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    /** D4 — kolejny/poprzedni typ w cyklu null(wszystkie) → FILTROWALNE_TYPY → null. */
    private static TypZdarzenia nastepnyTypFiltra(TypZdarzenia obecny, boolean wstecz) {
        TypZdarzenia[] typy = CentralaHistoriaGui.FILTROWALNE_TYPY;
        if (obecny == null) {
            return wstecz ? typy[typy.length - 1] : typy[0];
        }
        int idx = -1;
        for (int i = 0; i < typy.length; i++) {
            if (typy[i] == obecny) { idx = i; break; }
        }
        if (idx == -1) return null;
        int nowyIdx = wstecz ? idx - 1 : idx + 1;
        return (nowyIdx < 0 || nowyIdx >= typy.length) ? null : typy[nowyIdx];
    }

    /** D4 — kolejny/poprzedni gracz w cyklu null(wszyscy) → ostatniGracze → null. */
    private static UUID nastepnyGraczFiltra(CentralaHistoriaGuiHolder holder, boolean wstecz) {
        List<UUID> lista = holder.getOstatniGracze();
        if (lista.isEmpty()) return null;
        UUID obecny = holder.getFiltrGracz();
        if (obecny == null) {
            return wstecz ? lista.get(lista.size() - 1) : lista.get(0);
        }
        int idx = lista.indexOf(obecny);
        if (idx == -1) return null;
        int nowyIdx = wstecz ? idx - 1 : idx + 1;
        return (nowyIdx < 0 || nowyIdx >= lista.size()) ? null : lista.get(nowyIdx);
    }

    // ===================== ZAUFANI =====================

    private void obslugaKlikuZaufani(InventoryClickEvent event, CentralaZaufaniGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }

        int slot = event.getSlot();
        if (slot == CentralaZaufaniGui.SLOT_WROC) {
            player.openInventory(CentralaGui.zbuduj(centrala));
            return;
        }
        if (slot == CentralaZaufaniGui.SLOT_DODAJ) {
            // D1 (2026-09-18) — zarządzanie zaufanymi (dodawanie) tylko właściciel.
            if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
                player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może dodawać zaufanych.");
                return;
            }
            otworzDodajZaufanego(player, centrala);
            return;
        }

        UUID gracz = holder.getGraczWSlocie(slot);
        if (gracz == null) {
            return;
        }
        if (!player.hasPermission("alarm.trusted")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.trusted).");
            return;
        }
        // D1 (2026-09-18) — zarządzanie zaufanymi (usuwanie) tylko właściciel.
        if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
            player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może zarządzać zaufanymi.");
            return;
        }

        centrala.usunZaufanego(gracz);
        try {
            centralaRepository.zapisz(centrala);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu zaufanych z GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
            return;
        }
        rejestr.odswiezCentrala(centrala);
        player.openInventory(CentralaZaufaniGui.zbuduj(centrala));
    }

    private void otworzZaufani(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.trusted")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.trusted).");
            return;
        }
        player.openInventory(CentralaZaufaniGui.zbuduj(centrala));
    }

    /** Etap 4 (dawny) — prompt na czacie do dodania zaufanego, analogicznie do zmiany nazwy. */
    private void otworzDodajZaufanego(Player player, Centrala centrala) {
        player.closeInventory();
        oczekujacyNaWpis.put(player.getUniqueId(), new OczekiwanyWpis(centrala.getId(), TypWpisu.ZAUFANY));
        player.sendMessage("§e[VolkerNemrodAlarm] Wpisz nick gracza, którego chcesz dodać do zaufanych.");
        player.sendMessage("§e(lub §fcancel§e, by anulować).");
    }

    // ===================== REAKCJE =====================

    private void obslugaKlikuReakcje(InventoryClickEvent event, CentralaReakcjeGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }

        int slot = event.getSlot();
        if (slot == CentralaReakcjeGui.SLOT_WROC) {
            player.openInventory(CentralaGui.zbuduj(centrala));
            return;
        }

        int indeksTypu = -1;
        for (int i = 0; i < CentralaReakcjeGui.SLOTY.length; i++) {
            if (CentralaReakcjeGui.SLOTY[i] == slot) {
                indeksTypu = i;
                break;
            }
        }
        if (indeksTypu == -1) {
            return;
        }
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        // D1 (2026-09-18) — zarządzanie reakcjami tylko właściciel.
        if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
            player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może zarządzać reakcjami.");
            return;
        }

        TypZdarzenia typ = CentralaReakcjeGui.TYPY.get(indeksTypu);
        try {
            boolean obecnie = konfiguracjaReakcjiRepository.czyWywolujeAlarm(centrala.getId(), typ);
            konfiguracjaReakcjiRepository.ustaw(centrala.getId(), typ, !obecnie);

            Map<TypZdarzenia, Boolean> stan = new EnumMap<>(TypZdarzenia.class);
            for (TypZdarzenia t : CentralaReakcjeGui.TYPY) {
                stan.put(t, konfiguracjaReakcjiRepository.czyWywolujeAlarm(centrala.getId(), t));
            }
            player.openInventory(CentralaReakcjeGui.zbuduj(centrala, stan));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu konfiguracji reakcji z GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    private void otworzReakcje(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        try {
            Map<TypZdarzenia, Boolean> stan = new EnumMap<>(TypZdarzenia.class);
            for (TypZdarzenia t : CentralaReakcjeGui.TYPY) {
                stan.put(t, konfiguracjaReakcjiRepository.czyWywolujeAlarm(centrala.getId(), t));
            }
            player.openInventory(CentralaReakcjeGui.zbuduj(centrala, stan));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu konfiguracji reakcji do GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    // ===================== INCYDENTY =====================

    private void obslugaKlikuIncydenty(InventoryClickEvent event, CentralaIncydentyGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }

        int slot = event.getSlot();

        if (slot == CentralaIncydentyGui.SLOT_WROC) {
            player.openInventory(CentralaGui.zbuduj(centrala));
            return;
        }

        // Klik na incydent — otwórz szczegóły (punkt E).
        UUID incydentId = holder.getIncydentWSlocie(slot);
        if (incydentId == null) {
            return;
        }
        try {
            List<Zdarzenie> zdarzenia = zdarzenieRepository.znajdzIncydentu(incydentId);
            incydentRepository.znajdzPoId(incydentId).ifPresentOrElse(
                    incydent -> player.openInventory(
                            CentralaIncydentSzczegolyGui.zbuduj(centrala, incydent, zdarzenia)),
                    () -> player.sendMessage("§c[VolkerNemrodAlarm] Incydent nie znaleziony."));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu szczegółów incydentu.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    private void obslugaKlikuIncydentSzczegoly(InventoryClickEvent event, CentralaIncydentSzczegolyGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getSlot() != CentralaIncydentSzczegolyGui.SLOT_WROC) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }
        try {
            List<Incydent> incydenty = incydentRepository.znajdzOstatnie(centrala.getId(), LICZBA_INCYDENTOW_W_GUI);
            player.openInventory(CentralaIncydentyGui.zbuduj(centrala, incydenty));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu incydentów przy powrocie.", e);
            player.openInventory(CentralaGui.zbuduj(centrala));
        }
    }

    private void otworzIncydenty(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.history")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.history).");
            return;
        }
        try {
            List<Incydent> incydenty = incydentRepository.znajdzOstatnie(centrala.getId(), LICZBA_INCYDENTOW_W_GUI);
            player.openInventory(CentralaIncydentyGui.zbuduj(centrala, incydenty));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu incydentów do GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    // ===================== STREFA (Etap 1 — przebudowa, edycja na kopii) =====================

    private void obslugaKlikuStrefa(InventoryClickEvent event, CentralaStrefaGuiHolder holder) {
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Centrala centrala = rejestr.getCentrala(holder.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
            return;
        }
        // D1 (2026-09-18, Volker) — zarządzanie strefą (w tym rozmiarem) tylko właściciel.
        if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może zarządzać strefą.");
            return;
        }

        int slot = event.getSlot();
        Strefa robocza = holder.getRoboczaStrefa();

        if (slot == CentralaStrefaGui.SLOT_WROC) {
            // Kopia robocza nie była nigdzie zapisana — wystarczy wrócić do panelu, zmiany same przepadają.
            player.openInventory(CentralaGui.zbuduj(centrala));
            return;
        }
        if (slot == CentralaStrefaGui.SLOT_RESET) {
            holder.resetujDoZapisanej();
            CentralaStrefaGui.odswiez(event.getInventory(), holder);
            return;
        }
        if (slot == CentralaStrefaGui.SLOT_GRANICE) {
            pokazGraniceStrefy(player, robocza);
            player.sendMessage("§b[VolkerNemrodAlarm] Obrys strefy widoczny w świecie na ok. 10 sekund.");
            return;
        }
        if (slot == CentralaStrefaGui.SLOT_ZAPISZ) {
            try {
                strefaRepository.zapisz(robocza);
                rejestr.odswiezStrefa(robocza);
                player.openInventory(CentralaGui.zbuduj(centrala));
                player.sendMessage("§a[VolkerNemrodAlarm] Rozmiar strefy zapisany.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Błąd zapisu strefy z GUI.", e);
                player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
            }
            return;
        }

        // Klik w pole paska — ustawia promień tej osi bezpośrednio na wartość kolumny.
        int promienX = CentralaStrefaGui.promienZSlotuPaska(slot, CentralaStrefaGui.SLOT_PASEK_X_START);
        int promienY = CentralaStrefaGui.promienZSlotuPaska(slot, CentralaStrefaGui.SLOT_PASEK_Y_START);
        int promienZ = CentralaStrefaGui.promienZSlotuPaska(slot, CentralaStrefaGui.SLOT_PASEK_Z_START);

        if (promienX != -1) {
            int[] n = CentralaStrefaGui.przeliczNarozniki(robocza.getX1(), robocza.getX2(), promienX);
            robocza.setNarożniki(n[0], robocza.getY1(), robocza.getZ1(), n[1], robocza.getY2(), robocza.getZ2());
        } else if (promienY != -1) {
            int[] n = CentralaStrefaGui.przeliczNarozniki(robocza.getY1(), robocza.getY2(), promienY);
            robocza.setNarożniki(robocza.getX1(), n[0], robocza.getZ1(), robocza.getX2(), n[1], robocza.getZ2());
        } else if (promienZ != -1) {
            int[] n = CentralaStrefaGui.przeliczNarozniki(robocza.getZ1(), robocza.getZ2(), promienZ);
            robocza.setNarożniki(robocza.getX1(), robocza.getY1(), n[0], robocza.getX2(), robocza.getY2(), n[1]);
        } else {
            return; // klik poza rozpoznanym elementem (etykieta, klasa rozmiaru, podsumowanie, wolne pole)
        }

        CentralaStrefaGui.odswiez(event.getInventory(), holder);
    }

    private void otworzStrefe(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        // D1 (2026-09-18, Volker) — zarządzanie strefą tylko właściciel; zaufany ma tylko podgląd + uzbrój/rozbrój.
        if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
            player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może zarządzać strefą.");
            return;
        }

        Strefa zapisana;
        if (centrala.getStrefaId() == null) {
            // Etap 2 — tworzenie strefy z GUI: domyślna strefa 7×7×7 (promień 3) wokół bloku centrali.
            if (!centrala.maLokalizacje()) {
                player.sendMessage("§c[VolkerNemrodAlarm] Błąd — centrala nie ma zapisanej lokalizacji bloku.");
                return;
            }
            int promien = 3;
            Strefa nowa = new Strefa(UUID.randomUUID(), centrala.getId(), "Strefa-" + centrala.getNazwa(),
                    centrala.getSwiat(),
                    centrala.getX() - promien, centrala.getY() - promien, centrala.getZ() - promien,
                    centrala.getX() + promien, centrala.getY() + promien, centrala.getZ() + promien);
            try {
                strefaRepository.zapisz(nowa);
                centrala.setStrefaId(nowa.getId());
                centralaRepository.zapisz(centrala);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Błąd tworzenia domyślnej strefy z GUI.", e);
                player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
                return;
            }
            rejestr.odswiezStrefa(nowa);
            rejestr.odswiezCentrala(centrala);
            player.sendMessage("§a[VolkerNemrodAlarm] Utworzono domyślną strefę 7×7×7 — możesz ją teraz dostosować.");
            zapisana = nowa;
        } else {
            zapisana = rejestr.getStrefa(centrala.getStrefaId()).orElse(null);
            if (zapisana == null) {
                player.sendMessage("§c[VolkerNemrodAlarm] Błąd — strefa nie znaleziona w rejestrze.");
                return;
            }
        }

        // Edycja na kopii (naprawa bugu 1.0.b) — robocza kopia jest MUTOWANA kliknięciami,
        // ale to NIE jest ten sam obiekt co w CentralaRejestr, więc nic w wykrywaniu nie zmienia
        // się przed "Zapisz". Drugi snapshot (zapisanaKopia) służy do Resetu i wskaźnika zmian.
        Strefa robocza = zapisana.kopia();
        Strefa zapisanaKopia = zapisana.kopia();
        CentralaStrefaGuiHolder holder = new CentralaStrefaGuiHolder(centrala.getId(), robocza, zapisanaKopia);
        player.openInventory(CentralaStrefaGui.zbuduj(centrala, holder));
    }

    /**
     * D5 (2026-09-18, Volker) — obrys strefy cząsteczkami w świecie, widoczny ok. 10 sekund.
     * Rysuje 12 krawędzi prostopadłościanu strefy (od narożnika blokowego do narożnika+1,
     * bo blok zajmuje przestrzeń od x do x+1).
     */
    private void pokazGraniceStrefy(Player player, Strefa strefa) {
        World world = Bukkit.getWorld(strefa.getSwiat());
        if (world == null) {
            return;
        }
        List<double[]> punkty = punktyObrysu(strefa);

        new BukkitRunnable() {
            int minioneTicki = 0;

            @Override
            public void run() {
                if (minioneTicki >= 200 || !player.isOnline()) {
                    cancel();
                    return;
                }
                for (double[] p : punkty) {
                    world.spawnParticle(Particle.END_ROD, p[0], p[1], p[2], 1, 0, 0, 0, 0);
                }
                minioneTicki += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private static List<double[]> punktyObrysu(Strefa strefa) {
        double minX = strefa.getX1(), maxX = strefa.getX2() + 1.0;
        double minY = strefa.getY1(), maxY = strefa.getY2() + 1.0;
        double minZ = strefa.getZ1(), maxZ = strefa.getZ2() + 1.0;
        double krok = 0.5;

        List<double[]> punkty = new ArrayList<>();
        for (double y : new double[]{minY, maxY}) {
            for (double z : new double[]{minZ, maxZ}) {
                for (double x = minX; x <= maxX; x += krok) punkty.add(new double[]{x, y, z});
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double z : new double[]{minZ, maxZ}) {
                for (double y = minY; y <= maxY; y += krok) punkty.add(new double[]{x, y, z});
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double y : new double[]{minY, maxY}) {
                for (double z = minZ; z <= maxZ; z += krok) punkty.add(new double[]{x, y, z});
            }
        }
        return punkty;
    }

    // ===================== ZMIANA NAZWY (chat input) =====================

    private void otworzZmianaNazwy(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        // D1 (2026-09-18) — zarządzanie nazwą tylko właściciel.
        if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
            player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może zmienić nazwę.");
            return;
        }
        player.closeInventory();
        oczekujacyNaWpis.put(player.getUniqueId(), new OczekiwanyWpis(centrala.getId(), TypWpisu.NAZWA));
        player.sendMessage("§e[VolkerNemrodAlarm] Wpisz nową nazwę centrali na czacie");
        player.sendMessage("§e(lub §fcancel§e, by anulować). Aktualna nazwa: §f" + centrala.getNazwa());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID graczId = event.getPlayer().getUniqueId();
        OczekiwanyWpis oczekiwany = oczekujacyNaWpis.get(graczId);
        if (oczekiwany == null) {
            return;
        }
        event.setCancelled(true);
        oczekujacyNaWpis.remove(graczId);

        String tekst = PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
        Player player = event.getPlayer();

        if (oczekiwany.typ() == TypWpisu.NAZWA) {
            obslugaWpisuNazwy(player, oczekiwany.centralaId(), tekst);
        } else {
            obslugaWpisuZaufanego(player, oczekiwany.centralaId(), tekst);
        }
    }

    private void obslugaWpisuNazwy(Player player, UUID centralaId, String nowaLinia) {
        if (nowaLinia.equalsIgnoreCase("cancel") || nowaLinia.isEmpty()) {
            player.sendMessage("§7[VolkerNemrodAlarm] Anulowano zmianę nazwy.");
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    rejestr.getCentrala(centralaId).ifPresent(c ->
                            player.openInventory(CentralaGui.zbuduj(c))));
            return;
        }

        String ograniczona = nowaLinia.length() > 32 ? nowaLinia.substring(0, 32) : nowaLinia;
        plugin.getServer().getScheduler().runTask(plugin, () ->
            rejestr.getCentrala(centralaId).ifPresent(centrala -> {
                centrala.setNazwa(ograniczona);
                try {
                    centralaRepository.zapisz(centrala);
                    rejestr.odswiezCentrala(centrala);
                    player.sendMessage("§a[VolkerNemrodAlarm] Nazwa zmieniona na: §f" + ograniczona);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Błąd zapisu nazwy centrali z czatu.", e);
                    player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
                }
                player.openInventory(CentralaGui.zbuduj(centrala));
            })
        );
    }

    /** Etap 4 (dawny) — walidacja i dodanie zaufanego z wpisu na czacie. */
    private void obslugaWpisuZaufanego(Player player, UUID centralaId, String nick) {
        if (nick.equalsIgnoreCase("cancel") || nick.isEmpty()) {
            player.sendMessage("§7[VolkerNemrodAlarm] Anulowano dodawanie zaufanego.");
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    rejestr.getCentrala(centralaId).ifPresent(c ->
                            player.openInventory(CentralaZaufaniGui.zbuduj(c))));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Centrala centrala = rejestr.getCentrala(centralaId).orElse(null);
            if (centrala == null) {
                player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala już nie istnieje.");
                return;
            }
            // D1 (2026-09-18) — ktoś mógł stracić własność/uprawnienia w międzyczasie — sprawdź ponownie.
            if (centrala.rolaGracza(player.getUniqueId()) != Rola.WLASCICIEL) {
                player.sendMessage("§c[VolkerNemrodAlarm] Tylko właściciel może dodawać zaufanych.");
                return;
            }

            OfflinePlayer docelowy = Bukkit.getOfflinePlayer(nick);
            if (!docelowy.hasPlayedBefore() && !docelowy.isOnline()) {
                player.sendMessage("§c[VolkerNemrodAlarm] Gracz " + nick + " nigdy nie był na tym serwerze.");
                player.openInventory(CentralaZaufaniGui.zbuduj(centrala));
                return;
            }
            if (docelowy.getUniqueId().equals(centrala.getWlasciciel())) {
                player.sendMessage("§c[VolkerNemrodAlarm] Nie możesz dodać właściciela jako zaufanego.");
                player.openInventory(CentralaZaufaniGui.zbuduj(centrala));
                return;
            }
            if (centrala.getZaufani().contains(docelowy.getUniqueId())) {
                player.sendMessage("§c[VolkerNemrodAlarm] " + nick + " jest już zaufany.");
                player.openInventory(CentralaZaufaniGui.zbuduj(centrala));
                return;
            }

            centrala.dodajZaufanego(docelowy.getUniqueId());
            try {
                centralaRepository.zapisz(centrala);
                rejestr.odswiezCentrala(centrala);
                player.sendMessage("§a[VolkerNemrodAlarm] " + nick + " dodany do zaufanych.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Błąd zapisu zaufanego z czatu.", e);
                player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
            }
            player.openInventory(CentralaZaufaniGui.zbuduj(centrala));
        });
    }

    // ===================== ARM/DISARM =====================

    private void przelaczTryb(Player player, Centrala centrala) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }

        boolean obecnieUzbrojona = centrala.getTryb().isUzbrojona();
        if (!obecnieUzbrojona) {
            if (centrala.getStrefaId() == null) {
                player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala nie ma jeszcze strefy — najpierw /alarm zone.");
                return;
            }
            centrala.setTryb(TrybPracy.CZUWANIE);
        } else {
            centrala.setTryb(TrybPracy.ROZBROJONY);
        }

        try {
            centralaRepository.zapisz(centrala);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu trybu centrali z GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
            return;
        }
        rejestr.odswiezCentrala(centrala);
        player.openInventory(CentralaGui.zbuduj(centrala));
    }
}
