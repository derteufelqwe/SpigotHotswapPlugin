package de.derteufelqwe.Testserver;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Events1 implements Listener {



    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        System.out.println("[P] Player " + event.getPlayer().getDisplayName() + " joined.");
    }

    @EventHandler
    public void ssssf(PlayerQuitEvent event) {
        System.out.println("[P] Player " + event.getPlayer().getDisplayName() + " lieaft.");
    }

    public int sdsdfwewess() {
        return 0;
    }

}
