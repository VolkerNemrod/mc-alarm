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
 * Odpowiada za przedmiot "Głośnik VolkerNemrodAlarm" — blok NOTE_BLOCK
 * oznaczony niestandardowym znacznikiem (PersistentDataContainer) i
 * craftowany inną recepturą niż zwykły Note Block.
 *
 * Głośnik musi być umieszczony wewnątrz aktywnej strefy alarmowej —
 * wtedy uczestniczy w reakcji alarmowej (dźwięk ON/OFF, efekty wizualne ON/OFF).
 * Konfiguracja per głośnik przez GUI (prawy klik właściciela/zaufanego).
 *
 * Tymczasowy blok: NOTE_BLOCK. Docelowo własny model przez Custom Model Data
 * (Blockbench → resource pack). Plan działania — punkt D z mem-palace.
 *
 * Receptura (inna niż waniliowy Note Block — drewniane deski + beton):
 * <pre>
 * I R I
 * R N R
 * I R I
 * </pre>
 * I = IRON_INGOT, R = REDSTONE, N = NOTE_BLOCK
 */
public class GlosnikItemFactory {

    private final VolkerNemrodAlarmPlugin plugin;
    private final NamespacedKey znacznikKey;
    private final NamespacedKey przepisKey;

    public GlosnikItemFactory(VolkerNemrodAlarmPlugin plugin) {
        this.plugin = plugin;
        this.znacznikKey = new NamespacedKey(plugin, "glosnik_item");
        this.przepisKey = new NamespacedKey(plugin, "glosnik_note_block");
    }

    /** Buduje przedmiot głośnika (NOTE_BLOCK z nazwą, opisem i znacznikiem). */
    public ItemStack zbudujPrzedmiot() {
        ItemStack item = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lGłośnik VolkerNemrodAlarm");
        meta.setLore(List.of(
                "§7Postaw w strefie alarmowej, aby",
                "§7emitował dźwięk i efekty wizualne podczas alarmu.",
                "§7Prawy klik właściciela → konfiguracja.",
                "§7Zwykły Note Block NIE jest głośnikiem —",
                "§7tylko ten przedmiot, wykonany specjalną recepturą."
        ));
        meta.getPersistentDataContainer().set(znacznikKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Rejestruje niestandardową recepturę:
     * I R I
     * R N R
     * I R I
     */
    public void zarejestrujPrzepis() {
        ShapedRecipe przepis = new ShapedRecipe(przepisKey, zbudujPrzedmiot());
        przepis.shape("IRI", "RNR", "IRI");
        przepis.setIngredient('I', Material.IRON_INGOT);
        przepis.setIngredient('R', Material.REDSTONE);
        przepis.setIngredient('N', Material.NOTE_BLOCK);
        plugin.getServer().addRecipe(przepis);
        plugin.getLogger().info("Zarejestrowano recepturę na Głośnik VolkerNemrodAlarm.");
    }

    /** Czy dany przedmiot to nasz głośnik (a nie zwykły Note Block). */
    public boolean jestGlosnikItem(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK || !item.hasItemMeta()) {
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
