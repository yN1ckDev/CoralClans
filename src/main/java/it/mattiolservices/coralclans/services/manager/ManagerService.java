package it.mattiolservices.coralclans.services.manager;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.clan.manager.ClanManager;
import it.mattiolservices.coralclans.config.ConfigManager;
import it.mattiolservices.coralclans.storage.DatabaseManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ManagerService {

    private static ManagerService INSTANCE;

    private List<Manager> managers;

    public ManagerService(){
        INSTANCE = this;
    }

    public void init(){
        this.managers = new ArrayList<>();
        registerManager();
    }

    public void registerManager(){
        CoralClans plugin = CoralClans.get();

        addManager(new DatabaseManager());
        addManager(new ClanManager());
        addManager(new ConfigManager());

        managers.forEach(Manager::start);
    }

    public void shutdown(){
        managers.forEach(Manager::stop);
        managers.clear();
        INSTANCE = null;
    }


    private void addManager(Manager manager){
        if (managers.contains(manager)) return;
        managers.add(manager);
    }

    public static ManagerService get(){
        return INSTANCE;
    }
}
