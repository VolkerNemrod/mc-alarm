package pl.volkernemrod.mcalarm.storage;

import pl.volkernemrod.mcalarm.model.TypZdarzenia;
import pl.volkernemrod.mcalarm.model.Waznosc;
import pl.volkernemrod.mcalarm.model.Zdarzenie;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DAO dla tabeli `zdarzenia`. Plan działania — Etap 4 (silnik zdarzeń) +
 * Etap 6 (odczyt/historia, start).
 */
public class ZdarzenieRepository {

    private final DatabaseManager db;

    public ZdarzenieRepository(DatabaseManager db) {
        this.db = db;
    }

    public void zapisz(Zdarzenie z) throws SQLException {
        String sql = """
            INSERT INTO zdarzenia (id, centrala_id, strefa_id, incydent_id, gracz, typ, waznosc,
                                    szczegoly, swiat, x, y, z, czas)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, z.getId().toString());
            ps.setString(2, z.getCentralaId().toString());
            ps.setString(3, z.getStrefaId() == null ? null : z.getStrefaId().toString());
            ps.setString(4, z.getIncydentId() == null ? null : z.getIncydentId().toString());
            ps.setString(5, z.getGracz() == null ? null : z.getGracz().toString());
            ps.setString(6, z.getTyp().name());
            ps.setString(7, z.getWaznosc().name());
            ps.setString(8, z.getSzczegoly());
            ps.setString(9, z.getSwiat());
            ps.setInt(10, z.getX());
            ps.setInt(11, z.getY());
            ps.setInt(12, z.getZ());
            ps.setString(13, z.getCzas().toString());
            ps.executeUpdate();
        }
    }

    /** Wygodny skrót — buduje i zapisuje Zdarzenie z automatycznym id i czasem "teraz". */
    public void zarejestruj(UUID centralaId, UUID strefaId, UUID incydentId, UUID gracz, TypZdarzenia typ,
                             Waznosc waznosc, String szczegoly, String swiat, int x, int y, int z) throws SQLException {
        Zdarzenie zdarzenie = new Zdarzenie(UUID.randomUUID(), centralaId, strefaId, incydentId,
                gracz, typ, waznosc, szczegoly, swiat, x, y, z, Instant.now());
        zapisz(zdarzenie);
    }

    /** Ostatnie N zdarzeń danej centrali, od najnowszych. Etap 6 (start) — pełne filtry później. */
    public List<Zdarzenie> znajdzOstatnie(UUID centralaId, int limit) throws SQLException {
        String sql = """
            SELECT id, centrala_id, strefa_id, incydent_id, gracz, typ, waznosc, szczegoly, swiat, x, y, z, czas
            FROM zdarzenia WHERE centrala_id = ? ORDER BY czas DESC LIMIT ?
        """;
        List<Zdarzenie> wynik = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, centralaId.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wynik.add(mapuj(rs));
                }
            }
        }
        return wynik;
    }

    /**
     * Zdarzenia jednej centrali z opcjonalnymi filtrami (gracz i/lub typ), od najnowszych.
     * Etap 6 c.d. — filtry historii. gracz==null lub typ==null pomija dany filtr.
     */
    public List<Zdarzenie> znajdzZFiltrami(UUID centralaId, UUID gracz, TypZdarzenia typ, int limit) throws SQLException {
        return znajdzZFiltrami(centralaId, gracz, typ, limit, 0);
    }

    /**
     * Etap 5 (GUI historii) — jak wyżej, ale ze stronicowaniem (offset). Żeby wykryć, czy jest
     * kolejna strona, wywołujący zwykle prosi o limit+1 i sprawdza, czy dostał więcej niż limit.
     */
    public List<Zdarzenie> znajdzZFiltrami(UUID centralaId, UUID gracz, TypZdarzenia typ, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, centrala_id, strefa_id, incydent_id, gracz, typ, waznosc, szczegoly, swiat, x, y, z, czas "
                        + "FROM zdarzenia WHERE centrala_id = ?");
        List<String> parametryTekstowe = new ArrayList<>();
        parametryTekstowe.add(centralaId.toString());
        if (gracz != null) {
            sql.append(" AND gracz = ?");
            parametryTekstowe.add(gracz.toString());
        }
        if (typ != null) {
            sql.append(" AND typ = ?");
            parametryTekstowe.add(typ.name());
        }
        sql.append(" ORDER BY czas DESC LIMIT ? OFFSET ?");

        List<Zdarzenie> wynik = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql.toString())) {
            int i = 1;
            for (String p : parametryTekstowe) {
                ps.setString(i++, p);
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wynik.add(mapuj(rs));
                }
            }
        }
        return wynik;
    }

    /**
     * Etap 5 (GUI historii, D4) — lista graczy, którzy ostatnio mieli zdarzenie w tej centrali,
     * od najnowszej aktywności, bez duplikatów. Używana do filtra GRACZ (klik przeczą po liście,
     * bez wpisywania nicka na czacie).
     */
    public List<UUID> znajdzOstatnichGraczy(UUID centralaId, int limit) throws SQLException {
        String sql = """
            SELECT gracz, MAX(czas) AS ostatnio FROM zdarzenia
            WHERE centrala_id = ? AND gracz IS NOT NULL
            GROUP BY gracz ORDER BY ostatnio DESC LIMIT ?
        """;
        List<UUID> wynik = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, centralaId.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wynik.add(UUID.fromString(rs.getString("gracz")));
                }
            }
        }
        return wynik;
    }

    /** Wszystkie zdarzenia jednego incydentu, chronologicznie rosnąco (do podglądu przebiegu "wizyty"). */
    public List<Zdarzenie> znajdzIncydentu(UUID incydentId) throws SQLException {
        String sql = """
            SELECT id, centrala_id, strefa_id, incydent_id, gracz, typ, waznosc, szczegoly, swiat, x, y, z, czas
            FROM zdarzenia WHERE incydent_id = ? ORDER BY czas ASC
        """;
        List<Zdarzenie> wynik = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, incydentId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wynik.add(mapuj(rs));
                }
            }
        }
        return wynik;
    }

    /**
     * Usuwa zdarzenia starsze niż podana granica czasowa — wspólny fundament dla
     * ręcznego /alarm admin purge (Etap 8) i przyszłej automatycznej retencji
     * (Etap 9, historia.retencja-dni z config.yml). Zwraca liczbę usuniętych wierszy.
     */
    public int usunStarsze(Instant granica) throws SQLException {
        String sql = "DELETE FROM zdarzenia WHERE czas < ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, granica.toString());
            return ps.executeUpdate();
        }
    }

    private Zdarzenie mapuj(ResultSet rs) throws SQLException {
        String strefaId = rs.getString("strefa_id");
        String incydentId = rs.getString("incydent_id");
        String gracz = rs.getString("gracz");
        return new Zdarzenie(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("centrala_id")),
                strefaId == null ? null : UUID.fromString(strefaId),
                incydentId == null ? null : UUID.fromString(incydentId),
                gracz == null ? null : UUID.fromString(gracz),
                TypZdarzenia.valueOf(rs.getString("typ")),
                Waznosc.valueOf(rs.getString("waznosc")),
                rs.getString("szczegoly"),
                rs.getString("swiat"),
                rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                Instant.parse(rs.getString("czas"))
        );
    }
}
