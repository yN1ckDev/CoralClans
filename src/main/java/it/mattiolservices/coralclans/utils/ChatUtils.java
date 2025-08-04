package it.mattiolservices.coralclans.utils;

import lombok.experimental.UtilityClass;
import org.bukkit.ChatColor;

@UtilityClass
public class ChatUtils {

    public static String translate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
