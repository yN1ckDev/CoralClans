package it.mattiolservices.coralclans.gui;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.clan.enums.ClanRole;
import it.mattiolservices.coralclans.clan.manager.ClanManager;
import it.mattiolservices.coralclans.clan.structure.ClanMemberStructure;
import it.mattiolservices.coralclans.clan.structure.ClanStructure;
import it.mattiolservices.coralclans.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class KickGUI implements Listener {

    private final ClanManager clanManager = ClanManager.get();
    private final ClanStructure clan;
    private final ClanMemberStructure targetMember;
    private Inventory inventory;
    private Player player;

    public KickGUI(ClanStructure clan, ClanMemberStructure targetMember) {
        this.clan = clan;
        this.targetMember = targetMember;
    }

    public void init(Player player) {
        this.player = player;

        this.inventory = Bukkit.createInventory(null, 27, ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Kick.gui-title")));

        initItems();

        Bukkit.getPluginManager().registerEvents(this, CoralClans.get().getPlugin());

        Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
            player.openInventory(inventory);
        });
    }

    public Inventory getGui() {
        return inventory;
    }

    private void initItems() {
        fillEmptySlots();

        ItemStack playerHead = createPlayerHeadItem();
        inventory.setItem(4, playerHead);

        ItemStack confirmItem = createConfirmItem();
        inventory.setItem(20, confirmItem);

        ItemStack cancelItem = createCancelItem();
        inventory.setItem(24, cancelItem);
    }

    private ItemStack createPlayerHeadItem() {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta headMeta = playerHead.getItemMeta();
        if (headMeta != null) {
            String name = CoralClans.get().getConfigManager().getMessages().getString("Gui.Kick.player-head-title").replace("%player%", targetMember.playerName());

            headMeta.setDisplayName(ChatUtils.translate(name));

            List<String> lore = CoralClans.get().getConfigManager().getMessages().getStringList("Gui.Kick.player-head-lore")
                    .stream()
                    .map(ChatUtils::translate)
                    .collect(Collectors.toList());

            headMeta.setLore(lore);
            playerHead.setItemMeta(headMeta);
        }
        return playerHead;
    }

    private ItemStack createConfirmItem() {
        ItemStack confirmItem = new ItemStack(Material.ORANGE_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Kick.confirm-block-title")));
            List<String> lore = CoralClans.get().getConfigManager().getMessages().getStringList("Gui.Kick.confirm-block-lore")
                    .stream()
                    .map(ChatUtils::translate)
                    .collect(Collectors.toList());

            confirmMeta.setLore(lore);

            confirmItem.setItemMeta(confirmMeta);
        }
        return confirmItem;
    }

    private ItemStack createCancelItem() {
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Kick.cancel-block-title")));

            List<String> lore = CoralClans.get().getConfigManager().getMessages().getStringList("Gui.Kick.cancel-block-lore")
                    .stream()
                    .map(ChatUtils::translate)
                    .collect(Collectors.toList());

            cancelMeta.setLore(lore);
            cancelItem.setItemMeta(cancelMeta);
        }
        return cancelItem;
    }

    private void fillEmptySlots() {
        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glassPane.setItemMeta(glassMeta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glassPane);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player clickedPlayer = (Player) event.getWhoClicked();

        if (!event.getInventory().equals(this.inventory)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.ORANGE_WOOL) {
            clickedPlayer.closeInventory();
            clanManager.removeMember(clan.id(), targetMember.playerUuid()).thenAccept(removed -> {
                if (removed) {
                    Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-kicked-owner").replace("%player%", targetMember.playerName())));

                        Player targetPlayer = Bukkit.getPlayer(targetMember.playerUuid());
                        if (targetPlayer != null) {
                            targetPlayer.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-kicked-member")));
                        }
                    });
                } else {
                    Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                        player.sendMessage(ChatUtils.translate("&cSi è verificato un errore nel tentativo di kickare il giocatore, contatta l'amministrazione."));
                    });
                }
            });
        }
        else if (clickedItem.getType() == Material.RED_WOOL) {
            player.closeInventory();
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        if (event.getInventory().equals(this.inventory)) {
            cleanup(player);
        }
    }

    private void cleanup(Player player) {
        HandlerList.unregisterAll(this);
    }
}