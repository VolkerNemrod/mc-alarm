package pl.volkernemrod.mcalarm;

import org.bukkit.plugin.java.JavaPlugin;
import pl.volkernemrod.mcalarm.command.AlarmCommand;
import pl.volkernemrod.mcalarm.core.AlarmEngine;
import pl.volkernemrod.mcalarm.core.CentralaItemFactory;
import pl.volkernemrod.mcalarm.core.CentralaRejestr;
import pl.volkernemrod.mcalarm.core.GlosnikItemFactory;
import pl.volkernemrod.mcalarm.core.GlosnikRejestr;
import pl.volkernemrod.mcalarm.core.IncydentManager;
import pl.volkernemrod.mcalarm.core.SelekcjaManager;
import pl.volkernemrod.mcalarm.listener.CentralaBlockListener;
import pl.volkernemrod.mcalarm.listener.CentralaGuiListener;
import pl.volkernemrod.mcalarm.listener.GlosnikListener;
import pl.volkernemrod.mcalarm.listener.StrefaZdarzeniaListener;
import pl.volkernemrod.mcalarm.storage.CentralaRepository;
import pl.volkernemrod.mcalarm.storage.DatabaseManager;
import pl.volkernemrod.mcalarm.storage.GlosnikRepository;
import pl.volkernemrod.mcalarm.storage.IncydentRepository;
import pl.volkernemrod.mcalarm.storage.KonfiguracjaReakcjiRepository;
import pl.volkernemrod.mcalarm.storage.StrefaRepository;
import pl.volkernemrod.mcalarm.storage.ZdarzenieRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;

/**
 * Główna klasa pluginu VolkerNemrodAlarm.
 *
 * System alarmowy, monitoring i "czarna skrzynka" dla graczy Minecraft (Paper).
 * Zobacz README.md i GAMEPLAY_SPEC.md w katalogu głównym projektu — są
 * nadrzędnym źródłem prawdy dla mechaniki. Plan implementacji: mem-palace,
 * wing "mc-alarm", room "plan-dzialania".
 *
 * Stan na Etap 9 c.d.: model domenowy + SQLite (Etap 1), blok centrali + wybuch
 * + strefa + komendy podstawowe (Etap 2), zaufani + arm/disarm (Etap 3),
 * silnik zdarzeń (Etap 4), reakcje alarmowe: syrena/lampa/auto-alarm (Etap 5),
 * historia + grupowanie w incydenty (Etap 6), GUI panel + historia + podekrany
 * zaufanych, ustawień reakcji i incydentów (Etapy 7–9).
 */
public class VolkerNemrodAlarmPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private CentralaRepository centralaRepository;
    private StrefaRepository strefaRepository;
    private ZdarzenieRepository zdarzenieRepository;
    private KonfiguracjaReakcjiRepository konfiguracjaReakcjiRepository;
    private IncydentRepository incydentRepository;
    private GlosnikRepository glosnikRepository;
    private IncydentManager incydentManager;
    private CentralaRejestr rejestr;
    private GlosnikRejestr glosnikRejestr;
    private int retencjaTaskId = -1;

    @Override
    public void onEnable() {
        getLogger().info("VolkerNemrodAlarm — start.");

        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.start()) {
            getLogger().severe("Nie udało się zainicjalizować bazy danych — wyłączam plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        centralaRepository = new CentralaRepository(databaseManager);
        strefaRepository = new StrefaRepository(databaseManager);
        zdarzenieRepository = new ZdarzenieRepository(databaseManager);
        konfiguracjaReakcjiRepository = new KonfiguracjaReakcjiRepository(databaseManager);
        incydentRepository = new IncydentRepository(databaseManager);
        glosnikRepository = new GlosnikRepository(databaseManager);

        rejestr = new CentralaRejestr();
        try {
            rejestr.wczytajZBazy(centralaRepository, strefaRepository);
            getLogger().info("Wczytano centrale i strefy do pamięci.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Błąd wczytywania central/stref z bazy do rejestru.", e);
        }

        glosnikRejestr = new GlosnikRejestr();
        try {
            glosnikRejestr.wczytaj(glosnikRepository.wszystkie());
            getLogger().info("Wczytano głośniki do pamięci.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Błąd wczytywania głośników z bazy.", e);
        }

        CentralaItemFactory itemFactory = new CentralaItemFactory(this);
        itemFactory.zarejestrujPrzepis();

        GlosnikItemFactory glosnikItemFactory = new GlosnikItemFactory(this);
        glosnikItemFactory.zarejestrujPrzepis();

        incydentManager = new IncydentManager(this, incydentRepository);
        AlarmEngine alarmEngine = new AlarmEngine(this, centralaRepository, rejestr, incydentManager);

        getServer().getPluginManager().registerEvents(new CentralaBlockListener(this, itemFactory, rejestr), this);
        getServer().getPluginManager().registerEvents(
                new StrefaZdarzeniaListener(this, rejestr, zdarzenieRepository, konfiguracjaReakcjiRepository,
                        alarmEngine, incydentManager), this);
        getServer().getPluginManager().registerEvents(
                new CentralaGuiListener(this, rejestr, centralaRepository, zdarzenieRepository,
                        konfiguracjaReakcjiRepository, incydentRepository), this);
        getServer().getPluginManager().registerEvents(
                new GlosnikListener(this, glosnikItemFactory, glosnikRejestr, rejestr, glosnikRepository), this);

        SelekcjaManager selekcjaManager = new SelekcjaManager();
        getCommand("alarm").setExecutor(new AlarmCommand(this, selekcjaManager, rejestr));

        // Etap 9.1 — scheduler retencji historii (GAMEPLAY_SPEC rozdz. 64).
        // Uruchamiany tylko gdy historia.retencja-dni > 0 (0 = bez limitu).
        int retencjaDni = getConfig().getInt("historia.retencja-dni", 90);
        if (retencjaDni > 0) {
            // Pierwsze wykonanie po 1 godzinie od startu, potem co 24 godziny.
            // 20 ticków = 1 sekunda; 20*60*60 = 72 000 ticków = 1 godzina.
            long tickGodzina = 20L * 60 * 60;
            long tickDoba = tickGodzina * 24;
            retencjaTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                Instant granica = Instant.now().minus(retencjaDni, ChronoUnit.DAYS);
                try {
                    int z = zdarzenieRepository.usunStarsze(granica);
                    int i = incydentRepository.usunStarsze(granica);
                    if (z > 0 || i > 0) {
                        getLogger().info("[Retencja] Wyczyszczono " + z + " zdarzeń i " + i
                                + " zamkniętych incydentów starszych niż " + retencjaDni + " dni.");
                    }
                } catch (SQLException e) {
                    getLogger().log(Level.WARNING, "[Retencja] Błąd czyszczenia starej historii.", e);
                }
            }, tickGodzina, tickDoba).getTaskId();
            getLogger().info("[Retencja] Scheduler aktywny — retencja: " + retencjaDni + " dni.");
        } else {
            getLogger().info("[Retencja] Wyłączona (historia.retencja-dni=0).");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("VolkerNemrodAlarm — zatrzymano.");

        if (retencjaTaskId != -1) {
            getServer().getScheduler().cancelTask(retencjaTaskId);
        }

        if (databaseManager != null) {
            databaseManager.stop();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CentralaRepository getCentralaRepository() {
        return centralaRepository;
    }

    public StrefaRepository getStrefaRepository() {
        return strefaRepository;
    }

    public ZdarzenieRepository getZdarzenieRepository() {
        return zdarzenieRepository;
    }

    public KonfiguracjaReakcjiRepository getKonfiguracjaReakcjiRepository() {
        return konfiguracjaReakcjiRepository;
    }

    public IncydentRepository getIncydentRepository() {
        return incydentRepository;
    }

    public GlosnikRepository getGlosnikRepository() {
        return glosnikRepository;
    }

    public GlosnikRejestr getGlosnikRejestr() {
        return glosnikRejestr;
    }

    public IncydentManager getIncydentManager() {
        return incydentManager;
    }

    public CentralaRejestr getRejestr() {
        return rejestr;
    }
}
