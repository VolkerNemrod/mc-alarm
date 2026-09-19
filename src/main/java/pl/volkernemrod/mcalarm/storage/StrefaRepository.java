package pl.volkernemrod.mcalarm.storage;

import pl.volkernemrod.mcalarm.model.Strefa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO dla tabeli `strefy`. Plan działania — Etap 2.
 */
public class StrefaRepository {

    private final DatabaseManager db;

    public StrefaRepository(DatabaseManager db) {
        this.db = db;
    }

    public void zapisz(Strefa strefa) throws SQLException {
        String sql = """
            INSERT INTO strefy (id, centrala_id, nazwa, swiat, x1, y1, z1, x2, y2, z2, aktywna)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nazwa = excluded.nazwa,
                swiat = excluded.swiat,
                x1 = excluded.x1, y1 = excluded.y1, z1 = excluded.z1,
                x2 = excluded.x2, y2 = excluded.y2, z2 = excluded.z2,
                aktywna = excluded.aktywna;
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, strefa.getId().toString());
            ps.setString(2, strefa.getCentralaId().toString());
            ps.setString(3, strefa.getNazwa());
            ps.setString(4, strefa.getSwiat());
            ps.setInt(5, strefa.getX1());
            ps.setInt(6, strefa.getY1());
            ps.setInt(7, strefa.getZ1());
            ps.setInt(8, strefa.getX2());
            ps.setInt(9, strefa.getY2());
            ps.setInt(10, strefa.getZ2());
            ps.setBoolean(11, strefa.isAktywna());
            ps.executeUpdate();
        }
    }

    public Optional<Strefa> znajdzPoId(UUID id) throws SQLException {
        String sql = "SELECT * FROM strefy WHERE id = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapuj(rs));
            }
        }
    }

    /** Zwraca strefę przypisaną do danej centrali (V1: jedna centrala = jedna strefa). */
    public Optional<Strefa> znajdzPoCentrali(UUID centralaId) throws SQLException {
        String sql = "SELECT * FROM strefy WHERE centrala_id = ? AND aktywna = 1 LIMIT 1";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, centralaId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapuj(rs));
            }
        }
    }

    public void usun(UUID id) throws SQLException {
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("DELETE FROM strefy WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private Strefa mapuj(ResultSet rs) throws SQLException {
        Strefa strefa = new Strefa(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("centrala_id")),
                rs.getString("nazwa"),
                rs.getString("swiat"),
                rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
        );
        strefa.setAktywna(rs.getBoolean("aktywna"));
        return strefa;
    }
}
