package pl.volkernemrod.mcalarm.storage;

import pl.volkernemrod.mcalarm.model.Centrala;
import pl.volkernemrod.mcalarm.model.TrybPracy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO dla tabeli `centrale` (+ powiązanej `zaufani`). Plan działania — Etap 1.
 *
 * Uwaga: metody wykonują zapytania synchronicznie. W kolejnych etapach
 * wywołania z głównego wątku serwera powinny iść przez
 * Bukkit#getScheduler()#runTaskAsynchronously, żeby nie blokować tick loopa.
 */
public class CentralaRepository {

    private final DatabaseManager db;

    public CentralaRepository(DatabaseManager db) {
        this.db = db;
    }

    public void zapisz(Centrala centrala) throws SQLException {
        Connection c = db.getConnection();
        String sql = """
            INSERT INTO centrale (id, wlasciciel, nazwa, strefa_id, tryb, swiat, x, y, z, utworzono)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                nazwa = excluded.nazwa,
                strefa_id = excluded.strefa_id,
                tryb = excluded.tryb,
                swiat = excluded.swiat,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z;
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, centrala.getId().toString());
            ps.setString(2, centrala.getWlasciciel().toString());
            ps.setString(3, centrala.getNazwa());
            ps.setString(4, centrala.getStrefaId() == null ? null : centrala.getStrefaId().toString());
            ps.setString(5, centrala.getTryb().name());
            if (centrala.maLokalizacje()) {
                ps.setString(6, centrala.getSwiat());
                ps.setInt(7, centrala.getX());
                ps.setInt(8, centrala.getY());
                ps.setInt(9, centrala.getZ());
            } else {
                ps.setNull(6, java.sql.Types.VARCHAR);
                ps.setNull(7, java.sql.Types.INTEGER);
                ps.setNull(8, java.sql.Types.INTEGER);
                ps.setNull(9, java.sql.Types.INTEGER);
            }
            ps.setString(10, Instant.now().toString());
            ps.executeUpdate();
        }
        zapiszZaufanych(centrala);
    }

    private void zapiszZaufanych(Centrala centrala) throws SQLException {
        Connection c = db.getConnection();
        try (PreparedStatement del = c.prepareStatement("DELETE FROM zaufani WHERE centrala_id = ?")) {
            del.setString(1, centrala.getId().toString());
            del.executeUpdate();
        }
        String sql = "INSERT INTO zaufani (centrala_id, gracz) VALUES (?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (UUID gracz : centrala.getZaufani()) {
                ps.setString(1, centrala.getId().toString());
                ps.setString(2, gracz.toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Optional<Centrala> znajdzPoId(UUID id) throws SQLException {
        Connection c = db.getConnection();
        String sql = "SELECT id, wlasciciel, nazwa, strefa_id, tryb, swiat, x, y, z FROM centrale WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapuj(rs));
            }
        }
    }

    /** Szuka centrali po dokładnej lokalizacji jej bloku (listener zniszczenia — Etap 2). */
    public Optional<Centrala> znajdzPoLokalizacji(String swiat, int x, int y, int z) throws SQLException {
        Connection c = db.getConnection();
        String sql = """
            SELECT id, wlasciciel, nazwa, strefa_id, tryb, swiat, x, y, z
            FROM centrale WHERE swiat = ? AND x = ? AND y = ? AND z = ?
        """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, swiat);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapuj(rs));
            }
        }
    }

    public List<Centrala> znajdzWlascicielaId(UUID wlasciciel) throws SQLException {
        Connection c = db.getConnection();
        String sql = "SELECT id, wlasciciel, nazwa, strefa_id, tryb, swiat, x, y, z FROM centrale WHERE wlasciciel = ?";
        List<Centrala> wynik = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, wlasciciel.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    wynik.add(mapuj(rs));
                }
            }
        }
        return wynik;
    }

    /** Zwraca WSZYSTKIE centrale — używane do wypełnienia CentralaRejestr (cache w pamięci) przy starcie pluginu. */
    public List<Centrala> wszystkie() throws SQLException {
        Connection c = db.getConnection();
        String sql = "SELECT id, wlasciciel, nazwa, strefa_id, tryb, swiat, x, y, z FROM centrale";
        List<Centrala> wynik = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                wynik.add(mapuj(rs));
            }
        }
        return wynik;
    }

    /** Legalne usunięcie (ścieżka a) — historia NIE jest kasowana, tylko sama centrala. */
    public void usunLegalnie(UUID id) throws SQLException {
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("DELETE FROM centrale WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    /**
     * Twardy reset po fizycznym zniszczeniu uzbrojonej centrali (ścieżka b) —
     * kasuje też historię (zdarzenia, incydenty) tej centrali, zgodnie
     * z decyzją Volkera z 2026-09-12 (mem-palace, wing mc-alarm, decisions).
     * ON DELETE CASCADE w schemacie robi resztę.
     */
    public void zniszczFizycznie(UUID id) throws SQLException {
        usunLegalnie(id); // CASCADE skasuje: strefy, zaufani, incydenty, zdarzenia, konfiguracja_reakcji
    }

    private Centrala mapuj(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID wlasciciel = UUID.fromString(rs.getString("wlasciciel"));
        Centrala centrala = new Centrala(id, wlasciciel, rs.getString("nazwa"));

        String strefaId = rs.getString("strefa_id");
        if (strefaId != null) {
            centrala.setStrefaId(UUID.fromString(strefaId));
        }
        centrala.setTryb(TrybPracy.valueOf(rs.getString("tryb")));

        String swiat = rs.getString("swiat");
        if (swiat != null) {
            centrala.setLokalizacja(swiat, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
        }

        wczytajZaufanych(centrala);
        return centrala;
    }

    private void wczytajZaufanych(Centrala centrala) throws SQLException {
        try (PreparedStatement ps = db.getConnection()
                .prepareStatement("SELECT gracz FROM zaufani WHERE centrala_id = ?")) {
            ps.setString(1, centrala.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    centrala.dodajZaufanego(UUID.fromString(rs.getString("gracz")));
                }
            }
        }
    }
}
