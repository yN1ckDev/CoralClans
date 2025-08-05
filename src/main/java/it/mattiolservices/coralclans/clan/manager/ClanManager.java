package it.mattiolservices.coralclans.clan.manager;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.clan.data.ClanInviteData;
import it.mattiolservices.coralclans.clan.enums.ClanRole;
import it.mattiolservices.coralclans.clan.structure.ClanMemberStructure;
import it.mattiolservices.coralclans.clan.structure.ClanStructure;
import it.mattiolservices.coralclans.services.manager.Manager;
import it.mattiolservices.coralclans.storage.DatabaseManager;
import lombok.Getter;
import lombok.Setter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Getter
@Setter
public class ClanManager implements Manager {
    private static final Logger LOGGER = Logger.getLogger(ClanManager.class.getName());
    private static ClanManager INSTANCE;

    private Cache<String, Map<Integer, ClanInviteData>> playerInvites;

    private Cache<String, Boolean> clanChatCache;

    @Override
    public void start() {
        INSTANCE = this;

        this.playerInvites = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(2))
                .maximumSize(10_000)
                .build();

        this.clanChatCache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(30))
                .maximumSize(5000)
                .build();
    }

    @Override
    public void stop() {
        if(playerInvites != null) {
            playerInvites.invalidateAll();
        }

        if (clanChatCache != null) {
            clanChatCache.invalidateAll();
        }
    }

    public static ClanManager get() {
        return INSTANCE;
    }

    public CompletableFuture<Optional<ClanStructure>> createClan(String name, String tag, String leaderUuid, String leaderName) {
        return DatabaseManager.get().supplyAsync(() -> {
            try (Connection conn = DatabaseManager.get().getConnection()) {
                conn.setAutoCommit(false);

                try {
                    String clanSql = "INSERT INTO clans (name, tag, leader_uuid) VALUES (?, ?, ?)";
                    int clanId;

                    try (PreparedStatement stmt = conn.prepareStatement(clanSql, Statement.RETURN_GENERATED_KEYS)) {
                        stmt.setString(1, name);
                        stmt.setString(2, tag);
                        stmt.setString(3, leaderUuid);
                        stmt.executeUpdate();

                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                clanId = rs.getInt(1);
                            } else {
                                conn.rollback();
                                return Optional.empty();
                            }
                        }
                    }

                    String memberSql = "INSERT INTO clan_members (clan_id, player_uuid, player_name, role) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(memberSql)) {
                        stmt.setInt(1, clanId);
                        stmt.setString(2, leaderUuid);
                        stmt.setString(3, leaderName);
                        stmt.setString(4, ClanRole.LEADER.name());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    return getClanById(clanId);

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di creare il clan", e);
                return Optional.empty();
            }
        });
    }

    public CompletableFuture<Boolean> deleteClan(int clanId) {
        return DatabaseManager.get().supplyAsync(() -> {
            try (Connection conn = DatabaseManager.get().getConnection()) {
                String territorySql = "SELECT world FROM clan_territories WHERE clan_id = ?";
                List<String> worldsToClean = new ArrayList<>();

                try (PreparedStatement territoryStmt = conn.prepareStatement(territorySql)) {
                    territoryStmt.setInt(1, clanId);
                    try (ResultSet rs = territoryStmt.executeQuery()) {
                        while (rs.next()) {
                            worldsToClean.add(rs.getString("world"));
                        }
                    }
                }

                for (String worldName : worldsToClean) {
                    try {
                        boolean regionDeleted = deleteClanRegionAsync(clanId, worldName).get();
                        if (!regionDeleted) {
                            LOGGER.warning("Si è verificato un errore nel tentativo di eliminare la land: " + worldName);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di eliminare la land del mondo: " + worldName, e);
                    }
                }

                String sql = "DELETE FROM clans WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, clanId);
                    boolean deleted = stmt.executeUpdate() > 0;

                    if (deleted) {
                        playerInvites.asMap().keySet().removeIf(key -> key.startsWith(clanId + ":"));
                    }

                    return deleted;
                }

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di eliminare il clan ", e);
                return false;
            }
        });
    }


    public CompletableFuture<Optional<ClanStructure>> getClanByName(String name) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = "SELECT * FROM clans WHERE name = ?";

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(ClanStructure.fromResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di recapitare il clan dal nome", e);
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Optional<ClanStructure>> getPlayerClan(String playerUuid) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = """
                SELECT c.* FROM clans c
                JOIN clan_members cm ON c.id = cm.clan_id
                WHERE cm.player_uuid = ?
            """;

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(ClanStructure.fromResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di recapitare il clan del giocatore", e);
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Boolean> setClanHome(int clanId, String world, double x, double y, double z) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = "UPDATE clans SET home_world = ?, home_x = ?, home_y = ?, home_z = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, world);
                stmt.setDouble(2, x);
                stmt.setDouble(3, y);
                stmt.setDouble(4, z);
                stmt.setInt(5, clanId);

                return stmt.executeUpdate() > 0;

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di settare la home del clan ", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addMember(int clanId, String playerUuid, String playerName, ClanRole role) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = "INSERT INTO clan_members (clan_id, player_uuid, player_name, role) VALUES (?, ?, ?, ?)";

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, clanId);
                stmt.setString(2, playerUuid);
                stmt.setString(3, playerName);
                stmt.setString(4, role.name());

                boolean added = stmt.executeUpdate() > 0;

                if (added) {
                    ClanManager.get().deleteInvite(clanId, playerUuid);
                }

                return added;

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di aggiungere il giocatore al clan ", e);
                return false;
            }
        });
    }


    public CompletableFuture<Boolean> removeMember(int clanId, String playerUuid) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = "DELETE FROM clan_members WHERE clan_id = ? AND player_uuid = ?";

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, clanId);
                stmt.setString(2, playerUuid);

                boolean removed = stmt.executeUpdate() > 0;

                if (removed) {
                    clanChatCache.invalidate(playerUuid);
                }

                return removed;

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di rimuovere il giocatore dal clan ", e);
                return false;
            }
        });
    }

    public CompletableFuture<List<ClanMemberStructure>> getClanMembers(int clanId) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = "SELECT * FROM clan_members WHERE clan_id = ? ORDER BY role, joined_at";
            List<ClanMemberStructure> members = new ArrayList<>();

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, clanId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(ClanMemberStructure.fromResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di recapitare i membri del clan", e);
            }
            return members;
        });
    }

    public boolean createInvite(int clanId, String invitedUuid, String invitedName, String inviterUuid) {
        try {
            ClanInviteData invite = new ClanInviteData(clanId, invitedName, inviterUuid);
            Map<Integer, ClanInviteData> invites = playerInvites.get(invitedUuid, k -> new ConcurrentHashMap<>());
            invites.put(clanId, invite);
            playerInvites.put(invitedUuid, invites);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Si è verificato un errore nella creazione dell'invito", e);
            return false;
        }
    }

    public boolean deleteInvite(int clanId, String invitedUuid) {
        try {
            Map<Integer, ClanInviteData> invites = playerInvites.getIfPresent(invitedUuid);
            if (invites != null) {
                boolean removed = invites.remove(clanId) != null;
                if (invites.isEmpty()) {
                    playerInvites.invalidate(invitedUuid);
                } else {
                    playerInvites.put(invitedUuid, invites);
                }
                return removed;
            }
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Si è verificato un errore l'eliminazione dell'invito", e);
            return false;
        }
    }

    public List<ClanInviteData> getPlayerInvites(String playerUuid) {
        try {
            Map<Integer, ClanInviteData> invites = playerInvites.getIfPresent(playerUuid);
            if (invites == null) return new ArrayList<>();

            List<ClanInviteData> validInvites = invites.values().stream()
                    .filter(invite -> !invite.isExpired())
                    .collect(Collectors.toList());

            invites.entrySet().removeIf(entry -> entry.getValue().isExpired());

            if (invites.isEmpty()) {
                playerInvites.invalidate(playerUuid);
            } else {
                playerInvites.put(playerUuid, invites);
            }

            return validInvites;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante il recupero degli inviti del clan", e);
            return new ArrayList<>();
        }
    }

    public boolean hasInvite(String playerUuid, int clanId) {
        Map<Integer, ClanInviteData> invites = playerInvites.getIfPresent(playerUuid);
        if (invites == null) return false;

        ClanInviteData invite = invites.get(clanId);
        if (invite == null || invite.isExpired()) {
            invites.remove(clanId);
            if (invites.isEmpty()) {
                playerInvites.invalidate(playerUuid);
            } else {
                playerInvites.put(playerUuid, invites);
            }
            return false;
        }

        return true;
    }


    public boolean toggleClanChat(String playerUuid) {
        Boolean current = clanChatCache.getIfPresent(playerUuid);
        boolean newState = current == null || !current;

        if (newState) {
            clanChatCache.put(playerUuid, true);
        } else {
            clanChatCache.invalidate(playerUuid);
        }

        return newState;
    }

    public boolean isInClanChat(String playerUuid) {
        Boolean inChat = clanChatCache.getIfPresent(playerUuid);
        return inChat != null && inChat;
    }

    public void setClanChatMode(String playerUuid, boolean enabled) {
        if (enabled) {
            clanChatCache.put(playerUuid, true);
        } else {
            clanChatCache.invalidate(playerUuid);
        }
    }

    public CompletableFuture<List<String>> getPlayersInClanChat(int clanId) {
        return getClanMembers(clanId).thenApply(members ->
                members.stream()
                        .map(ClanMemberStructure::playerUuid)
                        .filter(this::isInClanChat)
                        .collect(Collectors.toList())
        );
    }


    public CompletableFuture<Boolean> isPlayerInClan(String playerUuid) {
        return getPlayerClan(playerUuid).thenApply(Optional::isPresent);
    }

    public CompletableFuture<Boolean> canPlayerManageClan(String playerUuid, int clanId) {
        return getClanMembers(clanId).thenApply(members ->
                members.stream()
                        .filter(member -> member.playerUuid().equals(playerUuid))
                        .anyMatch(member -> member.role() == ClanRole.LEADER || member.role() == ClanRole.OFFICER)
        );
    }

    public CompletableFuture<Boolean> transferLeadership(int clanId, String currentLeaderUuid, String newLeaderUuid) {
        return DatabaseManager.get().supplyAsync(() -> {
            try (Connection conn = DatabaseManager.get().getConnection()) {
                conn.setAutoCommit(false);

                try {
                    String sql = """
                    SELECT COUNT(*) FROM clan_members 
                    WHERE clan_id = ? AND player_uuid = ? AND role = 'LEADER'
                """;

                    try (PreparedStatement verifyStmt = conn.prepareStatement(sql)) {
                        verifyStmt.setInt(1, clanId);
                        verifyStmt.setString(2, currentLeaderUuid);

                        try (ResultSet rs = verifyStmt.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) == 0) {
                                conn.rollback();
                                return false;
                            }
                        }
                    }

                    String verifyMemberSql = """
                    SELECT COUNT(*) FROM clan_members 
                    WHERE clan_id = ? AND player_uuid = ?
                """;

                    try (PreparedStatement verifyStmt = conn.prepareStatement(verifyMemberSql)) {
                        verifyStmt.setInt(1, clanId);
                        verifyStmt.setString(2, newLeaderUuid);

                        try (ResultSet rs = verifyStmt.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) == 0) {
                                conn.rollback();
                                return false;
                            }
                        }
                    }

                    String updateClanSql = "UPDATE clans SET leader_uuid = ? WHERE id = ?";
                    try (PreparedStatement updateClanStmt = conn.prepareStatement(updateClanSql)) {
                        updateClanStmt.setString(1, newLeaderUuid);
                        updateClanStmt.setInt(2, clanId);
                        updateClanStmt.executeUpdate();
                    }

                    String demoteLeaderSql = """
                    UPDATE clan_members 
                    SET role = 'OFFICER' 
                    WHERE clan_id = ? AND player_uuid = ?
                """;

                    try (PreparedStatement demoteStmt = conn.prepareStatement(demoteLeaderSql)) {
                        demoteStmt.setInt(1, clanId);
                        demoteStmt.setString(2, currentLeaderUuid);
                        demoteStmt.executeUpdate();
                    }

                    String promoteLeaderSql = """
                    UPDATE clan_members 
                    SET role = 'LEADER' 
                    WHERE clan_id = ? AND player_uuid = ?
                """;

                    try (PreparedStatement promoteStmt = conn.prepareStatement(promoteLeaderSql)) {
                        promoteStmt.setInt(1, clanId);
                        promoteStmt.setString(2, newLeaderUuid);
                        promoteStmt.executeUpdate();
                    }

                    updateRegionOwnership(conn, clanId, currentLeaderUuid, newLeaderUuid);

                    conn.commit();
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }

            } catch (SQLException e) {
                return false;
            }
        });
    }

    private void updateRegionOwnership(Connection conn, int clanId, String oldLeaderUuid, String newLeaderUuid) {
        try {
            String clanTag = getClanTag(conn, clanId);
            if (clanTag == null) {
                return;
            }

            String regionName = "clan_" + clanTag.toLowerCase() + "_" + clanId;

            String territoryQuery = "SELECT DISTINCT world FROM clan_territories WHERE clan_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(territoryQuery)) {
                stmt.setInt(1, clanId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String worldName = rs.getString("world");
                        updateWorldRegionOwnership(worldName, regionName, oldLeaderUuid, newLeaderUuid);
                    }
                }
            }

        } catch (SQLException e) {
        }
    }

    private void updateWorldRegionOwnership(String worldName, String regionName, String oldLeaderUuid, String newLeaderUuid) {
        try {
            World bukkitWorld = Bukkit.getWorld(worldName);
            if (bukkitWorld == null) {
                return;
            }

            com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bukkitWorld);
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);

            if (regionManager == null) {
                return;
            }

            ProtectedRegion region = regionManager.getRegion(regionName);
            if (region == null) {
                return;
            }

            UUID oldLeaderUUID = UUID.fromString(oldLeaderUuid);
            UUID newLeaderUUID = UUID.fromString(newLeaderUuid);

            region.getOwners().removePlayer(oldLeaderUUID);
            region.getMembers().addPlayer(oldLeaderUUID);

            region.getMembers().removePlayer(newLeaderUUID);
            region.getOwners().addPlayer(newLeaderUUID);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore durante l'aggiornamento dei permessi della region " + regionName + " nel mondo " + worldName, e);
        }
    }

    public CompletableFuture<Boolean> createClanRegionAsync(int clanId, String worldName, int centerX, int centerZ,
                                                            boolean allowMobSpawning, boolean allowPvP, boolean allowBuild) {
        return DatabaseManager.get().supplyAsync(() -> {
            try (Connection conn = DatabaseManager.get().getConnection()) {
                int regionSize = CoralClans.get().getConfigManager().getStorage().getInt("clans.region-size", 100);
                int halfSize = regionSize / 2;

                int minX = centerX - halfSize;
                int maxX = centerX + halfSize;
                int minZ = centerZ - halfSize;
                int maxZ = centerZ + halfSize;

                String clanTag = getClanTag(conn, clanId);
                if (clanTag == null) {
                    LOGGER.warning("Si è verificato un errore nel tentativo di prendere la tag del clan con id: " + clanId);
                    return false;
                }

                String regionName = "clan_" + clanTag.toLowerCase() + "_" + clanId;

                World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld == null) {
                    LOGGER.warning("Si è verificato un errore nel tentativo di recapitare il mondo: " + worldName);
                    return false;
                }

                com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bukkitWorld);
                RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);

                if (regionManager == null) {
                    LOGGER.warning("Si è verificato un errore nel tentativo di recapitare il RegionManager del mondo: " + worldName);
                    return false;
                }

                if (regionManager.hasRegion(regionName)) {
                    LOGGER.info("La region esisite già: " + regionName);
                    return true;
                }

                BlockVector3 min = BlockVector3.at(minX, 0, minZ);
                BlockVector3 max = BlockVector3.at(maxX, 255, maxZ);
                ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionName, min, max);

                region.setFlag(Flags.MOB_SPAWNING, allowMobSpawning ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                region.setFlag(Flags.PVP, allowPvP ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                region.setFlag(Flags.BUILD, allowBuild ? StateFlag.State.ALLOW : StateFlag.State.DENY);

                addClanMembersToRegion(conn, region, clanId);

                regionManager.addRegion(region);

                saveRegionToDatabase(conn, clanId, worldName, regionName, minX, minZ, maxX, maxZ);
                return true;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di creare la region al clan con id: " + clanId, e);
                return false;
            }
        });
    }


    public CompletableFuture<Boolean> createClanRegionAsync(int clanId, String worldName, int centerX, int centerZ) {
        boolean defaultMobSpawning = CoralClans.get().getConfigManager().getConfig().getBoolean("Clans.default-mob-spawning", false);
        boolean defaultPvP = CoralClans.get().getConfigManager().getConfig().getBoolean("Clans.default-pvp", false);
        boolean defaultBuild = CoralClans.get().getConfigManager().getConfig().getBoolean("Clans.default-build", true);

        return createClanRegionAsync(clanId, worldName, centerX, centerZ, defaultMobSpawning, defaultPvP, defaultBuild);
    }

    public CompletableFuture<Boolean> deleteClanRegionAsync(int clanId, String worldName) {
        return DatabaseManager.get().supplyAsync(() -> {
            try (Connection conn = DatabaseManager.get().getConnection()) {
                String clanTag = getClanTag(conn, clanId);
                if (clanTag == null) {
                    return false;
                }

                String regionName = "clan_" + clanTag.toLowerCase() + "_" + clanId;

                World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld == null) {
                    LOGGER.warning("World not found: " + worldName);
                    return false;
                }

                com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bukkitWorld);
                RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);

                if (regionManager != null) {
                    if (regionManager.hasRegion(regionName)) {
                        regionManager.removeRegion(regionName);

                        try {
                            regionManager.save();
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Si è verificato un errore nel salvataggio della Land: " + regionName, e);
                            return false;
                        }
                    } else {
                        LOGGER.warning("Si è verificato un errore nel tentativo di trovare la region di worldguard: " + regionName);
                    }
                } else {
                    LOGGER.warning("Il RegionManager di WorldGuard è nullo in questo mondo: " + worldName);
                }

                String sql = "DELETE FROM clan_territories WHERE clan_id = ? AND world = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, clanId);
                    stmt.setString(2, worldName);
                    stmt.executeUpdate();
                }

                return true;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di eliminare la region del clan con id: " + clanId, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateClanRegionPermissions(int clanId, String worldName, boolean allowMobSpawning, boolean allowPvP, boolean allowBuild) {
        return DatabaseManager.get().supplyAsync(() -> {
            try (Connection conn = DatabaseManager.get().getConnection()) {
                String clanTag = getClanTag(conn, clanId);
                if (clanTag == null) {
                    return false;
                }

                String regionName = "clan_" + clanTag.toLowerCase() + "_" + clanId;

                World bukkitWorld = Bukkit.getWorld(worldName);
                if (bukkitWorld == null) {
                    return false;
                }

                com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(bukkitWorld);
                RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);

                if (regionManager == null) {
                    return false;
                }

                ProtectedRegion region = regionManager.getRegion(regionName);
                if (region == null) {
                    return false;
                }

                region.setFlag(Flags.MOB_SPAWNING, allowMobSpawning ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                region.setFlag(Flags.PVP, allowPvP ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                region.setFlag(Flags.BUILD, allowBuild ? StateFlag.State.ALLOW : StateFlag.State.DENY);
                return true;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di aggiornare i permessi della region del clan con id: " + clanId, e);
                return false;
            }
        });
    }


    public String getClanTag(Connection conn, int clanId) throws SQLException {
        String sql = "SELECT tag FROM clans WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clanId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tag");
                }
            }
        }
        return null;
    }

    private void addClanMembersToRegion(Connection conn, ProtectedRegion region, int clanId) throws SQLException {
        String sql = "SELECT player_uuid, role FROM clan_members WHERE clan_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clanId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    String role = rs.getString("role");
                    UUID uuid = UUID.fromString(playerUuid);

                    if ("LEADER".equals(role) || "OFFICER".equals(role)) {
                        region.getOwners().addPlayer(uuid);
                    } else {
                        region.getMembers().addPlayer(uuid);
                    }
                }
            }
        }
    }

    private void saveRegionToDatabase(Connection conn, int clanId, String world, String regionName,
                                      int minX, int minZ, int maxX, int maxZ) throws SQLException {
        String sql = """
            INSERT INTO clan_territories (clan_id, world, region_name, min_x, min_z, max_x, max_z)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            min_x = VALUES(min_x),
            min_z = VALUES(min_z),
            max_x = VALUES(max_x),
            max_z = VALUES(max_z)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clanId);
            stmt.setString(2, world);
            stmt.setString(3, regionName);
            stmt.setInt(4, minX);
            stmt.setInt(5, minZ);
            stmt.setInt(6, maxX);
            stmt.setInt(7, maxZ);
            stmt.executeUpdate();
        }
    }

    public Optional<ClanStructure> getClanById(int clanId) {
        String sql = "SELECT * FROM clans WHERE id = ?";

        try (Connection conn = DatabaseManager.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, clanId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(ClanStructure.fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo il clan id! ", e);
        }
        return Optional.empty();
    }

    public String getClanTagSync(String playerUuid) {
        try {
            Optional<ClanStructure> clanOpt = getPlayerClan(playerUuid).get();
            if (clanOpt.isPresent()) {
                return clanOpt.get().tag();
            }
            return "";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Si è verificato un errore nel tentativo di recapitare la tag del giocatore " + playerUuid, e);
            return "";
        }
    }

    public String getClanNameSync(String playerUuid) {
        try {
            Optional<ClanStructure> clanOpt = getPlayerClan(playerUuid).get();
            if (clanOpt.isPresent()) {
                return clanOpt.get().name();
            }
            return "";
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Si è verificato un errore nel tentativo di recapitare il nome del clan del giocatore " + playerUuid, e);
            return "";
        }
    }

    public CompletableFuture<String> getPlayerClanRole(String playerUuid) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = """
            SELECT role FROM clan_members 
            WHERE player_uuid = ?
        """;

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("role");
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Si è verificato un errore nel tentativo di recapitare il ruolo del giocatore", e);
            }
            return "";
        });
    }

    public String getPlayerClanRoleSync(String playerUuid) {
        try {
            return getPlayerClanRole(playerUuid).get();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Si è verificato un errore nel tentativo di recapitare il ruolo del giocatore" + playerUuid, e);
            return "";
        }
    }

    public CompletableFuture<Integer> getPlayerClanOnlineMembers(String playerUuid) {
        return getPlayerClan(playerUuid).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }

            return getClanMembers(clanOpt.get().id()).thenApply(members -> {
                int onlineCount = 0;
                for (ClanMemberStructure member : members) {
                    try {
                        UUID memberUuid = UUID.fromString(member.playerUuid());
                        if (Bukkit.getPlayer(memberUuid) != null) {
                            onlineCount++;
                        }
                    } catch (IllegalArgumentException e) {
                    }
                }
                return onlineCount;
            });
        });
    }

    public String getPlayerClanOnlineMembersSync(String playerUuid) {
        try {
            return String.valueOf(getPlayerClanOnlineMembers(playerUuid).get());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Si è verificato un errore nel tentativo di recapitare gli online del clan " + playerUuid, e);
            return "0";
        }
    }
}