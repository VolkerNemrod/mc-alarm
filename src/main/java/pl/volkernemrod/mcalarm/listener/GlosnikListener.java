package pl.volkernemrod.mcalarm.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;
import pl.volkernemrod.mcalarm.core.GlosnikItemFactory;
import pl.volkernemrod.mcalarm.core.GlosnikRejestr;
import pl.volkernemrod.mcalarm.gui.GlosnikGui;
import pl.volkernemrod.mcalarm.gui.GlosnikGuiHolder;
import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.Glosnik;
import pl.volkernemrod.mcalarm.model.Rola;
import pl.volkernemrod.mcalarm.core.CentralaRejestr;
import pl.volkernemrod.mcalarm.storage.GlosnikRepository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Obsługuje cykl życia głośnika: postawienie, zniszczenie, otwarcie GUI
 * i kliknięcia wewnątrz ekranu konfiguracji. Plan działania — punkt B.
 */
public class GlosnikListener implements Listener {

    private final VolkerNemrodAlarmPlugin plugin;
    private final GlosnikItemFactory itemFactory;
    private final GlosnikRejestr glosnikRejestr;
    private final CentralaRejestr centralaRejestr;
    private final GlosnikRepository glosnikRepository;

    public GlosnikListener(VolkerNemrodAlarmPlugin plugin, GlosnikItemFactory itemFactory,
                           GlosnikRejestr glosnikRejestr, CentralaRejestr centralaRejestr,
                           GlosnikRepository glosnikRepository) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.glosnikRejestr = glosnikRejestr;
        this.centralaRejestr = centralaRejestr;
        this.glosnikRepository = glosnikRepository;
    }

    // ===================== POSTAWIENIE =====================

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!itemFactory.jestGlosnikItem(event.getItemInHand())) {
            return;
        }

        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        String swiat = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();

        // Szukamy centrali której strefa zawiera tę lokalizację.
        Optional<Centrala> centralaOpt = centralaRejestr.wszystkieCentrale().stream()
                .filter(c -> {
                    if (c.getStrefaId() == null) return false;
                    return centralaRejestr.getStrefa(c.getStrefaId())
                            .map(s -> s.zawiera(swiat, x, y, z))
                            .orElse(false);
                })
                .findFirst();

        if (centralaOpt.isEmpty()) {
            player.sendMessage("§c[VolkerNemrodAlarm] Głośnik musi być postawiony w strefie alarmowej.");
            event.setCancelled(true);
            return;
        }

        Centrala centrala = centralaOpt.get();
        Rola rola = centrala.rolaGracza(player.getUniqueId());
        if (rola == Rola.OBCY) {
            player.sendMessage("§c[VolkerNemrodAlarm] Nie możesz postawić głośnika w cudzej strefie.");
            event.setCancelled(true);
            return;
        }

        Glosnik glosnik = new Glosnik(UUID.randomUUID(), centrala.getId(),
                swiat, x, y, z, true, true);
        try {
            glosnikRepository.zapisz(glosnik);
            glosnikRejestr.dodaj(glosnik);
            player.sendMessage("§a[VolkerNemrodAlarm] Głośnik zarejestrowany w strefie §f" + centrala.getNazwa() + "§a.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu głośnika do bazy.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }

    // ===================== ZNISZCZENIE =====================

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String swiat = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();

        Glosnik glosnik = glosnikRejestr.znajdzPoLokalizacji(swiat, x, y, z).orElse(null);
        if (glosnik == null) {
            return; // zwykły Note Block
        }

        try {
            glosnikRepository.usunPoLokalizacji(swiat, x, y, z);
            glosnikRejestr.usunPoLokalizacji(swiat, x, y, z);
            event.getPlayer().sendMessage("§7[VolkerNemrodAlarm] Głośnik usunięty z rejestru.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd usuwania głośnika z bazy.", e);
        }
    }

    // ===================== OTWARCIE GUI (prawy klik) =====================

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        String swiat = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();

        Glosnik glosnik = glosnikRejestr.znajdzPoLokalizacji(swiat, x, y, z).orElse(null);
        if (glosnik == null) {
            return; // zwykły Note Block
        }

        event.setCancelled(true); // nie graj dźwięku NOTE_BLOCK

        Player player = event.getPlayer();
        Centrala centrala = centralaRejestr.getCentrala(glosnik.getCentralaId()).orElse(null);
        if (centrala == null) {
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd — centrala głośnika nie znaleziona.");
            return;
        }

        Rola rola = centrala.rolaGracza(player.getUniqueId());
        if (rola == Rola.OBCY) {
            player.sendMessage("§c[VolkerNemrodAlarm] To nie jest twój głośnik.");
            return;
        }

        player.openInventory(GlosnikGui.zbuduj(glosnik));
    }

    // ===================== KLIKNIĘCIA W GUI =====================

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GlosnikGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Glosnik glosnik = glosnikRejestr.znajdzPoId(holder.getGlosnikId()).orElse(null);
        if (glosnik == null) {
            player.closeInventory();
            player.sendMessage("§c[VolkerNemrodAlarm] Ten głośnik już nie istnieje.");
            return;
        }

        int slot = event.getSlot();
        if (slot == GlosnikGui.SLOT_ZAMKNIJ) {
            player.closeInventory();
            return;
        }
        if (slot == GlosnikGui.SLOT_DZWIEK) {
            glosnik.setDzwiekWlaczony(!glosnik.isDzwiekWlaczony());
        } else if (slot == GlosnikGui.SLOT_EFEKTY) {
            glosnik.setEfektyWlaczone(!glosnik.isEfektyWlaczone());
        } else {
            return;
        }

        try {
            glosnikRepository.zapisz(glosnik);
            // Odśwież tylko zmieniony przycisk — nie otwieraj na nowo całego ekranu.
            event.getInventory().setItem(GlosnikGui.SLOT_DZWIEK, GlosnikGui.itemDzwiek(glosnik.isDzwiekWlaczony()));
            event.getInventory().setItem(GlosnikGui.SLOT_EFEKTY, GlosnikGui.itemEfekty(glosnik.isEfektyWlaczone()));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Błąd zapisu konfiguracji głośnika z GUI.", e);
            player.sendMessage("§c[VolkerNemrodAlarm] Błąd bazy danych — sprawdź logi serwera.");
        }
    }
}
