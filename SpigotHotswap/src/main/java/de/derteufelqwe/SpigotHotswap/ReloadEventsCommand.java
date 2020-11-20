package de.derteufelqwe.SpigotHotswap;

import lombok.SneakyThrows;
import org.bukkit.event.Listener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.hotswap.agent.command.Command;

/**
 * Hotswap command to reload Spigots event listeners
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

    @SneakyThrows
    @Override
    public void executeCommand() {
        this.pluginManager.registerEvents(this.listener, this.plugin);
    }
}
