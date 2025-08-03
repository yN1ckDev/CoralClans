package it.mattiolservices.coralclans.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

public class ClansPlugin extends JavaPlugin {

    private CoralClans coralClans;

    @Override
    public void onEnable() {
        this.coralClans = new CoralClans(this);
        this.coralClans.init();
    }

    @Override
    public void onDisable() {
        this.coralClans.stop();
    }
}
