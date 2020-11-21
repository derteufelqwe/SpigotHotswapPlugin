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
import org.hotswap.agent.javassist.NotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name = "SpigotHotswapPlugin", testedVersions = "1.12.2 upwards")
public class SpigotHotswapPlugin {

    public static Logger LOGGER = getLogger();
    private static Properties properties;
    private static final String PLUGIN_PACKAGE = "*.*";
    private static Pattern RE_CLASS;
    private static String pluginName = "";
    private static JavaPlugin javaPlugin;
    private static Map<Class<? extends Listener>, Listener> listenerMap = new HashMap<>();


    @Init
    static ClassLoader appClassLoader;

    @Init
    static Scheduler scheduler;

    @Init
    static PluginConfiguration pluginConfiguration;


    @SneakyThrows
    @OnClassLoadEvent(classNameRegexp = PLUGIN_PACKAGE, events = LoadEvent.REDEFINE)
    public static void onClassChange(ClassLoader cLoader, ClassPool classPool, CtClass ctClass, String classname) {
        if (!initData(cLoader, classname)) {
            return;
        }


        info("Updating class " + classname);

        // Process Listener classes
        if (isListenerSubclass(ctClass)) {
            onEventListenerChange(ctClass, cLoader, classname.replace("/", "."));
        }

    }


    // -----  Server modification methods  -----


    /**
     * Method responsible for parsing eventListeners
     *
     * @param ctClass   CtClass that was changed
     * @param cLoader   ClassLoader of the class that was updated
     * @param className Name of the changed class
     */
    @SneakyThrows
    private static void onEventListenerChange(CtClass ctClass, ClassLoader cLoader, String className) {
        info("Reloaded Listener class " + className);

        SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

        Method getEventListeners = SimplePluginManager.class.getDeclaredMethod("getEventListeners", Class.class);
        getEventListeners.setAccessible(true);

        Method getRegistrationClass = SimplePluginManager.class.getDeclaredMethod("getRegistrationClass", Class.class);
        getRegistrationClass.setAccessible(true);

        Class<? extends Listener> clazz = (Class<? extends Listener>) cLoader.loadClass(className);

        // -- Gather data --

        Set<Class<? extends Event>> usedEvents = getUsedEvents(clazz, cLoader);
        debug("Used events: " + usedEvents);

        // -- Unregister Events --

        Listener removedListener = analyzeHandlers(getEventListeners, pluginManager, usedEvents, clazz);
        debug("Removed Listener: " + removedListener);

        // -- Re-Register events --

        if (removedListener != null) {
            listenerMap.put(clazz, removedListener);
            scheduler.scheduleCommand(new ReloadEventsCommand(pluginManager, removedListener, javaPlugin));

        } else if (listenerMap.containsKey(clazz)) {
            /*
             * Re-register a version from the cache.
             * This is required when you remove all @EventHandler annotations from a listener class and reload the code.
             * This reloader will deregister the instance of the event listener and don't reregister it, since there are
             * no EventHandlers to register.
             * Adding an EventHandler to the class and reloading it, wont't add it to spigot again, since no Listener
             * instance can be taken from the server.
             * To solve this, an existing instance gets cached and reused.
             */
            scheduler.scheduleCommand(new ReloadEventsCommand(pluginManager, listenerMap.get(clazz), javaPlugin));

        } else {
            /*
             * If no cached Listener instance was found, the plugin tries to create a version with the default constructor.
             */
            try {
                scheduler.scheduleCommand(new ReloadEventsCommand(pluginManager, clazz.getConstructor().newInstance(), javaPlugin));

            } catch (ReflectiveOperationException e) {
                return;
            }
        }
    }

