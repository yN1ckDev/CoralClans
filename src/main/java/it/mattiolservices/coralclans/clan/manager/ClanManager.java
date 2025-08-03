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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.sql.*;
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

    private Map<String, Map<Integer, ClanInviteData>> playerInvites;
    private Map<String, Boolean> playersInClanChat;

    @Getter
    private static final int INVITE_EXPIRATION_HOURS = 24;

    @Override
    public void start() {
        INSTANCE = this;

        this.playerInvites = new ConcurrentHashMap<>();
        this.playersInClanChat = new ConcurrentHashMap<>();
    }

    @Override
    public void stop() {
        playerInvites.clear();
        playersInClanChat.clear();
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
                LOGGER.log(Level.SEVERE, "Error creating clan", e);
                return Optional.empty();
            }
        });
    }

    public CompletableFuture<Boolean> deleteClan(int clanId) {
        return DatabaseManager.get().supplyAsync(() -> {
            String sql = "DELETE FROM clans WHERE id = ?";

            try (Connection conn = DatabaseManager.get().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, clanId);
                boolean deleted = stmt.executeUpdate() > 0;

                if (deleted) {
                    playerInvites.values().forEach(invites -> invites.remove(clanId));
                    playerInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
                }

                return deleted;

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error deleting clan", e);
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
                LOGGER.log(Level.SEVERE, "Error getting clan by name", e);
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
                LOGGER.log(Level.SEVERE, "Error getting player clan", e);
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
                LOGGER.log(Level.SEVERE, "Error setting clan home", e);
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
                    Map<Integer, ClanInviteData> invites = playerInvites.get(playerUuid);
                    if (invites != null) {
                        invites.remove(clanId);
                        if (invites.isEmpty()) {
                            playerInvites.remove(playerUuid);
                        }
                    }
                }

                return added;

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error adding member", e);
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
                    playersInClanChat.remove(playerUuid);
                }

                return removed;

            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error removing member", e);
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
                LOGGER.log(Level.SEVERE, "Error getting clan members", e);
            }
            return members;
        });
    }

    public boolean createInvite(int clanId, String invitedUuid, String invitedName, String inviterUuid) {
        try {
            ClanInviteData invite = new ClanInviteData(clanId, invitedName, inviterUuid);
            playerInvites.computeIfAbsent(invitedUuid, k -> new ConcurrentHashMap<>())
                    .put(clanId, invite);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error creating invite", e);
            return false;
        }
    }

    public boolean deleteInvite(int clanId, String invitedUuid) {
        try {
            Map<Integer, ClanInviteData> invites = playerInvites.get(invitedUuid);
            if (invites != null) {
                boolean removed = invites.remove(clanId) != null;
                if (invites.isEmpty()) {
                    playerInvites.remove(invitedUuid);
                }
                return removed;
            }
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting invite", e);
            return false;
        }
    }

    public List<ClanInviteData> getPlayerInvites(String playerUuid) {
        try {
            Map<Integer, ClanInviteData> invites = playerInvites.get(playerUuid);
            if (invites == null) {
                return new ArrayList<>();
            }

            List<ClanInviteData> validInvites = invites.values().stream()
                    .filter(invite -> !invite.isExpired())
                    .collect(Collectors.toList());

            invites.entrySet().removeIf(entry -> entry.getValue().isExpired());
            if (invites.isEmpty()) {
                playerInvites.remove(playerUuid);
            }

            return validInvites;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting player invites", e);
            return new ArrayList<>();
        }
    }

    public boolean hasInvite(String playerUuid, int clanId) {
        Map<Integer, ClanInviteData> invites = playerInvites.get(playerUuid);
        if (invites == null) return false;

        ClanInviteData invite = invites.get(clanId);
        if (invite == null) return false;

        if (invite.isExpired()) {
            invites.remove(clanId);
            if (invites.isEmpty()) {
                playerInvites.remove(playerUuid);
            }
            return false;
        }

        return true;
    }

    public boolean toggleClanChat(String playerUuid) {
        return playersInClanChat.compute(playerUuid, (key, current) ->
                current == null || !current
        );
    }

    public boolean isInClanChat(String playerUuid) {
        return playersInClanChat.getOrDefault(playerUuid, false);
    }

    public void setClanChatMode(String playerUuid, boolean enabled) {
        if (enabled) {
            playersInClanChat.put(playerUuid, true);
        } else {
            playersInClanChat.remove(playerUuid);
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

    public void cleanupExpiredInvites() {
        playerInvites.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(inviteEntry -> inviteEntry.getValue().isExpired());
            return entry.getValue().isEmpty();
        });
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
                    LOGGER.warning("Could not find clan with ID: " + clanId);
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

                if (regionManager == null) {
                    LOGGER.warning("Could not get RegionManager for world: " + worldName);
                    return false;
                }

                if (regionManager.hasRegion(regionName)) {
                    LOGGER.info("Region already exists: " + regionName);
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

                LOGGER.info("Successfully created region: " + regionName + " for clan ID: " + clanId);
                return true;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error creating clan region for clan ID: " + clanId, e);
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
                    regionManager.removeRegion(regionName);
                }

                String sql = "DELETE FROM clan_territories WHERE clan_id = ? AND world = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, clanId);
                    stmt.setString(2, worldName);
                    stmt.executeUpdate();
                }

                LOGGER.info("Successfully deleted region: " + regionName);
                return true;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error deleting clan region for clan ID: " + clanId, e);
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

                LOGGER.info("Updated permissions for region: " + regionName);
                return true;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error updating clan region permissions for clan ID: " + clanId, e);
                return false;
            }
        });
    }


    private String getClanTag(Connection conn, int clanId) throws SQLException {
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

    private Optional<ClanStructure> getClanById(int clanId) {
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
            LOGGER.log(Level.SEVERE, "Error getting clan by ID", e);
        }
        return Optional.empty();
    }
}