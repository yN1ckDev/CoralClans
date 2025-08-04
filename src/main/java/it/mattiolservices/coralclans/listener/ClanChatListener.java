package it.mattiolservices.coralclans.listener;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.clan.manager.ClanManager;
import it.mattiolservices.coralclans.clan.structure.ClanMemberStructure;
import it.mattiolservices.coralclans.clan.structure.ClanStructure;
import it.mattiolservices.coralclans.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ClanChatListener implements Listener {

    private final ClanManager clanManager = ClanManager.get();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerUuid = player.getUniqueId().toString();

        if (!clanManager.isInClanChat(playerUuid)) {
            return;
        }

        event.setCancelled(true);

        sendClanMessage(player, event.getMessage());
    }

    private void sendClanMessage(Player player, String message) {
        clanManager.getPlayerClan(player.getUniqueId().toString())
                .thenAccept(clanOpt -> {

                    if (clanOpt.isEmpty()) {
                        Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                            player.sendMessage(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan"));
                        });
                        return;
                    }

                    ClanStructure clan = clanOpt.get();

                    clanManager.getClanMembers(clan.id())
                            .thenAccept(members -> {

                                Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                                    int sentCount = 0;
                                    for (ClanMemberStructure member : members) {

                                        if (!clanManager.isInClanChat(member.playerUuid())) {
                                            continue;
                                        }

                                        Player memberPlayer = null;

                                        try {
                                            memberPlayer = Bukkit.getPlayer(java.util.UUID.fromString(member.playerUuid()));
                                        } catch (Exception e) {
                                        }

                                        if (memberPlayer != null && memberPlayer.isOnline()) {
                                            memberPlayer.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-chat-message").replace("%player%", player.getName()).replace("%message%", message)));
                                            sentCount++;
                                        }
                                    }
                                });
                            })
                            .exceptionally(ex -> {
                                ex.printStackTrace();
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }
}