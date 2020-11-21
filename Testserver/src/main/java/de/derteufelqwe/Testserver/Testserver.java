package de.derteufelqwe.Testserver;

import org.bukkit.plugin.java.JavaPlugin;

public class Testserver extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new Events1(), this);
        getServer().getPluginManager().registerEvents(new Events2(), this);
    }

    @Override
    public void onDisable() {

    }

}
