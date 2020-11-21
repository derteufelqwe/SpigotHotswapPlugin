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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name = "SpigotHotswapPlugin", testedVersions = "1.12.2 upwards")
public class SpigotHotswapPlugin {

    public static Logger LOGGER = getLogger();
    public static LogLevel LOGLEVEL = LogLevel.INFO;
    private static Properties properties;
    private static final String PLUGIN_PACKAGE = "*.*";
    private static Pattern RE_CLASS;
    private static String pluginName = "";
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

        // Loads the properties file and sets the affected class regex
        if (properties == null) {
            properties = getProperties(cLoader);
            if (properties == null) {
                LOGGER.severe("SpigotHotswap.properties file missing.");
                return;

            } else {
               loadFromProperties();
            }
        }

        // This doesn't work without a working class regex
        if (RE_CLASS == null) {
            return;

        } else {
            Matcher m = RE_CLASS.matcher(classname);
            if (!m.matches()) {
                return;
            }
        }

        if (pluginName == null || pluginName.equals("")) {
            LOGGER.severe("Properties entry 'pluginName' required.");
            return;
        }

        LOGGER.info("Updated class " + classname);

        // Process Listener classes
        if (isListenerSubclass(ctClass)) {
            onEventListenerChange(ctClass, cLoader, classname.replace("/", "."));
        }

    }


    /**
     * Loads the required values from the properties
     */
    private static void loadFromProperties() {
        // Package name regex
        String packageName = properties.getProperty("packageName", "");
        if (packageName.equals("")) {
            LOGGER.severe("Properties entry 'packageName' required.");
            return;
        }
        RE_CLASS = Pattern.compile(packageName);

        // Plugin name
        pluginName = properties.getProperty("pluginName", "");

        String logLevel = properties.getProperty("logLevel", "");
        if (!logLevel.equals("")) {
            try {
                LOGLEVEL = LogLevel.valueOf(logLevel);

            } catch (IllegalArgumentException e) {
                //
            }
        }
    }


    /**
     * Checks if a CtClass is a subclass of {@link Listener}
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
     * Method responsible for parsing eventListeners
     * @param ctClass CtClass that was changed
     * @param cLoader ClassLoader of the class that was updated
     * @param className Name of the changed class
     */
    @SneakyThrows
    private static void onEventListenerChange(CtClass ctClass, ClassLoader cLoader, String className) {
        LOGGER.info("Reloaded Listener class " + className);

        SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

        Method getEventListeners = SimplePluginManager.class.getDeclaredMethod("getEventListeners", Class.class);
        getEventListeners.setAccessible(true);

        Method getRegistrationClass = SimplePluginManager.class.getDeclaredMethod("getRegistrationClass", Class.class);
        getRegistrationClass.setAccessible(true);

        Class<? extends Listener> clazz = (Class<? extends Listener>) cLoader.loadClass(className);

        // -- Gather data --

        Set<Class<? extends Event>> usedEvents = getUsedEvents(clazz, cLoader);
        LOGGER.info("Used events: " + usedEvents);

        JavaPlugin javaPlugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);

        // -- Unregister Events --

        Listener removedListener = analyzeHandlers(getEventListeners, pluginManager, usedEvents, clazz);
        LOGGER.info( "Removed Listener: " + removedListener);

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
     * @param getEventListeners Method to get the eventListeners
     * @param pluginManager Spigots Pluginmanager
     * @param usedEvents Set of used events in the class to identify which events need to be checked for reload
     * @param oldClazz Class object of the old version (before reload)
     */
    @SneakyThrows
    private static Listener analyzeHandlers(Method getEventListeners, SimplePluginManager pluginManager, Set<Class<? extends Event>> usedEvents, Class<? extends Listener> oldClazz) {
        JavaPlugin javaPlugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(pluginName);
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
                LOGGER.info("Unregistered " + e + " in " + listener.toString());

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

    /**
     * Loads the properties file
     * @param classLoader Spigots ClassLoader
     * @return Properties of null if they don't exist
     */
    private static Properties getProperties(ClassLoader classLoader) {
        InputStream inputStream = classLoader.getResourceAsStream("SpigotHotswap.properties");

        Properties properties = new Properties();
        try {
            properties.load(inputStream);

        } catch (IOException | NullPointerException e) {
            return null;
        }

        return properties;
    }

    /**
     * Constructs the logger
     */
    private static Logger getLogger() {
        Logger logger = Logger.getLogger(SpigotHotswapPlugin.class.getName());

        logger.setUseParentHandlers(false);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new Formatter());
        logger.addHandler(consoleHandler);

        return logger;
    }


}
