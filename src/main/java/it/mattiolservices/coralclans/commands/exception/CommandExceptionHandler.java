package it.mattiolservices.coralclans.commands.exception;

import org.jetbrains.annotations.NotNull;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.exception.*;
import revxrsal.commands.exception.MissingArgumentException;
import revxrsal.commands.node.ParameterNode;

import static revxrsal.commands.bukkit.util.BukkitUtils.legacyColorize;

public class CommandExceptionHandler extends BukkitExceptionHandler {

    @HandleException
    public void onInvalidPlayer(InvalidPlayerException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("&cIl giocatore &e" + e.input() + "&cnon è valido!."));
    }

    @HandleException
    public void onInvalidWorld(InvalidWorldException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("&cMondo non valido! &e" + e.input() + "&c."));
    }

    @HandleException
    public void onSenderNotConsole(SenderNotConsoleException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("&cDevi essere la Console per eseguire questo comando!"));
    }

    @HandleException
    public void onSenderNotPlayer(SenderNotPlayerException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("&cDevi essere un giocatore per poter eseguire questo comando!"));
    }

    @Override
    public void onMissingArgument(@NotNull MissingArgumentException e, @NotNull BukkitCommandActor actor, @NotNull ParameterNode<BukkitCommandActor, ?> parameter) {
        actor.error(legacyColorize("&cUn parametro richiesto è mancante: &e" + parameter.name() + "&c. Uso corretto: &e/" + parameter.command().usage() + "&c."));
    }

    @HandleException
    public void onEmptyEntitySelector(EmptyEntitySelectorException e, BukkitCommandActor actor) {
        actor.error(legacyColorize("&cNon è stata trovata nessuna entità"));
    }
}
