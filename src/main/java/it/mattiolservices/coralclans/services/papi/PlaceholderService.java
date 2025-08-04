package it.mattiolservices.coralclans.services.papi;

import it.mattiolservices.coralclans.clan.manager.ClanManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderService extends PlaceholderExpansion {

    @NotNull
    @Override
    public String getIdentifier() {
        return "clans";
    }

    @NotNull
    @Override
    public String getAuthor() {
        return "Mattiol";
    }

    @NotNull
    @Override
    public String getVersion() {
        return "1.0";
    }

    public String onPlaceholderRequest(Player player, @NotNull String params) {
        switch (params) {
            case "player_clan":
                return ClanManager.get().getClanNameSync(player.getUniqueId().toString());

            case "player_tag":
                return ClanManager.get().getClanTagSync(player.getUniqueId().toString());

            case "player_role":
                return ClanManager.get().getPlayerClanRoleSync(player.getUniqueId().toString());

            case "clan_online_members":
                return ClanManager.get().getPlayerClanOnlineMembersSync(player.getUniqueId().toString());

            default:
                return null;
        }
    }
}