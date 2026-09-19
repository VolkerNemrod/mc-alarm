package pl.volkernemrod.mcalarm.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.core.CentralaItemFactory;
import pl.volkernemrod.mcalarm.core.CentralaRejestr;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Strefa;
import pl.volkernemrod.mcalarm.storage.CentralaRepository;
import pl.volkernemrod.mcalarm.storage.StrefaRepository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener bloku centrali: postawienie (tworzy nową centralę) oraz fizyczne
 * zniszczenie (dwie ścieżki — patrz Etap 2 planu działania w mem-palace):
 *
 *  - centrala ROZBROJONA → zniszczenie bezpieczne, ale wciąż liczy się jako
 *    ścieżka (b): twardy reset historii tej centrali (CentralaRepository#zniszczFizycznie).
 *  - centrala UZBROJONA (CZUWANIE/ALARM) → faza pulsowania (jak creeper),
 *    potem eksplozja niszcząca bloki. Dotyczy KAŻDEGO, w tym właściciela —
 *    decyzja Volkera z 2026-09-12 (mem-palace, wing mc-alarm, room decisions).
 *
 * Blok centrali w V1 to placeholder: {@link Material#LECTERN}.
 */
public class CentralaBlockListener implements Listener {

    /** Placeholder na blok centrali w V1 — do ew. zmiany, patrz plan działania Etap 0 pkt 4. */
    public static final Material BLOK_CENTRALI = Material.LECTERN;

    private final VolkerNemrodAlarmPlugin plugin;
    private final CentralaRepository centralaRepository;
    private final StrefaRepository strefaRepository;
    private final CentralaItemFactory itemFactory;
    private final CentralaRejestr rejestr;

    public CentralaBlockListener(VolkerNemrodAlarmPlugin plugin, CentralaItemFactory itemFactory, CentralaRejestr rejestr) {
        this.plugin = plugin;
        this.centralaRepository = plugin.getCentralaRepository();
        this.strefaRepository = plugin.getStrefaRepository();
        this.itemFactory = itemFactory;
        this.rejestr = rejestr;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != BLOK_CENTRALI) {
            return;
        }

        Player player = event.getPlayer();

        // Tylko przedmiot z naszym znacznikiem tworzy centralę — zwykły
        // Lectern zostaje zwykłym Lecternem (patrz CentralaItemFactory).
        if (!itemFactory.jestCentralaItem(event.getItemInHand())) {
            return;
        }

        if (!player.hasPermission("alarm.create")) {
            event.setCancelled(true);
            player.sendMessage("§c[VolkerNemrodAlarm] Nie masz uprawnień do tworzenia centrali.");
            return;
        }

        Block block = event.getBlock();

        Centrala centrala = new Centrala(UUID.randomUUID(), player.getUniqueId(),
                "Centrala-" + player.getName());
        centrala.setLokalizacja(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());

        // Volker (2026-09-18): strefa powstaje od razu przy postawieniu centrali (upraszcza
        // stawianie alarmu) — domyślna 7×7×7 (promień 3), do skorygowania w ekranie Strefa.
        // KOLEJNOŚĆ WAŻNA: centrala MUSI być zapisana w bazie PRZED strefą, bo strefy.centrala_id
        // ma FOREIGN KEY ON DELETE CASCADE na centrale.id (PRAGMA foreign_keys=ON) — wstawienie
        // strefy odwołującej się do jeszcze nieistniejącej centrali kończy się SQLITE_CONSTRAINT_FOREIGNKEY.
        Strefa strefa = new Strefa(UUID.randomUUID(), centrala.getId(), "Strefa-" + centrala.getNazwa(),
                block.getWorld().getName(),
                block.getX() - 3, block.getY() - 3, block.getZ() - 3,
                block.getX() + 3, block.getY() + 3, block.getZ() + 3);

        try {
            centralaRepository.zapisz(centrala);
            strefaRepository.zapisz(strefa);
            centrala.setStrefaId(strefa.getId());
            centralaRepository.zapisz(centrala);
            rejestr.odswiezStrefa(strefa);
            rejestr.odswiezCentrala(centrala);
            player.sendMessage("§a[VolkerNemrodAlarm] Centrala utworzona ze strefą 7×7×7. Użyj /alarm, żeby ją skonfigurować.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Nie udało się zapisać nowej centrali do bazy.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd zapisu centrali do bazy — sprawdź logi serwera.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != BLOK_CENTRALI) {
            return;
        }

        Optional<Centrala> znaleziona;
        try {
            znaleziona = centralaRepository.znajdzPoLokalizacji(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd odczytu centrali przy próbie zniszczenia bloku.", e);
            return;
        }

        if (znaleziona.isEmpty()) {
            // to zwykły Lectern, nie centrala — nie ingerujemy
            return;
        }

        Centrala centrala = znaleziona.get();
        Player player = event.getPlayer();

        if (!centrala.getTryb().isUzbrojona()) {
            // Rozbrojona — bezpieczne zniszczenie, ale to wciąż ścieżka (b): twardy reset historii.
            try {
                centralaRepository.zniszczFizycznie(centrala.getId());
                rejestr.usunCentrala(centrala.getId());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Błąd kasowania centrali po fizycznym zniszczeniu.", e);
            }
            player.sendMessage("§e[VolkerNemrodAlarm] Centrala zniszczona (była rozbrojona) — historia skasowana.");
            return;
        }

        // Uzbrojona — dotyczy TAKŻE właściciela. Blok znika dopiero po wybuchu.
        event.setCancelled(true);
        rozpocznijPulsowanie(centrala, block, player);
    }

    private void rozpocznijPulsowanie(Centrala centrala, Block block, Player wywolujacy) {
        FileConfiguration cfg = plugin.getConfig();
        int czasPulsowaniaSekund = cfg.getInt("centrala.zniszczenie-fizyczne.czas-pulsowania-sekundy", 3);
        float promienEksplozji = (float) cfg.getInt("centrala.zniszczenie-fizyczne.promien-eksplozji", 3);
        boolean niszczyBloki = cfg.getBoolean("centrala.zniszczenie-fizyczne.niszczy-bloki", true);

        World world = block.getWorld();
        Location srodek = block.getLocation().add(0.5, 0.5, 0.5);
        int totalTicks = Math.max(1, czasPulsowaniaSekund * 20);

        wywolujacy.sendMessage("§c§l[VolkerNemrodAlarm] UWAGA — centrala UZBROJONA! Wybuch za "
                + czasPulsowaniaSekund + " s!");
        world.playSound(srodek, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);

        new BukkitRunnable() {
            int minioneTicki = 0;

            @Override
            public void run() {
                // Ktoś/coś usunęło blok w międzyczasie (np. inny plugin) — przerywamy.
                if (block.getType() != BLOK_CENTRALI) {
                    cancel();
                    return;
                }

                minioneTicki += 5;
                float postep = Math.min(1.0f, minioneTicki / (float) totalTicks);
                world.playSound(srodek, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f + postep);
                world.spawnParticle(Particle.SMOKE, srodek, 6, 0.2, 0.2, 0.2, 0.01);

                if (minioneTicki >= totalTicks) {
                    cancel();
                    wybuchnij(centrala, block, world, srodek, promienEksplozji, niszczyBloki);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void wybuchnij(Centrala centrala, Block block, World world, Location srodek,
                            float promienEksplozji, boolean niszczyBloki) {
        block.setType(Material.AIR);
        world.createExplosion(srodek.getX(), srodek.getY(), srodek.getZ(), promienEksplozji, false, niszczyBloki);

        try {
            // Ścieżka (b): fizyczne zniszczenie uzbrojonej centrali — twardy reset historii.
            centralaRepository.zniszczFizycznie(centrala.getId());
            rejestr.usunCentrala(centrala.getId());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd kasowania centrali po wybuchu.", e);
        }
    }
}
