package pl.volkernemrod.mcalarm.storage;

import pl.volkernemrod.mcalarm.model.Glosnik;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO dla tabeli `glosniki`. Plan działania — punkt B (głośnik).
 *
 * Tabela tworzona przez DatabaseManager.start() razem z pozostałymi tabelami.
 */
public class GlosnikRepository {

    private final DatabaseManager db;

    public GlosnikRepository(DatabaseManager db) {
        this.db = db;
    }

    public void zapisz(Glosnik glosnik) throws SQLException {
        String sql = """
            INSERT INTO glosniki (id, centrala_id, swiat, x, y, z, dzwiek, efekty)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                dzwiek  = excluded.dzwiek,
                efekty  = excluded.efekty;
        """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, glosnik.getId().toString());
            ps.setString(2, glosnik.getCentralaId().toString());
            ps.setString(3, glosnik.getSwiat());
            ps.setInt(4, glosnik.getX());
            ps.setInt(5, glosnik.getY());
            ps.setInt(6, glosnik.getZ());
            ps.setBoolean(7, glosnik.isDzwiekWlaczony());
            ps.setBoolean(8, glosnik.isEfektyWlaczone());
            ps.executeUpdate();
        }
    }

    public Optional<Glosnik> znajdzPoLokalizacji(String swiat, int x, int y, int z) throws SQLException {
        String sql = "SELECT * FROM glosniki WHERE swiat = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, swiat);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapuj(rs));
            }
        }
    }

    public List<Glosnik> znajdzPoCentrali(UUID centralaId) throws SQLException {
        String sql = "SELECT * FROM glosniki WHERE centrala_id = ?";
        List<Glosnik> lista = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, centralaId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapuj(rs));
                }
            }
        }
        return lista;
    }

    public void usun(UUID id) throws SQLException {
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("DELETE FROM glosniki WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    public void usunPoLokalizacji(String swiat, int x, int y, int z) throws SQLException {
        String sql = "DELETE FROM glosniki WHERE swiat = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, swiat);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        }
    }

    public List<Glosnik> wszystkie() throws SQLException {
        List<Glosnik> lista = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("SELECT * FROM glosniki");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapuj(rs));
            }
        }
        return lista;
    }

    private Glosnik mapuj(ResultSet rs) throws SQLException {
        return new Glosnik(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("centrala_id")),
                rs.getString("swiat"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getBoolean("dzwiek"),
                rs.getBoolean("efekty")
        );
    }
}
