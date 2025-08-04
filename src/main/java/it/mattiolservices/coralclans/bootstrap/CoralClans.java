package it.mattiolservices.coralclans.bootstrap;

import it.mattiolservices.coralclans.config.ConfigManager;
import it.mattiolservices.coralclans.listener.ClanChatListener;
import it.mattiolservices.coralclans.services.manager.ManagerService;
import it.mattiolservices.coralclans.services.papi.PlaceholderService;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

@Getter
@Setter
public class CoralClans {

    private static CoralClans INSTANCE;

    private JavaPlugin plugin;

    private ManagerService service;
    private ConfigManager configManager;
    private PlaceholderService placeholderService;

    public CoralClans(JavaPlugin plugin) {
        this.plugin = plugin;
        INSTANCE = this;
    }

    public void init() {
        registerConfig();
        registerService();
        registerListener();
        registerPlaceholders();
    }

    public void stop() {
        INSTANCE = null;
        this.service.shutdown();
    }

    private void registerService() {
        this.service = new ManagerService();
        this.service.init();
    }

    private void registerConfig() {
        this.configManager = new ConfigManager();
        this.configManager.start();
    }

    private void registerListener() {
        List.of(
                new ClanChatListener()
        ).forEach(listener -> getPlugin().getServer().getPluginManager().registerEvents(listener, getPlugin()));
    }

    private void registerPlaceholders() {
        this.placeholderService = new PlaceholderService();
        this.placeholderService.register();
    }

    public static CoralClans get() {
        return INSTANCE;
    }
}
