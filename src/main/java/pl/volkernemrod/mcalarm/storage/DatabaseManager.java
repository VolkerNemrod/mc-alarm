package pl.volkernemrod.mcalarm.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

/**
 * Zarządza jednym, długożyjącym połączeniem SQLite dla całego pluginu oraz
 * tworzy schemat bazy przy starcie. Plan działania — Etap 1.
 *
 * Plik bazy: <folder pluginu>/database.db
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Otwiera połączenie i tworzy brakujące tabele. Zwraca false przy błędzie. */
    public boolean start() {
        try {
            Class.forName("org.sqlite.JDBC");

            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "database.db");

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON;");
            }

            createSchema();
            plugin.getLogger().info("Baza SQLite zainicjalizowana: " + dbFile.getAbsolutePath());
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Brak sterownika SQLite JDBC na classpath.", e);
            return false;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Nie udało się zainicjalizować bazy SQLite.", e);
            return false;
        }
    }

    public void stop() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Błąd przy zamykaniu połączenia z SQLite.", e);
            }
        }
    }

    public Connection getConnection() {
        return connection;
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS centrale (
                    id TEXT PRIMARY KEY,
                    wlasciciel TEXT NOT NULL,
                    nazwa TEXT NOT NULL,
                    strefa_id TEXT,
                    tryb TEXT NOT NULL DEFAULT 'ROZBROJONY',
                    swiat TEXT,
                    x INTEGER,
                    y INTEGER,
                    z INTEGER,
                    utworzono TEXT NOT NULL
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS strefy (
                    id TEXT PRIMARY KEY,
                    centrala_id TEXT NOT NULL,
                    nazwa TEXT NOT NULL,
                    swiat TEXT NOT NULL,
                    x1 INTEGER NOT NULL, y1 INTEGER NOT NULL, z1 INTEGER NOT NULL,
                    x2 INTEGER NOT NULL, y2 INTEGER NOT NULL, z2 INTEGER NOT NULL,
                    aktywna INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (centrala_id) REFERENCES centrale(id) ON DELETE CASCADE
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS zaufani (
                    centrala_id TEXT NOT NULL,
                    gracz TEXT NOT NULL,
                    PRIMARY KEY (centrala_id, gracz),
                    FOREIGN KEY (centrala_id) REFERENCES centrale(id) ON DELETE CASCADE
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS incydenty (
                    id TEXT PRIMARY KEY,
                    centrala_id TEXT NOT NULL,
                    strefa_id TEXT,
                    gracz TEXT,
                    start TEXT NOT NULL,
                    koniec TEXT,
                    status TEXT NOT NULL DEFAULT 'OTWARTY',
                    FOREIGN KEY (centrala_id) REFERENCES centrale(id) ON DELETE CASCADE
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS zdarzenia (
                    id TEXT PRIMARY KEY,
                    centrala_id TEXT NOT NULL,
                    strefa_id TEXT,
                    incydent_id TEXT,
                    gracz TEXT,
                    typ TEXT NOT NULL,
                    waznosc TEXT NOT NULL,
                    szczegoly TEXT,
                    swiat TEXT NOT NULL,
                    x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                    czas TEXT NOT NULL,
                    FOREIGN KEY (centrala_id) REFERENCES centrale(id) ON DELETE CASCADE,
                    FOREIGN KEY (incydent_id) REFERENCES incydenty(id) ON DELETE SET NULL
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS konfiguracja_reakcji (
                    centrala_id TEXT NOT NULL,
                    typ_zdarzenia TEXT NOT NULL,
                    wywoluje_alarm INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (centrala_id, typ_zdarzenia),
                    FOREIGN KEY (centrala_id) REFERENCES centrale(id) ON DELETE CASCADE
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS glosniki (
                    id TEXT PRIMARY KEY,
                    centrala_id TEXT NOT NULL,
                    swiat TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    dzwiek INTEGER NOT NULL DEFAULT 1,
                    efekty INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (centrala_id) REFERENCES centrale(id) ON DELETE CASCADE
                );
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_glosniki_lokalizacja ON glosniki(swiat, x, y, z);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_glosniki_centrala ON glosniki(centrala_id);");

            // Indeks pod szybkie wyszukiwanie centrali po bloku w świecie
            // (listener zniszczenia bloku centrali — Etap 2).
            st.execute("CREATE INDEX IF NOT EXISTS idx_centrale_lokalizacja ON centrale(swiat, x, y, z);");

            // Indeksy pod najczęstsze zapytania historii (rozdz. 39-41 GAMEPLAY_SPEC).
            st.execute("CREATE INDEX IF NOT EXISTS idx_zdarzenia_centrala ON zdarzenia(centrala_id, czas);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_zdarzenia_gracz ON zdarzenia(gracz, czas);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_incydenty_centrala ON incydenty(centrala_id, start);");
        }
    }
}
