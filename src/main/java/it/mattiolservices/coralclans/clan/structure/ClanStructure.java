package it.mattiolservices.coralclans.clan.structure;

import lombok.With;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@With
public record ClanStructure(
        int id,
        String name,
        String tag,
        String leaderUuid,
        LocalDateTime createdAt,
        String homeWorld,
        double homeX,
        double homeY,
        double homeZ
) {
    public static ClanStructure fromResultSet(ResultSet rs) throws SQLException {
        return new ClanStructure(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("tag"),
                rs.getString("leader_uuid"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("home_world"),
                rs.getDouble("home_x"),
                rs.getDouble("home_y"),
                rs.getDouble("home_z")
        );
    }
}