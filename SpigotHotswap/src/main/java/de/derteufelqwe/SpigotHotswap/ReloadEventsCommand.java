package de.derteufelqwe.SpigotHotswap;

import org.bukkit.event.Listener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.hotswap.agent.command.Command;

/**
 * Hotswap command to reload Spigots event listeners
 * This command is executed after the class got reloaded, so re-registering it here will register the new version
 */
public class ReloadEventsCommand implements Command {

    private SimplePluginManager pluginManager;
    private Listener listener;
    private JavaPlugin plugin;

    public ReloadEventsCommand(SimplePluginManager pluginManager, Listener listener, JavaPlugin plugin) {
        this.pluginManager = pluginManager;
        this.listener = listener;
        this.plugin = plugin;
    }


    @Override
    public void executeCommand() {
        this.pluginManager.registerEvents(this.listener, this.plugin);
        SpigotHotswapPlugin.debug("Re-added Listener: " + this.listener);
    }
}
