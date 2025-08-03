package it.mattiolservices.coralclans.clan.structure;

import it.mattiolservices.coralclans.clan.enums.ClanRole;
import lombok.With;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@With
public record ClanMemberStructure(
        int id,
        int clanId,
        String playerUuid,
        String playerName,
        ClanRole role,
        LocalDateTime joinedAt
) {
    public static ClanMemberStructure fromResultSet(ResultSet rs) throws SQLException {
        return new ClanMemberStructure(
                rs.getInt("id"),
                rs.getInt("clan_id"),
                rs.getString("player_uuid"),
                rs.getString("player_name"),
                ClanRole.valueOf(rs.getString("role")),
                rs.getTimestamp("joined_at").toLocalDateTime()
        );
    }
}