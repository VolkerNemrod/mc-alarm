package pl.volkernemrod.mcalarm.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.core.CentralaRejestr;
import pl.volkernemrod.mcalarm.core.IncydentManager;
import pl.volkernemrod.mcalarm.core.SelekcjaManager;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Incydent;
import pl.volkernemrod.mcalarm.model.StatusIncydentu;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handler komendy /alarm. Plan działania — Etap 2 c.d. (zone) i Etap 8
 * (reszta komend, dokładana stopniowo).
 *
 * V1 tego handlera: list, zone pos1/pos2/save, remove (ścieżka 2a — legalne
 * usunięcie, historia zostaje, w przeciwieństwie do fizycznego zniszczenia
 * obsługiwanego przez CentralaBlockListener).
 */
public class AlarmCommand implements CommandExecutor {

    private final VolkerNemrodAlarmPlugin plugin;
    private final CentralaRepository centralaRepository;
    private final StrefaRepository strefaRepository;
    private final ZdarzenieRepository zdarzenieRepository;
    private final KonfiguracjaReakcjiRepository konfiguracjaReakcjiRepository;
    private final IncydentRepository incydentRepository;
    private final IncydentManager incydentManager;
    private final SelekcjaManager selekcjaManager;
    private final CentralaRejestr rejestr;

    private static final DateTimeFormatter FORMAT_CZASU = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Proste dwustopniowe potwierdzenie /alarm admin purge: gracz -> liczba dni czekająca na potwierdzenie. */
    private final Map<UUID, Integer> oczekujacyPurge = new HashMap<>();

    public AlarmCommand(VolkerNemrodAlarmPlugin plugin, SelekcjaManager selekcjaManager, CentralaRejestr rejestr) {
        this.plugin = plugin;
        this.centralaRepository = plugin.getCentralaRepository();
        this.strefaRepository = plugin.getStrefaRepository();
        this.zdarzenieRepository = plugin.getZdarzenieRepository();
        this.konfiguracjaReakcjiRepository = plugin.getKonfiguracjaReakcjiRepository();
        this.incydentRepository = plugin.getIncydentRepository();
        this.incydentManager = plugin.getIncydentManager();
        this.selekcjaManager = selekcjaManager;
        this.rejestr = rejestr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda działa tylko w grze.");
            return true;
        }

        if (args.length == 0) {
            wyslijPomoc(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> wyslijPomoc(player);
            case "list" -> listaCentrali(player);
            case "zone" -> obslugaZone(player, args);
            case "arm" -> obslugaTryb(player, args, true);
            case "disarm" -> obslugaTryb(player, args, false);
            case "history" -> obslugaHistory(player, args);
            case "incidents" -> obslugaIncidents(player, args);
            case "trusted" -> obslugaTrusted(player, args);
            case "reaction" -> obslugaReaction(player, args);
            case "admin" -> obslugaAdmin(player, args);
            default -> player.sendMessage("§c[VolkerNemrodAlarm] Nieznana komenda. Użyj /alarm help.");
        }
        return true;
    }

