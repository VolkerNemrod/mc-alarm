package pl.volkernemrod.mcalarm.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.volkernemrod.mcalarm.VolkerNemrodAlarmPlugin;

import java.util.List;

/**
 * Odpowiada za przedmiot "Centrala VolkerNemrodAlarm" — blok Lectern
 * oznaczony niestandardowym znacznikiem (PersistentDataContainer) i
 * craftowany inną recepturą niż zwykły Lectern, żeby się nie myliły
 * (decyzja Volkera, 2026-09-12 — mem-palace, wing mc-alarm, room decisions).
 *
 * Tylko przedmiot z tym znacznikiem, postawiony w świecie, tworzy centralę
 * (patrz CentralaBlockListener#onBlockPlace). Zwykły Lectern postawiony
 * normalnie zostaje zwykłym Lecternem.
 */
public class CentralaItemFactory {

    private final VolkerNemrodAlarmPlugin plugin;
    private final NamespacedKey znacznikKey;
    private final NamespacedKey przepisKey;

    public CentralaItemFactory(VolkerNemrodAlarmPlugin plugin) {
        this.plugin = plugin;
        this.znacznikKey = new NamespacedKey(plugin, "centrala_item");
        this.przepisKey = new NamespacedKey(plugin, "centrala_lectern");
    }

    /** Buduje przedmiot centrali (Lectern z nazwą, opisem i znacznikiem). */
    public ItemStack zbudujPrzedmiot() {
        ItemStack item = new ItemStack(Material.LECTERN);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lCentrala VolkerNemrodAlarm");
        meta.setLore(List.of(
                "§7Postaw, aby założyć system alarmowy.",
                "§7Zwykły pulpit (Lectern) NIE jest centralą —",
                "§7tylko ten przedmiot, wykonany specjalną recepturą."
        ));
        meta.getPersistentDataContainer().set(znacznikKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Rejestruje niestandardową recepturę craftingu — inną niż zwykły Lectern
     * (wanilijski Lectern: deski + półka na książki). Nasza: żelazo + księga
     * + redstone, w układzie kojarzącym się z panelem alarmowym:
     * <pre>
     * I B I
     * R   R
     * I R I
     * </pre>
     */
    public void zarejestrujPrzepis() {
        ShapedRecipe przepis = new ShapedRecipe(przepisKey, zbudujPrzedmiot());
        przepis.shape("IBI", "R R", "IRI");
        przepis.setIngredient('I', Material.IRON_INGOT);
        przepis.setIngredient('B', Material.BOOK);
        przepis.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(przepis);
        plugin.getLogger().info("Zarejestrowano recepturę na Centralę VolkerNemrodAlarm.");
    }

    /** Czy dany przedmiot to nasza centrala (a nie zwykły Lectern). */
    public boolean jestCentralaItem(ItemStack item) {
        if (item == null || item.getType() != Material.LECTERN || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Boolean wartosc = meta.getPersistentDataContainer().get(znacznikKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(wartosc);
    }

    public NamespacedKey getZnacznikKey() {
        return znacznikKey;
    }
}