    /**
     * Returns the removed listeners
     *
     * @param getEventListeners Method to get the eventListeners
     * @param pluginManager     Spigots Pluginmanager
     * @param usedEvents        Set of used events in the class to identify which events need to be checked for reload
     * @param oldClazz          Class object of the old version (before reload)
     */
    @SneakyThrows
    private static Listener analyzeHandlers(Method getEventListeners, SimplePluginManager pluginManager, Set<Class<? extends Event>> usedEvents, Class<? extends Listener> oldClazz) {
        Listener usedListener = null;

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
                debug("Unregistered " + e + " in " + listener.toString());

                if (usedListener == null) {
                    usedListener = listener;
                } else {
                    if (usedListener != listener) {
                        throw new RuntimeException(String.format("Found different Listeners %s and %s for class %s. This shouldn't be possible.",
                                usedListener, listener, oldClazz));
                    }
                }
            }

        }

        return usedListener;
    }


    // -----  Spigot utility methods  -----


    /**
     * Checks if a CtClass is a subclass of {@link Listener}
     *
     * @return True if this is the case.
     */
    private static boolean isListenerSubclass(CtClass ctClass) {
        try {
            // Check the interfaces of the class
            for (CtClass interfaceClazz : ctClass.getInterfaces()) {
                if (interfaceClazz.getName().equals(Listener.class.getName())) {
                    return true;
                }
            }

            // Repeat on the superclass
            CtClass superClazz = ctClass.getSuperclass();
            if (!superClazz.getName().equals(Object.class.getName())) {
                return isListenerSubclass(superClazz);
            }

        } catch (NotFoundException e) {
            return false;
        }

        return false;
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
                error("Method " + m + " has invalid parameter count.");
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


    // -----  Initialization methods  -----


    /**
     * Loads the properties file
     *
     * @param classLoader Spigots ClassLoader
     * @return Properties of null if they don't exist
     */
    private static Properties getProperties(ClassLoader classLoader, int count) {
        InputStream inputStream = classLoader.getResourceAsStream("SpigotHotswap.properties");

        Properties properties = new Properties();
        try {
            properties.load(inputStream);

        } catch (IOException | NullPointerException e) {
            if (count <= 3) {
                return getProperties(classLoader, count + 1);

            } else {
                return null;
            }
        }

        return properties;
    }

    private static Properties getProperties(ClassLoader classLoader) {
        return getProperties(classLoader, 0);
    }

    /**
     * Loads the required values from the properties
     */
    private static void loadFromProperties() {
        // Package name regex
        String packageName = properties.getProperty("packageName", "");
        if (packageName.equals("")) {
            error("Properties entry 'packageName' required.");
            return;
        }
        RE_CLASS = Pattern.compile(packageName);

        // Plugin name
        pluginName = properties.getProperty("pluginName", "");

        String logLevel = properties.getProperty("logLevel", "");
        switch (logLevel.toUpperCase()) {
            case "ALL":
                setLogLevel(Level.ALL);
                break;
            case "DEBUG":
                setLogLevel(Level.FINE);
                break;
            case "INFO":
                setLogLevel(Level.INFO);
                break;
            case "ERROR":
                setLogLevel(Level.SEVERE);
                break;

            case "":    // No Config
                debug("No 'logLevel' property. Setting level to INFO.");
                break;

            default:
                error("Properties key 'logLevel' has invalid value '" + logLevel + "'.");
        }
    }

    /***
     * Initializes the required variables
     */
    private static boolean initData(ClassLoader cLoader, String classname) {

        // Loads the properties file and sets the affected class regex
        if (properties == null) {
            properties = getProperties(cLoader);
            if (properties == null) {
                error("SpigotHotswap.properties file missing.");
                return false;

            } else {
                loadFromProperties();
            }
        }

        // Class filter initialization
        if (RE_CLASS == null) {
            return false;

        } else {
            Matcher m = RE_CLASS.matcher(classname);
            if (!m.matches()) {
                return false;
            }
        }

        // Spigot plugin initialization
        if (pluginName == null || pluginName.equals("")) {
            error("Properties entry 'pluginName' required.");
            return false;

        } else if (javaPlugin == null) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin == null) {
                error("Failed to find plugin '" + pluginName + "'.");
                return false;

            } else {
                javaPlugin = (JavaPlugin) plugin;
            }
        }

        return true;
    }


    // -----  Logger methods  -----


    /**
     * Constructs the logger
     */
    private static Logger getLogger() {
        Logger logger = Logger.getLogger(SpigotHotswapPlugin.class.getName());

        logger.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new Formatter());
        logger.addHandler(consoleHandler);
        logger.setLevel(Level.INFO);

        return logger;
    }

    /**
     * Utility method to set the loglevel to the logger and to its handlers as well
     */
    private static void setLogLevel(Level level) {
        LOGGER.setLevel(level);
        for (Handler h : LOGGER.getHandlers()) {
            h.setLevel(level);
        }
    }


    public static void all(String msg) {
        LOGGER.log(Level.ALL, msg);
    }

    public static void debug(String msg) {
        LOGGER.log(Level.FINE, msg);
    }

    public static void info(String msg) {
        LOGGER.log(Level.INFO, msg);
    }

    public static void error(String msg) {
        LOGGER.log(Level.SEVERE, msg);
    }

}
