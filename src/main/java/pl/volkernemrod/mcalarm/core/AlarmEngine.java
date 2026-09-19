package pl.volkernemrod.mcalarm.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Glosnik;
import pl.volkernemrod.mcalarm.model.Strefa;
import pl.volkernemrod.mcalarm.model.TrybPracy;
import pl.volkernemrod.mcalarm.storage.CentralaRepository;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Silnik alarmu — Etap 5. Wywoływany przez StrefaZdarzeniaListener, gdy
 * zdarzenie kwalifikuje się do wywołania alarmu: obcy, centrala w trybie
 * CZUWANIE, typ zdarzenia skonfigurowany jako wywołujący alarm (patrz
 * KonfiguracjaReakcjiRepository).
 *
 * Sekwencja: CZUWANIE→ALARM, syrena+lampa pulsują co sekundę przez czas
 * z config.yml (alarm.domyslny-czas-trwania-sekundy), potem automatyczny
 * powrót do CZUWANIE (NIE do ROZBROJONY — alarm się wycisza, system nadal
 * czuwa). Ręczne /alarm disarm w trakcie alarmu przerywa pętlę (sprawdzane
 * co sekundę: jeśli tryb przestał być ALARM, pętla się zatrzymuje).
 *
 * Etap 6: przy zakończeniu alarmu (zarówno auto jak i przez disarm) zamykane
 * są wszystkie otwarte incydenty graczy powiązanych z centralą (gracz mógł
 * nie wyjść ze strefy, a alarm wygasł lub właściciel go rozbroił).
 */
public class AlarmEngine {

    private final VolkerNemrodAlarmPlugin plugin;
    private final CentralaRepository centralaRepository;
    private final CentralaRejestr rejestr;
    private final IncydentManager incydentManager;
    private final GlosnikRejestr glosnikRejestr;

    public AlarmEngine(VolkerNemrodAlarmPlugin plugin, CentralaRepository centralaRepository,
                       CentralaRejestr rejestr, IncydentManager incydentManager) {
        this.plugin = plugin;
        this.centralaRepository = centralaRepository;
        this.rejestr = rejestr;
        this.incydentManager = incydentManager;
        this.glosnikRejestr = plugin.getGlosnikRejestr();
    }

    public void wywolajAlarm(Centrala centrala, Strefa strefa) {
        if (centrala.getTryb() != TrybPracy.CZUWANIE) {
            return; // już ALARM, albo ktoś zdążył zmienić tryb w międzyczasie — nic nie rób
        }
        if (!centrala.maLokalizacje()) {
            return;
        }
        World world = Bukkit.getWorld(centrala.getSwiat());
        if (world == null) {
            return;
        }

        centrala.setTryb(TrybPracy.ALARM);
        try {
            centralaRepository.zapisz(centrala);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu trybu ALARM.", e);
        }
        rejestr.odswiezCentrala(centrala);

        powiadomWlasciciela(centrala, strefa);

        Location loc = new Location(world, centrala.getX() + 0.5, centrala.getY() + 0.5, centrala.getZ() + 0.5);
        Syrena syrena = new Syrena(loc);
        Lampa lampa = new Lampa(loc);

        int czasTrwaniaSekund = plugin.getConfig().getInt("alarm.domyslny-czas-trwania-sekundy", 60);
        int maksTickow = Math.max(20, czasTrwaniaSekund * 20);
        UUID centralaId = centrala.getId();

        new BukkitRunnable() {
            int minioneTicki = 0;

            @Override
            public void run() {
                // Ktoś zdążył ręcznie zmienić tryb w międzyczasie (np. /alarm disarm) — przerywamy.
                Centrala aktualna = rejestr.getCentrala(centralaId).orElse(null);
                if (aktualna == null || aktualna.getTryb() != TrybPracy.ALARM) {
                    cancel();
                    return;
                }

                syrena.aktywuj();
                lampa.aktywuj();
                aktywujGlosniki(centralaId, world);

                minioneTicki += 20;
                if (minioneTicki >= maksTickow) {
                    cancel();
                    zakonczAlarm(centralaId);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Etap B.2 — głośniki podczas alarmu.
     * Każdy głośnik tej centrali z włączonym dzwiękiem gra BELL, z włączonymi efektami — cząsteczki FLAME.
     * Wywoływane co sekundę przez pulę alarmową.
     */
    private void aktywujGlosniki(UUID centralaId, World world) {
        for (Glosnik g : glosnikRejestr.wszystkie()) {
            if (!g.getCentralaId().equals(centralaId)) {
                continue;
            }
            World gSwiat = Bukkit.getWorld(g.getSwiat());
            if (gSwiat == null || !gSwiat.equals(world)) {
                continue;
            }
            Location gLoc = new Location(gSwiat, g.getX() + 0.5, g.getY() + 0.5, g.getZ() + 0.5);
            if (g.isDzwiekWlaczony()) {
                gSwiat.playSound(gLoc, Sound.BLOCK_BELL_USE, 2.0f, 0.8f);
            }
            if (g.isEfektyWlaczone()) {
                gSwiat.spawnParticle(Particle.FLAME, gLoc, 12, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private void zakonczAlarm(UUID centralaId) {
        Centrala aktualna = rejestr.getCentrala(centralaId).orElse(null);
        if (aktualna == null || aktualna.getTryb() != TrybPracy.ALARM) {
            return; // ktoś już to zmienił ręcznie — nie nadpisujemy
        }
        aktualna.setTryb(TrybPracy.CZUWANIE); // wraca do czuwania, NIE do rozbrojenia
        try {
            centralaRepository.zapisz(aktualna);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu końca alarmu.", e);
        }
        rejestr.odswiezCentrala(aktualna);
        zamknijOtwarteIncydenty(centralaId);
        powiadomKoniecAlarmu(aktualna);
    }

    /**
     * Zamyka wszystkie otwarte incydenty powiązane z centralą — wywoływane
     * przy automatycznym wygaśnięciu alarmu. Gracz mógł pozostać w strefie,
     * dlatego to uzupełnienie wobec zamknięcia przy PLAYER_EXIT_ZONE.
     */
    private void zamknijOtwarteIncydenty(UUID centralaId) {
        // Iterujemy po wszystkich graczach online i zamykamy ich otwarte incydenty
        // dla tej centrali. Nie mamy listy otwartych incydentów per centrala —
        // IncydentManager.zamknij() jest bezpiecznym "próbuj zamknąć" (robi nic gdy brak).
        for (org.bukkit.entity.Player gracz : plugin.getServer().getOnlinePlayers()) {
            incydentManager.zamknij(gracz.getUniqueId(), centralaId);
        }
    }

    private void powiadomWlasciciela(Centrala centrala, Strefa strefa) {
        Player wlasciciel = Bukkit.getPlayer(centrala.getWlasciciel());
        if (wlasciciel != null) {
            String nazwaStrefy = strefa != null ? strefa.getNazwa() : "?";
            wlasciciel.sendMessage("§c§l[VolkerNemrodAlarm] ALARM! §r§cCoś się dzieje w strefie \""
                    + nazwaStrefy + "\" centrali " + centrala.getNazwa() + "!");
        }
    }

    private void powiadomKoniecAlarmu(Centrala centrala) {
        Player wlasciciel = Bukkit.getPlayer(centrala.getWlasciciel());
        if (wlasciciel != null) {
            wlasciciel.sendMessage("§e[VolkerNemrodAlarm] Alarm centrali " + centrala.getNazwa()
                    + " wyciszył się automatycznie (nadal CZUWANIE).");
        }
    }
}
