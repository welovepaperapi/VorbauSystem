package aut.philippzinhobl.manager;

import aut.philippzinhobl.Main;
import org.bukkit.configuration.file.FileConfiguration;
import java.sql.*;

public class DatabaseManager {

    private final Main plugin;
    private Connection connection;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) return;

            FileConfiguration config = plugin.getDatabaseConfig();

            String host = config.getString("host", "localhost");
            String port = config.getString("port", "3306");
            String database = config.getString("database", "buildserver");
            String user = config.getString("user", "root");
            String password = config.getString("password", "");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false";
            connection = DriverManager.getConnection(url, user, password);

            setupTables();
            plugin.getLogger().info("Datenbank erfolgreich verbunden.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Fehler bei der Datenbankverbindung: " + e.getMessage());
        }
    }

    private void setupTables() {
        try (Statement s = connection.createStatement()) {
            // Tabellen erstellen
            s.executeUpdate("CREATE TABLE IF NOT EXISTS vorbau_worlds (uuid VARCHAR(36), world_name VARCHAR(64) PRIMARY KEY, slot INT, type VARCHAR(10))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS world_builders (world_name VARCHAR(64), builder_uuid VARCHAR(36), PRIMARY KEY (world_name, builder_uuid))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS vorbau_applications (uuid VARCHAR(36) PRIMARY KEY, world_name VARCHAR(64), discord VARCHAR(64), real_name VARCHAR(64), age INT, comment TEXT, status VARCHAR(20) DEFAULT 'PENDING', notified BOOLEAN DEFAULT FALSE)");

            s.executeUpdate("ALTER TABLE vorbau_worlds ADD COLUMN IF NOT EXISTS display_name VARCHAR(64) AFTER world_name");
            s.executeUpdate("ALTER TABLE vorbau_worlds ADD COLUMN IF NOT EXISTS is_finished BOOLEAN DEFAULT FALSE");
            s.executeUpdate("ALTER TABLE vorbau_applications ADD COLUMN IF NOT EXISTS notified BOOLEAN DEFAULT FALSE");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) connect();
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}