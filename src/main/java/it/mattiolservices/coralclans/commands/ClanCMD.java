package it.mattiolservices.coralclans.commands;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.clan.enums.ClanRole;
import it.mattiolservices.coralclans.clan.manager.ClanManager;
import it.mattiolservices.coralclans.clan.structure.ClanMemberStructure;
import it.mattiolservices.coralclans.clan.structure.ClanStructure;
import it.mattiolservices.coralclans.gui.KickGUI;
import it.mattiolservices.coralclans.gui.DisbandGUI;
import it.mattiolservices.coralclans.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.*;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Command("clan")
@CommandPermission("coralclans.clan")
public class ClanCMD {

    private final ClanManager clanManager = ClanManager.get();

    @Subcommand("create")
    @CommandPermission("coralclans.clan.create")
    public void createClan(Player player, String name, String tag) {
        clanManager.isPlayerInClan(player.getUniqueId().toString()).thenAccept(isInClan -> {
            if (isInClan) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-in-clan")));
                return;
            }

            if (name.length() < 3 || name.length() > 16) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.name-format")));
                return;
            }

            if (tag.length() < 2 || tag.length() > 3) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.tag-format")));
                return;
            }

            clanManager.getClanByName(name).thenAccept(existingClan -> {
                if (existingClan.isPresent()) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-already-exist")));
                    return;
                }
                clanManager.createClan(name, tag, player.getUniqueId().toString(), player.getName())
                        .thenAccept(createdClan -> {
                            if (createdClan.isPresent()) {
                                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-created").replace("%name%", name).replace("%tag%", tag)));
                            } else {
                                player.sendMessage(ChatUtils.translate("&cSi è verificato un errore durante la creazione del clan, contatta l'Amministrazione"));
                            }
                        });
            });
        });
    }

    @Subcommand("reload")
    @CommandPermission("coralclans.admin")
    public void reloadPlugin(Player player) throws IOException {
        CoralClans.get().getConfigManager().reload();
        player.sendMessage(ChatUtils.translate("&aConfigurazione ricaricata!"));
    }

    @Subcommand("disband")
    @CommandPermission("coralclans.clan.disband")
    public void disbandClan(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            if (!clan.leaderUuid().equals(player.getUniqueId().toString())) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.leader-only")));
                return;
            }

            DisbandGUI gui = new DisbandGUI(clan);
            gui.init(player);
        });
    }

    @Subcommand("invite")
    @CommandPermission("coralclans.clan.invite")
    public void invitePlayer(Player player, @Named("player") Player target) {
        if (target.equals(player)) {
            player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.self-invite")));
            return;
        }

        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            clanManager.canPlayerManageClan(player.getUniqueId().toString(), clan.id()).thenAccept(canManage -> {
                if (!canManage) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-permission-clan")));
                    return;
                }

                clanManager.isPlayerInClan(target.getUniqueId().toString()).thenAccept(targetInClan -> {
                    if (targetInClan) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-in-clan-other").replace("%player%", player.getName())));
                        return;
                    }

                    if (clanManager.hasInvite(target.getUniqueId().toString(), clan.id())) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-invite")));
                        return;
                    }

                    boolean invited = clanManager.createInvite(
                            clan.id(),
                            target.getUniqueId().toString(),
                            target.getName(),
                            player.getUniqueId().toString()
                    );

                    if (invited) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-invite").replace("%player%", target.getName())));

                        List<String> inviteMessages = CoralClans.get().getConfigManager().getMessages().getStringList("Messages.clan-invite-other");
                        for (String line : inviteMessages) {
                            target.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    line.replace("%clan%", clan.name())
                                            .replace("%sender%", player.getName())
                                            .replace("%target%", target.getName())
                            ));
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Si è verificato un errore durante l'invio dell'invito.");
                    }
                });
            });
        });
    }


    @Subcommand("join")
    @CommandPermission("coralclans.clan.join")
    public void joinClan(Player player, String clanName) {
        clanManager.isPlayerInClan(player.getUniqueId().toString()).thenAccept(isInClan -> {
            if (isInClan) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-in-clan")));
                return;
            }

            clanManager.getClanByName(clanName).thenAccept(clanOpt -> {
                if (clanOpt.isEmpty()) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-not-found")));
                    return;
                }

                ClanStructure clan = clanOpt.get();

                if (!clanManager.hasInvite(player.getUniqueId().toString(), clan.id())) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-invite")));
                    return;
                }

                clanManager.addMember(clan.id(), player.getUniqueId().toString(),
                                player.getName(), ClanRole.MEMBER)
                        .thenCompose(added -> {
                            if (added) {
                                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-joined").replace("%name%", clanName)));
                                return clanManager.getClanMembers(clan.id());
                            } else {
                                player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                                return CompletableFuture.completedFuture(Collections.emptyList());
                            }
                        })
                        .thenAccept(members -> {
                            for (ClanMemberStructure member : members) {
                                Player memberPlayer = Bukkit.getPlayer(member.playerUuid());
                                if (memberPlayer != null && !memberPlayer.equals(player)) {
                                    memberPlayer.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-joined-other").replace("%player%", player.getName())));
                                }
                            }
                        });
            });
        });
    }

    @Subcommand("kick")
    @CommandPermission("coralclans.clan.kick")
    public void kickPlayer(Player player, String targetName) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();
            clanManager.canPlayerManageClan(player.getUniqueId().toString(), clan.id()).thenAccept(canManage -> {
                if (!canManage) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-permission-clan")));
                    return;
                }

                clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                    Optional<ClanMemberStructure> targetMember = members.stream()
                            .filter(member -> member.playerName().equalsIgnoreCase(targetName))
                            .findFirst();

                    if (targetMember.isEmpty()) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.not-found-in-clan")));
                        return;
                    }

                    ClanMemberStructure target = targetMember.get();

                    if (target.playerUuid().equals(player.getUniqueId().toString())) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.self-kick")));
                        return;
                    }

                    if (target.role() == ClanRole.LEADER) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.not-kick-leader")));
                        return;
                    }

                    KickGUI gui = new KickGUI(clan, target);
                    gui.init(player);
                });
            });
        });
    }

    @Subcommand("leave")
    @CommandPermission("coralclans.clan.leave")
    public void leaveClan(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            if (clan.leaderUuid().equals(player.getUniqueId().toString())) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-transfer")));
                return;
            }

            clanManager.removeMember(clan.id(), player.getUniqueId().toString()).thenAccept(left -> {
                if (left) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-leaved")));

                    clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                        for (ClanMemberStructure member : members) {
                            Player memberPlayer = Bukkit.getPlayer(member.playerUuid());
                            if (memberPlayer != null) {
                                memberPlayer.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-transfer-other")));
                            }
                        }
                    });
                } else {
                    player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                }
            });
        });
    }

    @Subcommand("promote")
    @CommandPermission("coralclans.clan.promote")
    public void promotePlayer(Player player, @Named("player") String targetName) {
        manageMemberRole(player, targetName, true);
    }

    @Subcommand("demote")
    @CommandPermission("coralclans.clan.demote")
    public void demotePlayer(Player player, @Named("player") String targetName) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            if (!clan.leaderUuid().equals(player.getUniqueId().toString())) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.leader-only")));
                return;
            }

            clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                Optional<ClanMemberStructure> targetMember = members.stream()
                        .filter(member -> member.playerName().equalsIgnoreCase(targetName))
                        .findFirst();

                if (targetMember.isEmpty()) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.not-found-in-clan")));
                    return;
                }

                ClanMemberStructure target = targetMember.get();

                if (target.playerUuid().equals(player.getUniqueId().toString())) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.own-role")));
                    return;
                }

                if (target.role() != ClanRole.OFFICER) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-member")));
                    return;
                }

                manageMemberRole(player, targetName, false);
            });
        });
    }

    private void manageMemberRole(Player player, String targetName, boolean promote) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            if (!clan.leaderUuid().equals(player.getUniqueId().toString())) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.leader-only")));
                return;
            }

            clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                Optional<ClanMemberStructure> targetMember = members.stream()
                        .filter(member -> member.playerName().equalsIgnoreCase(targetName))
                        .findFirst();

                if (targetMember.isEmpty()) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.not-found-in-clan")));
                    return;
                }

                ClanMemberStructure target = targetMember.get();

                if (target.playerUuid().equals(player.getUniqueId().toString())) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.own-role")));
                    return;
                }

                ClanRole newRole;
                String action;

                if (promote) {
                    if (target.role() == ClanRole.MEMBER) {
                        newRole = ClanRole.OFFICER;
                        action = ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.officer"));
                    } else {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-officer")));
                        return;
                    }
                } else {
                    if (target.role() == ClanRole.OFFICER) {
                        newRole = ClanRole.MEMBER;
                        action = ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.member"));
                    } else {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.already-member")));
                        return;
                    }
                }

                clanManager.removeMember(clan.id(), target.playerUuid()).thenAccept(removed -> {
                    if (removed) {
                        clanManager.addMember(clan.id(), target.playerUuid(), target.playerName(), newRole)
                                .thenAccept(added -> {
                                    if (added) {
                                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.promoted").replace("%player%", target.playerName()).replace("%action%", action)));

                                        Player targetPlayer = Bukkit.getPlayer(target.playerUuid());
                                        if (targetPlayer != null) {
                                            targetPlayer.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.promoted-self").replace("%action%", action)));
                                        }
                                    } else {
                                        player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                                    }
                                });
                    }
                });
            });
        });
    }

    @Subcommand("chat")
    @CommandPermission("coralclans.clan.chat")
    public void clanChat(Player player, @revxrsal.commands.annotation.Optional String message) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            if (message == null || message.isEmpty()) {
                boolean newMode = clanManager.toggleClanChat(player.getUniqueId().toString());
                if (newMode) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-chat-enabled")));
                } else {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-chat-disabled")));
                }
            }
        });
    }

    @Subcommand("claim")
    @CommandPermission("coralclans.clan.claim")
    public void claimTerritory(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            clanManager.canPlayerManageClan(player.getUniqueId().toString(), clan.id()).thenAccept(canManage -> {
                if (!canManage) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.leader-only")));
                    return;
                }

                Location loc = player.getLocation();
                int centerX = loc.getBlockX();
                int centerZ = loc.getBlockZ();
                String worldName = loc.getWorld().getName();

                clanManager.createClanRegionAsync(clan.id(), worldName, centerX, centerZ).thenAccept(created -> {
                    if (created) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.region-claimed").replace("%center_x%", String.valueOf(centerX)).replace("%center_y%", String.valueOf(centerX)).replace("%world%", worldName)));
                    } else {
                        player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                    }
                });
            });
        });
    }

    @Subcommand("unclaim")
    @CommandPermission("coralclans.clan.unclaim")
    public void unclaimTerritory(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            clanManager.canPlayerManageClan(player.getUniqueId().toString(), clan.id()).thenAccept(canManage -> {
                if (!canManage) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.leader-only")));
                    return;
                }

                String worldName = player.getWorld().getName();

                clanManager.deleteClanRegionAsync(clan.id(), worldName).thenAccept(deleted -> {
                    if (deleted) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.unclaimed")));
                    } else {
                        player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                    }
                });
            });
        });
    }

    @Subcommand("home")
    @CommandPermission("coralclans.clan.home")
    public void clanHome(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            if (clan.homeWorld() == null) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.home-not-set")));
                return;
            }

            Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                Location homeLocation = new Location(
                        Bukkit.getWorld(clan.homeWorld()),
                        clan.homeX(),
                        clan.homeY(),
                        clan.homeZ()
                );

                if (homeLocation.getWorld() == null) {
                    player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                    return;
                }

                player.teleport(homeLocation);
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.home-tp")));
            });
        });
    }

    @Subcommand("sethome")
    @CommandPermission("coralclans.clan.sethome")
    public void setClanHome(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                return;
            }

            ClanStructure clan = clanOpt.get();

            clanManager.canPlayerManageClan(player.getUniqueId().toString(), clan.id()).thenAccept(canManage -> {
                if (!canManage) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.leader-only")));
                    return;
                }

                Location loc = player.getLocation();

                clanManager.setClanHome(clan.id(), loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ()).thenAccept(set -> {
                    if (set) {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.home-set")));
                    } else {
                        player.sendMessage(ChatUtils.translate("&cSi è verificato un errore, contatta l'amministrazione"));
                    }
                });
            });
        });
    }

    @Subcommand("info")
    @CommandPermission("coralclans.clan.info")
    public void clanInfo(Player player, @revxrsal.commands.annotation.Optional String clanName) {
        CompletableFuture<Optional<ClanStructure>> clanFuture;

        if (clanName == null) {
            clanFuture = clanManager.getPlayerClan(player.getUniqueId().toString());
        } else {
            clanFuture = clanManager.getClanByName(clanName);
        }

        clanFuture.thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                if (clanName == null) {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan")));
                } else {
                    player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-not-found")));
                }
                return;
            }

            ClanStructure clan = clanOpt.get();

            clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                List<String> messages = CoralClans.get().getConfigManager().getMessages().getStringList("Messages.clan-info");

                if (messages.isEmpty()) {
                    player.sendMessage(ChatUtils.translate("&cClan info non settata!"));
                    return;
                }

                Optional<ClanMemberStructure> leader = members.stream()
                        .filter(member -> member.role() == ClanRole.LEADER)
                        .findFirst();

                long officerCount = members.stream()
                        .filter(member -> member.role() == ClanRole.OFFICER)
                        .count();

                for (String line : messages) {
                    String parsed = ChatColor.translateAlternateColorCodes('&', line)
                            .replace("%name%", clan.name())
                            .replace("%tag%", clan.tag())
                            .replace("%created_at%", clan.getFormattedCreatedAt())
                            .replace("%members%", String.valueOf(members.size()));

                    if (line.contains("%leader%")) {
                        parsed = parsed.replace("%leader%", leader.map(ClanMemberStructure::playerName).orElse("N/A"));
                    }

                    if (line.contains("%officer_count%")) {
                        parsed = parsed.replace("%officer_count%", String.valueOf(officerCount));
                    }

                    if (line.contains("%x%") || line.contains("%y%") || line.contains("%z%") || line.contains("%world%")) {
                        if (clan.homeWorld() != null) {
                            parsed = parsed
                                    .replace("%x%", String.format("%.1f", clan.homeX()))
                                    .replace("%y%", String.format("%.1f", clan.homeY()))
                                    .replace("%z%", String.format("%.1f", clan.homeZ()))
                                    .replace("%world%", clan.homeWorld());
                        } else {
                            parsed = parsed
                                    .replace("%x%", "N/A")
                                    .replace("%y%", "N/A")
                                    .replace("%z%", "N/A")
                                    .replace("%world%", "Not Set");
                        }
                    }
                    player.sendMessage(parsed);
                }
            });
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    @Subcommand("list")
    @CommandPermission("coralclans.clan.list")
    public void listMembers(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                String noClan = CoralClans.get().getConfigManager().getMessages().getString("Messages.no-clan");
                player.sendMessage(ChatUtils.translate(noClan));
                return;
            }

            ClanStructure clan = clanOpt.get();
            clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                List<String> configLines = CoralClans.get().getConfigManager().getMessages().getStringList("Messages.clan-list");

                StringBuilder membersBuilder = new StringBuilder();
                for (ClanMemberStructure member : members) {
                    ChatColor roleColor = switch (member.role()) {
                        case LEADER -> ChatColor.RED;
                        case OFFICER -> ChatColor.YELLOW;
                        case MEMBER -> ChatColor.WHITE;
                    };

                    boolean isOnline = Bukkit.getPlayer(member.playerUuid()) != null;
                    String status = isOnline ? ChatColor.GREEN + "Online" : ChatColor.GRAY + "Offline";

                    membersBuilder.append(roleColor)
                            .append(member.playerName())
                            .append(ChatColor.GRAY).append(" [")
                            .append(member.role().name())
                            .append("] ").append(status)
                            .append("\n");
                }

                String membersList = membersBuilder.toString().trim();

                for (String line : configLines) {
                    String formatted = ChatColor.translateAlternateColorCodes('&', line)
                            .replace("%members_list%", membersList)
                            .replace("%total_members%", String.valueOf(members.size()));
                    for (String part : formatted.split("\n")) {
                        player.sendMessage(part);
                    }
                }
            });
        });
    }

    public void defaultClan(Player player) {
        clanManager.getPlayerClan(player.getUniqueId().toString()).thenAccept(clanOpt -> {
            if (clanOpt.isPresent()) {
                clanInfo(player, null);
            } else {
                player.sendMessage(ChatUtils.translate("&bCoralMC Clans"));
                player.sendMessage(ChatUtils.translate("&bMade with ♥ by Mattiol"));
            }
        });
    }


}