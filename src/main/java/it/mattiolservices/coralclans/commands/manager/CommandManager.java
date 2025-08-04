package it.mattiolservices.coralclans.commands.manager;

import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.commands.ClanCMD;
import it.mattiolservices.coralclans.commands.exception.CommandExceptionHandler;
import it.mattiolservices.coralclans.services.manager.Manager;
import revxrsal.commands.bukkit.BukkitLamp;

public class CommandManager implements Manager {

    private static CommandManager INSTANCE;

    @Override
    public void start() {
        INSTANCE = this;

        var lamp = BukkitLamp.builder(CoralClans.get().getPlugin())
                .exceptionHandler(new CommandExceptionHandler())
                .build();

        lamp.register(new ClanCMD());
    }

    @Override
    public void stop() {
        INSTANCE = null;
    }
}
