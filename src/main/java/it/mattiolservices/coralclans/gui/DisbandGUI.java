package it.mattiolservices.coralclans.gui;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
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

public class DisbandGUI implements Listener {


    private final ClanManager clanManager = ClanManager.get();
    private final ClanStructure clan;
    private Inventory inventory;
    private Player player;

    public DisbandGUI(ClanStructure clan) {
        this.clan = clan;
    }

    public void init(Player player) {
        this.player = player;

        this.inventory = Bukkit.createInventory(null, 27, ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Disband.gui-title")));

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

        ItemStack warningItem = createWarningItem();
        inventory.setItem(4, warningItem);

        ItemStack confirmItem = createConfirmItem();
        inventory.setItem(20, confirmItem);

        ItemStack cancelItem = createCancelItem();
        inventory.setItem(24, cancelItem);
    }

    private ItemStack createWarningItem() {
        ItemStack warningItem = new ItemStack(Material.BARRIER);
        ItemMeta warningMeta = warningItem.getItemMeta();
        if (warningMeta != null) {
            warningMeta.setDisplayName(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Disband.warning-block-title")));

            List<String> lore = CoralClans.get().getConfigManager().getMessages().getStringList("Gui.Disband.warning-block-lore")
                    .stream()
                    .map(ChatUtils::translate)
                    .collect(Collectors.toList());

            warningMeta.setLore(lore);
            warningItem.setItemMeta(warningMeta);
        }
        return warningItem;
    }

    private ItemStack createConfirmItem() {
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Disband.confirm-block-title")));

            List<String> lore = CoralClans.get().getConfigManager().getMessages().getStringList("Gui.Disband.confirm-block-lore")
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
            cancelMeta.setDisplayName(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Gui.Disband.cancel-block-title")));

            List<String> lore = CoralClans.get().getConfigManager().getMessages().getStringList("Gui.Disband.cancel-block-lore")
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

        if (clickedItem.getType() == Material.GREEN_WOOL) {
            clickedPlayer.closeInventory();

            clanManager.deleteClan(clan.id()).thenAccept(deleted -> {
                if (deleted) {
                    Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                        player.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-disbanded")));
                    });

                    clanManager.getClanMembers(clan.id()).thenAccept(members -> {
                        Bukkit.getScheduler().runTask(CoralClans.get().getPlugin(), () -> {
                            for (ClanMemberStructure member : members) {
                                Player memberPlayer = Bukkit.getPlayer(member.playerUuid());
                                if (memberPlayer != null && !memberPlayer.equals(player)) {
                                    memberPlayer.sendMessage(ChatUtils.translate(CoralClans.get().getConfigManager().getMessages().getString("Messages.clan-disbaded-members")));
                                }
                            }
                        });
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

        Player closedPlayer = (Player) event.getPlayer();

        if (event.getInventory().equals(this.inventory)) {
            cleanup(closedPlayer);
        }
    }


    private void cleanup(Player player) {
        HandlerList.unregisterAll(this);
    }

}