    private void wyslijPomoc(Player player) {
        player.sendMessage("§6§l== VolkerNemrodAlarm ==");
        player.sendMessage("§7/alarm list §f— lista Twoich central");
        player.sendMessage("§7/alarm zone pos1 §f— ustaw 1. narożnik strefy (Twoja pozycja)");
        player.sendMessage("§7/alarm zone pos2 §f— ustaw 2. narożnik strefy");
        player.sendMessage("§7/alarm zone save [nazwa] §f— zapisz strefę dla centrali");
        player.sendMessage("§7/alarm arm [nazwa] §f— uzbrój centralę (tryb CZUWANIE)");
        player.sendMessage("§7/alarm disarm [nazwa] §f— rozbrój centralę");
        player.sendMessage("§7/alarm history [nazwa] [liczba] [gracz=<nick>] [typ=<TYP>] §f— ostatnie zdarzenia (domyślnie 10)");
        player.sendMessage("§7/alarm incidents [nazwa] [liczba] §f— ostatnie incydenty (wizyty obcych w strefie, domyślnie 10)");
        player.sendMessage("§7/alarm trusted add|remove <nick> [nazwa centrali] §f— zarządzaj zaufanymi");
        player.sendMessage("§7/alarm trusted list [nazwa centrali] §f— lista zaufanych");
        player.sendMessage("§7/alarm reaction set <typ> <on|off> [nazwa] §f— czy dany typ zdarzenia wywołuje alarm");
        player.sendMessage("§7Usunięcie centrali: rozbrój i zburz fizycznie blok (kasuje też historię tej centrali).");
        if (player.hasPermission("alarm.admin")) {
            player.sendMessage("§6§l== Admin ==");
            player.sendMessage("§7/alarm admin list §f— lista WSZYSTKICH central na serwerze");
            player.sendMessage("§7/alarm admin reload §f— przeładowanie config.yml");
            player.sendMessage("§7/alarm admin debug §f— dane diagnostyczne pluginu");
            player.sendMessage("§7/alarm admin purge [dni] §f— ręczne czyszczenie starej historii");
        }
    }

