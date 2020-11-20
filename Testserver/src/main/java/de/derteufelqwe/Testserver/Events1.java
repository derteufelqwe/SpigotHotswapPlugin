package de.derteufelqwe.Testserver;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class Events1 implements Listener {

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        System.out.println("[P] Player " + event.getPlayer().getDisplayName() + " joined.");
    }


    public void asssfdf() {

    }

}
