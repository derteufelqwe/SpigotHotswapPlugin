package de.derteufelqwe.SpigotHotswap;

import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name = "SpigotHotswapPlugin", testedVersions = "1.12.2 upwards")
public class SpigotHotswapPlugin {

    private static Logger LOGGER = Logger.getLogger("org.hotswap.agent.plugin");

    private static boolean enabled = true;
    private static Properties properties;
    public static final String PLUGIN_PACKAGE = "*.*";
    private static Pattern RE_CLASS;
    private static String pluginName = "SpigotHotswapTestPlugin";


    @Init
    static ClassLoader appClassLoader;

    @Init
    static Scheduler scheduler;

    @Init
    static PluginConfiguration pluginConfiguration;


    @SneakyThrows
    @OnClassLoadEvent(classNameRegexp = PLUGIN_PACKAGE, events = LoadEvent.REDEFINE)
    public static void onClassChange(ClassLoader cLoader, ClassPool classPool, CtClass ctClass, String classname) {
        if (!enabled) {
            return;
        }
        System.err.println("Hallo Welt");
        if (properties == null) {
            properties = getProperties(cLoader);
            if (properties == null) {
                enabled = false;
                System.out.println("[SHP] SpigotHotswap.properties file missing.");

            } else {
                RE_CLASS = Pattern.compile(properties.getProperty("packageName", ""));
            }
        }

        if (RE_CLASS == null) {
            return;

        } else {
            Matcher m = RE_CLASS.matcher(classname);
            if (!m.matches()) {
                return;
            }
        }

        System.out.println("Load class " + classname);
        LOGGER.log(Level.WARNING, "logger - Load class");

        if (isListenerSubclass(ctClass)) {
            onEventListenerChange(ctClass, cLoader, classname.replace("/", "."));
        }

        System.out.println("Properties: " + getProperties(cLoader));
    }


    @SneakyThrows
    private static boolean isListenerSubclass(CtClass ctClass) {
        for (CtClass interfaceClazz : ctClass.getInterfaces()) {
            if (interfaceClazz.getName().equals(Listener.class.getName())) {
                return true;
            }
        }

        CtClass superClazz = ctClass.getSuperclass();
        if (!superClazz.getName().equals(Object.class.getName())) {
            return isListenerSubclass(superClazz);
        }

        return false;
    }


    @SneakyThrows
    private static void onEventListenerChange(CtClass ctClass, ClassLoader cLoader, String className) {
        System.out.println("Reloaded Listener class " + className);

        Field f = JavaPlugin.class.getDeclaredField("loader");
        f.setAccessible(true);

        SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

        Method getEventListeners = SimplePluginManager.class.getDeclaredMethod("getEventListeners", Class.class);
        getEventListeners.setAccessible(true);

        Method getRegistrationClass = SimplePluginManager.class.getDeclaredMethod("getRegistrationClass", Class.class);
        getRegistrationClass.setAccessible(true);

        Class<? extends Listener> clazz = (Class<? extends Listener>) cLoader.loadClass(className);

        // Gather data

        Set<Class<? extends Event>> usedEvents = getUsedEvents(clazz, cLoader);
        System.out.println(usedEvents);

        JavaPlugin javaPlugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);

        // Unregister Events

        Set<Listener> removed = analyzeHandlers(getEventListeners, pluginManager, usedEvents, clazz);
        System.out.println("Removed: " + removed);

        // Re-Register events

        if (removed.size() > 0) {
            scheduler.scheduleCommand(new ReloadEventsCommand(pluginManager, removed.stream().iterator().next(), javaPlugin));

        }
    }



    @SneakyThrows
    private static Set<Listener> analyzeHandlers(Method getEventListeners, SimplePluginManager pluginManager, Set<Class<? extends Event>> usedEvents, Class<? extends Listener> oldClazz) {
        Set<Listener> removedListeners = new HashSet<>();
        JavaPlugin javaPlugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);

        for (Class<? extends Event> e : usedEvents) {
            HandlerList handlerList = (HandlerList) getEventListeners.invoke(pluginManager, e);

            Method getPlugin = RegisteredListener.class.getDeclaredMethod("getPlugin");
            getPlugin.setAccessible(true);

            Method getListener = RegisteredListener.class.getDeclaredMethod("getListener");
            getListener.setAccessible(true);


            for (RegisteredListener l : handlerList.getRegisteredListeners()) {
                JavaPlugin plugin = (JavaPlugin) getPlugin.invoke(l);
                Listener listener = (Listener) getListener.invoke(l);

                if (!plugin.equals(javaPlugin)) {
                    continue;
                }

                if (!listener.getClass().getName().equals(oldClazz.getName())) {
                    continue;
                }


                handlerList.unregister(l);
                System.out.println("[HSP] Unregistered " + e + " in " + listener.toString());

                removedListeners.add(listener);
            }


            continue;
        }

        return removedListeners;
    }


    /**
     * Scans a class for @{@link EventHandler} methods and extracts the Used events
     */
    private static Set<Class<? extends Event>> getUsedEvents(Class<? extends Listener> clazz, ClassLoader cLoader) {
        Set<Class<? extends Event>> events = new HashSet<>();

        for (Method m : clazz.getDeclaredMethods()) {
            EventHandler eventHandler = m.getAnnotation(EventHandler.class);
            if (eventHandler == null) {
                continue;
            }

            Class<?>[] types = m.getParameterTypes();

            if (types.length == 1) {
                Class<?> type = types[0];
                if (isEventClass(type)) {
                    try {
                        events.add((Class<? extends Event>) cLoader.loadClass(type.getName()));

                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }

            } else {
                System.err.println("Method " + m + " has invalid parameter count.");
            }

        }

        return events;
    }


    /**
     * Checks if a class is a subtype of {@link Event}
     */
    @SneakyThrows
    private static boolean isEventClass(Class<?> clazz) {
        Class<?> superClazz = clazz.getSuperclass();

        if (superClazz.equals(Event.class)) {
            return true;

        } else if (!superClazz.equals(Object.class)) {
            return isEventClass(superClazz);
        }

        return false;
    }


    private static Properties getProperties(ClassLoader classLoader) {
        InputStream inputStream = classLoader.getResourceAsStream("SpigotHotswap.properties");

        Properties properties = new Properties();
        try {
            properties.load(inputStream);

        } catch (IOException e) {
            return null;
        }

        return properties;
    }

}
