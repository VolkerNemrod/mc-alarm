package pl.volkernemrod.mcalarm.storage;

import pl.volkernemrod.mcalarm.model.TypZdarzenia;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * DAO dla tabeli `konfiguracja_reakcji` — które typy zdarzeń wywołują alarm
 * dla danej centrali. Plan działania — Etap 5.
 *
 * Gdy centrala nie ma jawnego wpisu dla danego typu, obowiązują DOMYŚLNE
 * wartości poniżej. ROBOCZE ZAŁOŻENIE Claude — do zweryfikowania z dokładną
 * tabelą w GAMEPLAY_SPEC rozdz. 74 (nieodczytaną ponownie w tej sesji).
 *
 * V1: brak jeszcze komendy do zmiany tych ustawień przez gracza — ustaw()
 * istnieje w kodzie, ale nic go na razie nie wywołuje. To naturalny dopisek
 * do kolejki (komenda /alarm reaction set <typ> <on|off>).
 */
public class KonfiguracjaReakcjiRepository {

    private static final Map<TypZdarzenia, Boolean> DOMYSLNE = new EnumMap<>(TypZdarzenia.class);
    static {
        DOMYSLNE.put(TypZdarzenia.PLAYER_ENTER_ZONE, true);
        DOMYSLNE.put(TypZdarzenia.PLAYER_EXIT_ZONE, false);
        DOMYSLNE.put(TypZdarzenia.BLOCK_BREAK, true);
        DOMYSLNE.put(TypZdarzenia.BLOCK_PLACE, false);
        DOMYSLNE.put(TypZdarzenia.CONTAINER_OPEN, false);
        DOMYSLNE.put(TypZdarzenia.CONTAINER_ITEM_ADD, false);
        DOMYSLNE.put(TypZdarzenia.CONTAINER_ITEM_REMOVE, true);
    }

    private final DatabaseManager db;

    public KonfiguracjaReakcjiRepository(DatabaseManager db) {
        this.db = db;
    }

    public boolean czyWywolujeAlarm(UUID centralaId, TypZdarzenia typ) throws SQLException {
        String sql = "SELECT wywoluje_alarm FROM konfiguracja_reakcji WHERE centrala_id = ? AND typ_zdarzenia = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, centralaId.toString());
            ps.setString(2, typ.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("wywoluje_alarm");
                }
            }
        }
        return DOMYSLNE.getOrDefault(typ, false);
    }

    public void ustaw(UUID centralaId, TypZdarzenia typ, boolean wywoluje) throws SQLException {
        String sql = """
            INSERT INTO konfiguracja_reakcji (centrala_id, typ_zdarzenia, wywoluje_alarm)
            VALUES (?, ?, ?)
            ON CONFLICT(centrala_id, typ_zdarzenia) DO UPDATE SET wywoluje_alarm = excluded.wywoluje_alarm;
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, centralaId.toString());
            ps.setString(2, typ.name());
            ps.setBoolean(3, wywoluje);
            ps.executeUpdate();
        }
    }
}
