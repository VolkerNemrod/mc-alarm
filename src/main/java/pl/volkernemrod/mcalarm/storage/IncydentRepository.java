package pl.volkernemrod.mcalarm.storage;

import pl.volkernemrod.mcalarm.model.Incydent;
import pl.volkernemrod.mcalarm.model.StatusIncydentu;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO dla tabeli `incydenty`. Plan działania — Etap 6.
 */
public class IncydentRepository {

    private final DatabaseManager db;

    public IncydentRepository(DatabaseManager db) {
        this.db = db;
    }

    public void zapisz(Incydent incydent) throws SQLException {
        String sql = """
            INSERT INTO incydenty (id, centrala_id, strefa_id, gracz, start, koniec, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET koniec = excluded.koniec, status = excluded.status;
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, incydent.getId().toString());
            ps.setString(2, incydent.getCentralaId().toString());
            ps.setString(3, incydent.getStrefaId() == null ? null : incydent.getStrefaId().toString());
            ps.setString(4, incydent.getGracz() == null ? null : incydent.getGracz().toString());
            ps.setString(5, incydent.getStart().toString());
            ps.setString(6, incydent.getKoniec() == null ? null : incydent.getKoniec().toString());
            ps.setString(7, incydent.getStatus().name());
            ps.executeUpdate();
        }
    }

    public void zamknij(UUID incydentId, Instant koniec) throws SQLException {
        String sql = "UPDATE incydenty SET koniec = ?, status = ? WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, koniec.toString());
            ps.setString(2, StatusIncydentu.ZAMKNIETY.name());
            ps.setString(3, incydentId.toString());
            ps.executeUpdate();
        }
    }

    /** Pojedynczy incydent po id — do podglądu szczegółów (punkt E). */
    public Optional<Incydent> znajdzPoId(UUID id) throws SQLException {
        String sql = """
            SELECT id, centrala_id, strefa_id, gracz, start, koniec, status
            FROM incydenty WHERE id = ?
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapuj(rs));
            }
        }
    }

    /** Ostatnie N incydentów danej centrali, od najnowszych. */
    public List<Incydent> znajdzOstatnie(UUID centralaId, int limit) throws SQLException {
        String sql = """
            SELECT id, centrala_id, strefa_id, gracz, start, koniec, status
            FROM incydenty WHERE centrala_id = ? ORDER BY start DESC LIMIT ?
        """;
        List<Incydent> wynik = new ArrayList<>();
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

    /** Liczba obecnie otwartych incydentów na CAŁYM serwerze — używane przez /alarm admin debug (Etap 8). */
    public int liczOtwarte() throws SQLException {
        String sql = "SELECT COUNT(*) FROM incydenty WHERE status = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, StatusIncydentu.OTWARTY.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Usuwa ZAMKNIĘTE incydenty rozpoczęte przed podaną granicą czasową — wspólny
     * fundament dla ręcznego /alarm admin purge (Etap 8) i przyszłej automatycznej
     * retencji (Etap 9). OTWARTE incydenty NIGDY nie są usuwane, nawet stare —
     * to byłoby kasowanie aktywnego stanu, nie historii. Zwraca liczbę usuniętych wierszy.
     */
    public int usunStarsze(Instant granica) throws SQLException {
        String sql = "DELETE FROM incydenty WHERE start < ? AND status = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, granica.toString());
            ps.setString(2, StatusIncydentu.ZAMKNIETY.name());
            return ps.executeUpdate();
        }
    }

    private Incydent mapuj(ResultSet rs) throws SQLException {
        String strefaId = rs.getString("strefa_id");
        String gracz = rs.getString("gracz");
        Incydent incydent = new Incydent(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("centrala_id")),
                strefaId == null ? null : UUID.fromString(strefaId),
                gracz == null ? null : UUID.fromString(gracz),
                Instant.parse(rs.getString("start"))
        );
        String koniec = rs.getString("koniec");
        if (koniec != null) {
            incydent.zamknij(Instant.parse(koniec));
        }
        return incydent;
    }
}
