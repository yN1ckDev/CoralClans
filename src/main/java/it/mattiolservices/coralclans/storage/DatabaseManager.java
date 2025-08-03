package it.mattiolservices.coralclans.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.services.manager.Manager;
import lombok.Getter;
import lombok.Setter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
@Setter
public class DatabaseManager implements Manager {

    private static DatabaseManager INSTANCE;
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    private HikariDataSource dataSource;
    private ExecutorService executor;

    @Override
    public void start() {
        INSTANCE = this;

        try {
            setupDatabase();
            setupExecutor();
            createTablesAsync();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start Database!", e);
        }
    }

    @Override
    public void stop() {
        if (INSTANCE != null) {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            INSTANCE = null;
        }
    }

    public static DatabaseManager get() {
        return INSTANCE;
    }

    private void setupDatabase() {
        HikariConfig config = new HikariConfig();

        String host = CoralClans.get().getConfigManager().getStorage().getString("MariaDB.host", "localhost");
        int port = CoralClans.get().getConfigManager().getStorage().getInt("MariaDB.port", 3306);
        String database = CoralClans.get().getConfigManager().getStorage().getString("MariaDB.database", "coralclans");
        String username = CoralClans.get().getConfigManager().getStorage().getString("MariaDB.username", "root");
        String password = CoralClans.get().getConfigManager().getStorage().getString("MariaDB.password", "");

        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");

        config.addDataSourceProperty("allowMultiQueries", "false");
        config.addDataSourceProperty("allowLoadLocalInfile", "false");

        this.dataSource = new HikariDataSource(config);
    }

    private void setupExecutor() {
        this.executor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "ClanDB-" + r.hashCode());
            thread.setDaemon(true);
            return thread;
        });
    }

    private void createTablesAsync() {
        executeAsync(() -> {
            try (Connection conn = getConnection()) {
                executeUpdate(conn, """
                    CREATE TABLE IF NOT EXISTS clans (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(32) NOT NULL UNIQUE,
                        tag VARCHAR(8) NOT NULL UNIQUE,
                        leader_uuid CHAR(36) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        home_world VARCHAR(64),
                        home_x DOUBLE DEFAULT 0,
                        home_y DOUBLE DEFAULT 0,
                        home_z DOUBLE DEFAULT 0,
                        INDEX idx_leader (leader_uuid),
                        INDEX idx_name (name)
                    )
                """);

                executeUpdate(conn, """
                    CREATE TABLE IF NOT EXISTS clan_members (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        player_uuid CHAR(36) NOT NULL,
                        player_name VARCHAR(16) NOT NULL,
                        role ENUM('LEADER', 'OFFICER', 'MEMBER') DEFAULT 'MEMBER',
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                        UNIQUE KEY unique_player (clan_id, player_uuid),
                        INDEX idx_player (player_uuid),
                        INDEX idx_clan (clan_id)
                    )
                """);

                executeUpdate(conn, """
                    CREATE TABLE IF NOT EXISTS clan_territories (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        clan_id INT NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        region_name VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL,
                        min_z INT NOT NULL,
                        max_x INT NOT NULL,
                        max_z INT NOT NULL,
                        claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                        INDEX idx_clan (clan_id),
                        INDEX idx_world (world)
                    )
                """);


                LOGGER.info("All clan tables created successfully");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error creating tables", e);
            }
        });
    }

    private void executeUpdate(Connection conn, String sql) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource not available");
        }
        return dataSource.getConnection();
    }

    public CompletableFuture<Void> executeAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    public <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}