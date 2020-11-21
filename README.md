# Spigot Hotswap Plugin
Make Spigot plugin development more comfortable.

## Uuuh, what's this?
Developing Minecraft Plugins can be tedious, because often times you need to restart the server until
your changes have any effects. Hotswaping code is a nice feature, but Java can only hotswap method
bodies, which isn't sufficient most of the times.
<br>
This is where [DCEVM](http://dcevm.github.io/) together with [Hotswap Agent](https://github.com/HotswapProjects/HotswapAgent)
come into play. They allow you to hotswap almost any code and thus eliminate the need
to restart your server every few minutes to test your code.
<br>
This is nice and all but on problem remains: When you add or remove an EventHandler from an
EventListener, Spigot doesn't react to the changes, so your new EventHandler will do nothing and
removed EventHandler will cause errors.

### Tl;dr
This is a plugin for the Hotswap Agent, which will add or remove Spigot ``EventHandlers``
when the HotswapAgent added or removed them, so the server doesn't need to be restarted.

## Usage
Make sure that you have installed [DCEVM](http://dcevm.github.io/) and [Hotswap Agent](https://github.com/HotswapProjects/HotswapAgent)
before adding these dependencies.

To use this in your project you need to add this repository and the dependency

```
<repository>
    <id>github</id>
    <name>GitHub derteufelqwe Apache Maven Packages</name>
    <url>https://maven.pkg.github.com/derteufelqwe/spigothotswapplugin</url>
</repository>
```
```
<dependency>
  <groupId>de.derteufelqwe.SpigotHotswapPlugin</groupId>
  <artifactId>spigothotswap</artifactId>
  <version>1.0</version>
</dependency> 
```
Next you need to create a file named `SpigotHotswap.properties` in your resources directory.
This file needs to contain the following value
```
packageName=
pluginName=
logLevel=
```
`packageName` must container a regex string, which matches the package used for your project.
So if your package name is ``de.derteufelqwe.Testserver`` your regex should look like this
``de.derteufelqwe.Testserver.*``
Ths ``pluginName`` is the name of the Spigot or PaperMC plugin you want to enable hotswap for
and ``logLevel`` can have one of the following value (ordered in decreasing verbosity)
``ALL, DEBUG, INFO, ERROR``. This field is optional and will be set to `INFO` if it's not present.


# Imporant notes
- This only works with one plugin at the time. So you can't debug two plugins on one server
at the same time, using this plugin
- Make sure to remove this dependency before you publish or use your plugin. If you don't your plugin
will probably crash because it will still expect the Hotswap Agent

