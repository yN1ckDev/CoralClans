package it.mattiolservices.coralclans.clan.structure;

import lombok.With;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@With
public record ClanTerritoryStructure(
        int id,
        int clanId,
        String world,
        String regionName,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        LocalDateTime claimedAt
) {
    public static ClanTerritoryStructure fromResultSet(ResultSet rs) throws SQLException {
        return new ClanTerritoryStructure(
                rs.getInt("id"),
                rs.getInt("clan_id"),
                rs.getString("world"),
                rs.getString("region_name"),
                rs.getInt("min_x"),
                rs.getInt("min_z"),
                rs.getInt("max_x"),
                rs.getInt("max_z"),
                rs.getTimestamp("claimed_at").toLocalDateTime()
        );
    }
}