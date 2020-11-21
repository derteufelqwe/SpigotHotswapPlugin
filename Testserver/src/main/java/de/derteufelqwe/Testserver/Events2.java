package de.derteufelqwe.Testserver;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Events2 implements Listener {

    @EventHandler
    public void onPlayerJoinEvent(AsyncPlayerChatEvent event) {
        System.out.println("Chat message: " + event.getMessage());
    }


    @EventHandler
    public void onPlayerJn(PlayerJoinEvent event) {
        System.out.println("Second join " + event.getPlayer().getDisplayName());
    }


    public int sdswes() {
        return 0;
    }

}
