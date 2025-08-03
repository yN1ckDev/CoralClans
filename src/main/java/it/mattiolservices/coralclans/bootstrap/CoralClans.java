package it.mattiolservices.coralclans.bootstrap;

import it.mattiolservices.coralclans.config.ConfigManager;
import it.mattiolservices.coralclans.services.manager.ManagerService;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Setter
public class CoralClans {

    private static CoralClans INSTANCE;

    private JavaPlugin plugin;

    private ManagerService service;
    private ConfigManager configManager;

    public CoralClans(JavaPlugin plugin) {
        this.plugin = plugin;
        INSTANCE = this;
    }

    public void init() {
        registerConfig();
        registerService();
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

    public static CoralClans get() {
        return INSTANCE;
    }
}