    private void listaCentrali(Player player) {
        try {
            List<Centrala> centrale = centralaRepository.znajdzWlascicielaId(player.getUniqueId());
            if (centrale.isEmpty()) {
                player.sendMessage("§7[VolkerNemrodAlarm] Nie masz żadnej centrali.");
                return;
            }
            player.sendMessage("§6[VolkerNemrodAlarm] Twoje centrale:");
            for (Centrala c : centrale) {
                String strefaInfo = c.getStrefaId() == null ? "§cbrak strefy" : "§astrefa ustawiona";
                player.sendMessage("§7 - §f" + c.getNazwa() + " §7(tryb: " + c.getTryb() + ", " + strefaInfo + "§7)");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu listy central.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    private void obslugaZone(Player player, String[] args) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm zone <pos1|pos2|save> [nazwa]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "pos1" -> {
                selekcjaManager.ustawPos1(player, player.getLocation());
                player.sendMessage("§a[VolkerNemrodAlarm] Ustawiono 1. narożnik strefy w Twojej pozycji.");
            }
            case "pos2" -> {
                selekcjaManager.ustawPos2(player, player.getLocation());
                player.sendMessage("§a[VolkerNemrodAlarm] Ustawiono 2. narożnik strefy w Twojej pozycji.");
            }
            case "save" -> zapiszStrefe(player, args.length >= 3 ? args[2] : null);
            default -> player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm zone <pos1|pos2|save> [nazwa]");
        }
    }

    private void zapiszStrefe(Player player, String filtrNazwyCentrali) {
        Location p1 = selekcjaManager.getPos1(player);
        Location p2 = selekcjaManager.getPos2(player);

        if (p1 == null || p2 == null) {
            player.sendMessage("§c[VolkerNemrodAlarm] Najpierw ustaw oba narożniki: /alarm zone pos1 i /alarm zone pos2.");
            return;
        }
        if (p1.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage("§c[VolkerNemrodAlarm] Oba narożniki muszą być w tym samym świecie.");
            return;
        }

        try {
            List<Centrala> centrale = centralaRepository.znajdzWlascicielaId(player.getUniqueId());
            if (centrale.isEmpty()) {
                player.sendMessage("§c[VolkerNemrodAlarm] Nie masz żadnej centrali — postaw ją najpierw.");
                return;
            }

            Centrala centrala;
            if (centrale.size() == 1) {
                centrala = centrale.get(0);
            } else if (filtrNazwyCentrali != null) {
                centrala = centrale.stream()
                        .filter(c -> c.getNazwa().equalsIgnoreCase(filtrNazwyCentrali))
                        .findFirst()
                        .orElse(null);
                if (centrala == null) {
                    player.sendMessage("§c[VolkerNemrodAlarm] Nie znaleziono Twojej centrali o nazwie " + filtrNazwyCentrali + ".");
                    return;
                }
            } else {
                player.sendMessage("§c[VolkerNemrodAlarm] Masz więcej niż jedną centralę — podaj nazwę: /alarm zone save <nazwa>");
                return;
            }

            Strefa strefa = zbudujStrefeZDopasowaniem(centrala, p1, p2);

            strefaRepository.zapisz(strefa);
            centrala.setStrefaId(strefa.getId());
            centralaRepository.zapisz(centrala);
            rejestr.odswiezStrefa(strefa);
            rejestr.odswiezCentrala(centrala);

            selekcjaManager.wyczysc(player);
            player.sendMessage("§a[VolkerNemrodAlarm] Strefa zapisana dla centrali " + centrala.getNazwa() + ".");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu strefy.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    /**
     * D3 (2026-09-18, Volker, potwierdzone) — ten sam limit rozmiaru co w GUI (Etap 1,
     * CentralaStrefaGui) jest teraz wymuszany również przy /alarm zone save: promień każdej
     * osi jest przycinany do [MIN_PROMIEN, MAX_PROMIEN] i dopasowywany do modelu nieparzystego
     * (2*promień+1), symetrycznie względem środka zaznaczonego przez gracza obszaru — zamiast
     * używać surowych współrzędnych pos1/pos2 wprost.
     */
    private static Strefa zbudujStrefeZDopasowaniem(Centrala centrala, Location p1, Location p2) {
        int[] x = dopasujOs(Math.min(p1.getBlockX(), p2.getBlockX()), Math.max(p1.getBlockX(), p2.getBlockX()));
        int[] y = dopasujOs(Math.min(p1.getBlockY(), p2.getBlockY()), Math.max(p1.getBlockY(), p2.getBlockY()));
        int[] z = dopasujOs(Math.min(p1.getBlockZ(), p2.getBlockZ()), Math.max(p1.getBlockZ(), p2.getBlockZ()));
        return new Strefa(UUID.randomUUID(), centrala.getId(), "Strefa-" + centrala.getNazwa(),
                p1.getWorld().getName(), x[0], y[0], z[0], x[1], y[1], z[1]);
    }

    private static final int STREFA_MIN_PROMIEN = 1; // 3x3x3 — ten sam limit co CentralaStrefaGui.MIN_PROMIEN
    private static final int STREFA_MAX_PROMIEN = 7; // 15x15x15 — ten sam limit co CentralaStrefaGui.MAX_PROMIEN

    private static int[] dopasujOs(int min, int max) {
        int promien = Math.min(STREFA_MAX_PROMIEN, Math.max(STREFA_MIN_PROMIEN, (max - min) / 2));
        int centrum = min + (max - min) / 2;
        return new int[]{centrum - promien, centrum + promien};
    }

    private void obslugaTryb(Player player, String[] args, boolean uzbroic) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        String filtrNazwyCentrali = args.length >= 2 ? args[1] : null;

        try {
            List<Centrala> centrale = centralaRepository.znajdzWlascicielaId(player.getUniqueId());
            if (centrale.isEmpty()) {
                player.sendMessage("§c[VolkerNemrodAlarm] Nie masz żadnej centrali.");
                return;
            }

            Centrala centrala;
            if (centrale.size() == 1) {
                centrala = centrale.get(0);
            } else if (filtrNazwyCentrali != null) {
                centrala = centrale.stream()
                        .filter(c -> c.getNazwa().equalsIgnoreCase(filtrNazwyCentrali))
                        .findFirst()
                        .orElse(null);
                if (centrala == null) {
                    player.sendMessage("§c[VolkerNemrodAlarm] Nie znaleziono Twojej centrali o nazwie " + filtrNazwyCentrali + ".");
                    return;
                }
            } else {
                player.sendMessage("§c[VolkerNemrodAlarm] Masz więcej niż jedną centralę — podaj nazwę: /alarm "
                        + (uzbroic ? "arm" : "disarm") + " <nazwa>");
                return;
            }

            if (uzbroic) {
                if (centrala.getStrefaId() == null) {
                    player.sendMessage("§c[VolkerNemrodAlarm] Ta centrala nie ma jeszcze strefy — najpierw /alarm zone.");
                    return;
                }
                centrala.setTryb(TrybPracy.CZUWANIE);
            } else {
                centrala.setTryb(TrybPracy.ROZBROJONY);
            }
            centralaRepository.zapisz(centrala);
            rejestr.odswiezCentrala(centrala);

            if (!uzbroic) {
                // Etap 6: ręczny disarm może przerwać aktywny ALARM — zamykamy otwarte
                // incydenty tej centrali (AlarmEngine robi to samo przy wygaśnięciu
                // automatycznym, ale nie przy ręcznej zmianie trybu). Bezpieczne przy
                // każdym disarm — IncydentManager.zamknij() jest no-op, gdy nic nie jest otwarte.
                zamknijOtwarteIncydentyCentrali(centrala.getId());
            }

            player.sendMessage(uzbroic
                    ? "§c[VolkerNemrodAlarm] Centrala " + centrala.getNazwa() + " UZBROJONA (tryb CZUWANIE)."
                    : "§a[VolkerNemrodAlarm] Centrala " + centrala.getNazwa() + " rozbrojona.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zmiany trybu centrali.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    /**
     * Zamyka wszystkie otwarte incydenty danej centrali, iterując po graczach online —
     * ten sam wzorzec co AlarmEngine#zamknijOtwarteIncydenty. Używane przy ręcznym
     * /alarm disarm, żeby incydent nie został "wiecznie otwarty" po przerwaniu alarmu.
     */
    private void zamknijOtwarteIncydentyCentrali(UUID centralaId) {
        for (Player gracz : plugin.getServer().getOnlinePlayers()) {
            incydentManager.zamknij(gracz.getUniqueId(), centralaId);
        }
    }

    private void obslugaHistory(Player player, String[] args) {
        if (!player.hasPermission("alarm.history")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.history).");
            return;
        }

        // Etap 6 c.d. — filtry: argumenty postaci gracz=<nick> / typ=<TYP> można dodać w
        // dowolnym miejscu po "history"; reszta argumentów (nazwa centrali, liczba) jest
        // parsowana jak dotąd, niezależnie od kolejności.
        List<String> pozostale = new ArrayList<>();
        UUID filtrGracza = null;
        TypZdarzenia filtrTypu = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            int rownik = arg.indexOf('=');
            if (rownik <= 0) {
                pozostale.add(arg);
                continue;
            }
            String klucz = arg.substring(0, rownik).toLowerCase();
            String wartosc = arg.substring(rownik + 1);
            if (klucz.equals("gracz")) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(wartosc);
                if (!op.hasPlayedBefore() && !op.isOnline()) {
                    player.sendMessage("§c[VolkerNemrodAlarm] Gracz " + wartosc + " nigdy nie był na tym serwerze.");
                    return;
                }
                filtrGracza = op.getUniqueId();
            } else if (klucz.equals("typ")) {
                try {
                    filtrTypu = TypZdarzenia.valueOf(wartosc.toUpperCase());
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§c[VolkerNemrodAlarm] Nieznany typ zdarzenia: " + wartosc);
                    return;
                }
            } else {
                player.sendMessage("§c[VolkerNemrodAlarm] Nieznany filtr: " + klucz + " (dostępne: gracz=, typ=)");
                return;
            }
        }

        String filtrNazwyCentrali = null;
        int limit = 10;
        if (!pozostale.isEmpty()) {
            Integer jakoLiczba = sparsujLiczbe(pozostale.get(0));
            if (jakoLiczba != null && pozostale.size() == 1) {
                limit = jakoLiczba;
            } else {
                filtrNazwyCentrali = pozostale.get(0);
                if (pozostale.size() >= 2) {
                    Integer drugaLiczba = sparsujLiczbe(pozostale.get(1));
                    if (drugaLiczba != null) {
                        limit = drugaLiczba;
                    }
                }
            }
        }
        limit = Math.max(1, Math.min(limit, 50));

        try {
            List<Centrala> centrale = centralaRepository.znajdzWlascicielaId(player.getUniqueId());
            if (centrale.isEmpty()) {
                player.sendMessage("§c[VolkerNemrodAlarm] Nie masz żadnej centrali.");
                return;
            }

            Centrala centrala;
            if (centrale.size() == 1) {
                centrala = centrale.get(0);
            } else if (filtrNazwyCentrali != null) {
                final String nazwaSzukana = filtrNazwyCentrali;
                centrala = centrale.stream()
                        .filter(c -> c.getNazwa().equalsIgnoreCase(nazwaSzukana))
                        .findFirst()
                        .orElse(null);
                if (centrala == null) {
                    player.sendMessage("§c[VolkerNemrodAlarm] Nie znaleziono Twojej centrali o nazwie " + filtrNazwyCentrali + ".");
                    return;
                }
            } else {
                player.sendMessage("§c[VolkerNemrodAlarm] Masz więcej niż jedną centralę — podaj nazwę: /alarm history <nazwa> [liczba]");
                return;
            }

            boolean maFiltry = filtrGracza != null || filtrTypu != null;
            List<Zdarzenie> zdarzenia = maFiltry
                    ? zdarzenieRepository.znajdzZFiltrami(centrala.getId(), filtrGracza, filtrTypu, limit)
                    : zdarzenieRepository.znajdzOstatnie(centrala.getId(), limit);
            if (zdarzenia.isEmpty()) {
                player.sendMessage("§7[VolkerNemrodAlarm] Brak zarejestrowanych zdarzeń dla " + centrala.getNazwa()
                        + (maFiltry ? " (z podanymi filtrami)." : "."));
                return;
            }

            player.sendMessage("§6[VolkerNemrodAlarm] Ostatnie zdarzenia (" + centrala.getNazwa() + "):");
            for (Zdarzenie z : zdarzenia) {
                String kolor = switch (z.getWaznosc()) {
                    case INFO -> "§7";
                    case WARNING -> "§e";
                    case SUSPICIOUS -> "§6";
                    case CRITICAL -> "§c";
                };
                String gracz = z.getGracz() == null ? "NIEZNANY" : nazwaGracza(z.getGracz());
                player.sendMessage(kolor + "[" + FORMAT_CZASU.format(z.getCzas()) + "] " + z.getTyp()
                        + " — " + gracz + " — " + z.getSzczegoly());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu historii.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    // ===================== INCIDENTS (Etap 6 c.d.) =====================

    private void obslugaIncidents(Player player, String[] args) {
        if (!player.hasPermission("alarm.history")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.history).");
            return;
        }

        String filtrNazwyCentrali = null;
        int limit = 10;
        if (args.length >= 2) {
            Integer jakoLiczba = sparsujLiczbe(args[1]);
            if (jakoLiczba != null && args.length == 2) {
                limit = jakoLiczba;
            } else {
                filtrNazwyCentrali = args[1];
                if (args.length >= 3) {
                    Integer drugaLiczba = sparsujLiczbe(args[2]);
                    if (drugaLiczba != null) {
                        limit = drugaLiczba;
                    }
                }
            }
        }
        limit = Math.max(1, Math.min(limit, 50));

        try {
            Centrala centrala = wybierzCentraleGracza(player, filtrNazwyCentrali, "/alarm incidents <nazwa> [liczba]");
            if (centrala == null) {
                return;
            }

            List<Incydent> incydenty = incydentRepository.znajdzOstatnie(centrala.getId(), limit);
            if (incydenty.isEmpty()) {
                player.sendMessage("§7[VolkerNemrodAlarm] Brak zarejestrowanych incydentów dla " + centrala.getNazwa() + ".");
                return;
            }

            player.sendMessage("§6[VolkerNemrodAlarm] Ostatnie incydenty (" + centrala.getNazwa() + "):");
            for (Incydent inc : incydenty) {
                String gracz = inc.getGracz() == null ? "NIEZNANY" : nazwaGracza(inc.getGracz());
                String kolor = inc.getStatus() == StatusIncydentu.OTWARTY ? "§c" : "§7";
                String koniec = inc.getKoniec() == null ? "trwa..." : FORMAT_CZASU.format(inc.getKoniec());
                player.sendMessage(kolor + "[" + FORMAT_CZASU.format(inc.getStart()) + " → " + koniec + "] "
                        + gracz + " — " + inc.getStatus());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu incydentów.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    private static Integer sparsujLiczbe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nazwaGracza(UUID uuid) {
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        String nazwa = op.getName();
        return nazwa != null ? nazwa : uuid.toString().substring(0, 8);
    }

    // ===================== TRUSTED (Etap 3) =====================

    private void obslugaTrusted(Player player, String[] args) {
        if (!player.hasPermission("alarm.trusted")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.trusted).");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm trusted <add|remove|list> ...");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> zmienZaufanego(player, args, true);
            case "remove" -> zmienZaufanego(player, args, false);
            case "list" -> listaZaufanych(player, args);
            default -> player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm trusted <add|remove|list> ...");
        }
    }

    private void zmienZaufanego(Player player, String[] args, boolean dodaj) {
        if (args.length < 3) {
            player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm trusted " + (dodaj ? "add" : "remove")
                    + " <nick> [nazwa centrali]");
            return;
        }
        String nick = args[2];
        String filtrNazwyCentrali = args.length >= 4 ? args[3] : null;

        OfflinePlayer docelowy = Bukkit.getOfflinePlayer(nick);
        if (!docelowy.hasPlayedBefore() && !docelowy.isOnline()) {
            player.sendMessage("§c[VolkerNemrodAlarm] Gracz " + nick + " nigdy nie był na tym serwerze.");
            return;
        }

        try {
            Centrala centrala = wybierzCentraleGracza(player, filtrNazwyCentrali,
                    "/alarm trusted " + (dodaj ? "add" : "remove") + " " + nick + " <nazwa>");
            if (centrala == null) {
                return; // komunikat błędu już wysłany przez wybierzCentraleGracza
            }

            if (dodaj) {
                centrala.dodajZaufanego(docelowy.getUniqueId());
            } else {
                centrala.usunZaufanego(docelowy.getUniqueId());
            }
            centralaRepository.zapisz(centrala);
            rejestr.odswiezCentrala(centrala);

            player.sendMessage(dodaj
                    ? "§a[VolkerNemrodAlarm] " + nick + " dodany do zaufanych centrali " + centrala.getNazwa() + "."
                    : "§a[VolkerNemrodAlarm] " + nick + " usunięty z zaufanych centrali " + centrala.getNazwa() + ".");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu zaufanych.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    private void listaZaufanych(Player player, String[] args) {
        String filtrNazwyCentrali = args.length >= 3 ? args[2] : null;
        try {
            Centrala centrala = wybierzCentraleGracza(player, filtrNazwyCentrali, "/alarm trusted list <nazwa>");
            if (centrala == null) {
                return;
            }
            if (centrala.getZaufani().isEmpty()) {
                player.sendMessage("§7[VolkerNemrodAlarm] Centrala " + centrala.getNazwa() + " nie ma zaufanych graczy.");
                return;
            }
            player.sendMessage("§6[VolkerNemrodAlarm] Zaufani (" + centrala.getNazwa() + "):");
            for (UUID uuid : centrala.getZaufani()) {
                player.sendMessage("§7 - §f" + nazwaGracza(uuid));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu zaufanych.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    // ===================== REACTION (Etap 5 c.d.) =====================

    private void obslugaReaction(Player player, String[] args) {
        if (!player.hasPermission("alarm.manage")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.manage).");
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("set")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm reaction set <typ> <on|off> [nazwa centrali]");
            player.sendMessage("§7Typy: PLAYER_ENTER_ZONE, PLAYER_EXIT_ZONE, BLOCK_BREAK, BLOCK_PLACE, CONTAINER_OPEN, CONTAINER_ITEM_ADD, CONTAINER_ITEM_REMOVE");
            return;
        }

        TypZdarzenia typ;
        try {
            typ = TypZdarzenia.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c[VolkerNemrodAlarm] Nieznany typ zdarzenia: " + args[2]);
            return;
        }

        Boolean wlacz = sparsujOnOff(args[3]);
        if (wlacz == null) {
            player.sendMessage("§c[VolkerNemrodAlarm] Trzeci argument musi być on/off.");
            return;
        }

        String filtrNazwyCentrali = args.length >= 5 ? args[4] : null;
        try {
            Centrala centrala = wybierzCentraleGracza(player, filtrNazwyCentrali,
                    "/alarm reaction set " + typ + " " + args[3] + " <nazwa>");
            if (centrala == null) {
                return;
            }
            konfiguracjaReakcjiRepository.ustaw(centrala.getId(), typ, wlacz);
            player.sendMessage("§a[VolkerNemrodAlarm] " + typ + " → " + (wlacz ? "WŁĄCZONE" : "WYŁĄCZONE")
                    + " (wywoływanie alarmu) dla centrali " + centrala.getNazwa() + ".");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu konfiguracji reakcji.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    private static Boolean sparsujOnOff(String s) {
        String znormalizowane = s.toLowerCase();
        if (znormalizowane.equals("on") || znormalizowane.equals("true")) {
            return true;
        }
        if (znormalizowane.equals("off") || znormalizowane.equals("false")) {
            return false;
        }
        return null;
    }

    // ===================== ADMIN (Etap 8) =====================

    private void obslugaAdmin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm admin <list|reload|debug|purge> ...");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> adminList(player);
            case "reload" -> adminReload(player);
            case "debug" -> adminDebug(player);
            case "purge" -> adminPurge(player, args);
            default -> player.sendMessage("§c[VolkerNemrodAlarm] Użycie: /alarm admin <list|reload|debug|purge> ...");
        }
    }

    /** GAMEPLAY_SPEC rozdz. 63 — lista wszystkich central niezależnie od właściciela. Bez danych graczy, chyba że config na to pozwala. */
    private void adminList(Player player) {
        if (!player.hasPermission("alarm.admin")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.admin).");
            return;
        }

        Collection<Centrala> wszystkie = rejestr.wszystkieCentrale();
        if (wszystkie.isEmpty()) {
            player.sendMessage("§7[VolkerNemrodAlarm] Brak central na serwerze.");
            return;
        }

        boolean pokazujDaneGraczy = plugin.getConfig().getBoolean("admin.pokazuj-dane-graczy", false);
        player.sendMessage("§6[VolkerNemrodAlarm] Wszystkie centrale (" + wszystkie.size() + "):");
        for (Centrala c : wszystkie) {
            String kolor = switch (c.getTryb()) {
                case ROZBROJONY -> "§7";
                case MONITORING -> "§e";
                case CZUWANIE -> "§6";
                case ALARM -> "§c";
            };
            String linia = kolor + c.getNazwa() + " §7— " + c.getTryb();
            if (pokazujDaneGraczy) {
                linia += " §7— właściciel: §f" + nazwaGracza(c.getWlasciciel());
            }
            player.sendMessage(linia);
        }
    }

    private void adminReload(Player player) {
        if (!player.hasPermission("alarm.admin.reload")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.admin.reload).");
            return;
        }
        plugin.reloadConfig();
        player.sendMessage("§a[VolkerNemrodAlarm] Konfiguracja przeładowana (config.yml).");
    }

    /** GAMEPLAY_SPEC rozdz. 79 — "administrator posiada podstawowe narzędzia diagnostyczne". */
    private void adminDebug(Player player) {
        if (!player.hasPermission("alarm.admin.debug")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.admin.debug).");
            return;
        }

        player.sendMessage("§6[VolkerNemrodAlarm] Diagnostyka:");
        player.sendMessage("§7Wersja pluginu: §f" + plugin.getDescription().getVersion());
        player.sendMessage("§7Centrale w rejestrze (pamięć): §f" + rejestr.wszystkieCentrale().size());

        boolean polaczenieOk;
        try {
            polaczenieOk = plugin.getDatabaseManager().getConnection() != null
                    && !plugin.getDatabaseManager().getConnection().isClosed();
        } catch (SQLException e) {
            polaczenieOk = false;
        }
        player.sendMessage("§7Połączenie z bazą SQLite: " + (polaczenieOk ? "§aOK" : "§cBŁĄD"));

        try {
            player.sendMessage("§7Otwarte incydenty (wszystkie centrale): §f" + incydentRepository.liczOtwarte());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu liczby otwartych incydentów (/alarm admin debug).", e);
            player.sendMessage("§cBłąd odczytu liczby otwartych incydentów — sprawdź logi serwera.");
        }
    }

    /**
     * Ręczne czyszczenie starej historii (zdarzenia + ZAMKNIĘTE incydenty), niezależnie
     * od automatycznej retencji z Etapu 9. Nieodwracalne kasowanie danych — wymaga
     * dwustopniowego potwierdzenia, tak jak /alarm remove.
     */
    private void adminPurge(Player player, String[] args) {
        if (!player.hasPermission("alarm.admin.purge")) {
            player.sendMessage("§c[VolkerNemrodAlarm] Brak uprawnień (alarm.admin.purge).");
            return;
        }

        int dni;
        if (args.length >= 3) {
            Integer podane = sparsujLiczbe(args[2]);
            if (podane == null || podane < 1) {
                player.sendMessage("§c[VolkerNemrodAlarm] Liczba dni musi być liczbą całkowitą ≥ 1.");
                return;
            }
            dni = podane;
        } else {
            int retencja = plugin.getConfig().getInt("historia.retencja-dni", 90);
            if (retencja <= 0) {
                player.sendMessage("§c[VolkerNemrodAlarm] Retencja wyłączona (historia.retencja-dni=0) — podaj liczbę dni jawnie: /alarm admin purge <dni>.");
                return;
            }
            dni = retencja;
        }

        Integer oczekujace = oczekujacyPurge.get(player.getUniqueId());
        if (oczekujace != null && oczekujace == dni) {
            oczekujacyPurge.remove(player.getUniqueId());
            try {
                Instant granica = Instant.now().minus(dni, ChronoUnit.DAYS);
                int usunieteZdarzenia = zdarzenieRepository.usunStarsze(granica);
                int usunieteIncydenty = incydentRepository.usunStarsze(granica);
                player.sendMessage("§a[VolkerNemrodAlarm] Wyczyszczono historię starszą niż " + dni + " dni: "
                        + usunieteZdarzenia + " zdarzeń, " + usunieteIncydenty + " zamkniętych incydentów.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Błąd czyszczenia historii (/alarm admin purge).", e);
                player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
            }
        } else {
            oczekujacyPurge.put(player.getUniqueId(), dni);
            player.sendMessage("§e[VolkerNemrodAlarm] Na pewno chcesz TRWALE usunąć zamkniętą historię starszą niż "
                    + dni + " dni? Wpisz ponownie /alarm admin purge " + dni + ", żeby potwierdzić.");
        }
    }

    /**
     * Wspólny wybór centrali gracza używany przez funkcje trusted (jedna centrala
     * gracza = automatycznie, kilka = wymaga podania nazwy). Sam wysyła komunikat
     * błędu i zwraca null, jeśli nie można jednoznacznie wybrać centrali.
     */
    private Centrala wybierzCentraleGracza(Player player, String filtrNazwy, String przykladKomendy) throws SQLException {
        List<Centrala> centrale = centralaRepository.znajdzWlascicielaId(player.getUniqueId());
        if (centrale.isEmpty()) {
            player.sendMessage("§c[VolkerNemrodAlarm] Nie masz żadnej centrali.");
            return null;
        }
        if (centrale.size() == 1) {
            return centrale.get(0);
        }
        if (filtrNazwy == null) {
            player.sendMessage("§c[VolkerNemrodAlarm] Masz więcej niż jedną centralę — podaj nazwę: " + przykladKomendy);
            return null;
        }
        final String szukana = filtrNazwy;
        Centrala znaleziona = centrale.stream()
                .filter(c -> c.getNazwa().equalsIgnoreCase(szukana))
                .findFirst()
                .orElse(null);
        if (znaleziona == null) {
            player.sendMessage("§c[VolkerNemrodAlarm] Nie znaleziono Twojej centrali o nazwie " + filtrNazwy + ".");
        }
        return znaleziona;
    }
}
