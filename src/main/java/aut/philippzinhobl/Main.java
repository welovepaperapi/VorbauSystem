package aut.philippzinhobl;

import aut.philippzinhobl.listener.ApplyListener;
import aut.philippzinhobl.listener.JoinListener;
import aut.philippzinhobl.listener.MenuListener;
import aut.philippzinhobl.listener.WorldProtectionListener;
import aut.philippzinhobl.manager.ApplyManager;
import aut.philippzinhobl.manager.DatabaseManager;
import aut.philippzinhobl.manager.InventoryManager;
import aut.philippzinhobl.manager.WorldManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public final class Main extends JavaPlugin {

    public static Component PREFIX_COMP;
    public static String PREFIX;

    private WorldManager worldManager;
    private InventoryManager inventoryManager;
    private DatabaseManager dbManager;
    private ApplyManager applyManager;

    private FileConfiguration databaseConfig;

    @Override
    public void onEnable() {
        // configs laden
        saveDefaultConfig();
        loadPrefix();
        createDatabaseConfig();

        // database connection
        this.dbManager = new DatabaseManager(this);
        this.dbManager.connect();

        // manager initaliesieren
        this.worldManager = new WorldManager(this);
        this.inventoryManager = new InventoryManager(this);
        this.applyManager = new ApplyManager(this);

        // events registrieren
        var pm = getServer().getPluginManager();
        pm.registerEvents(new WorldProtectionListener(this), this);
        pm.registerEvents(new JoinListener(), this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new ApplyListener(this), this);

        // Commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {

            // /spawn
            commands.registrar().register(
                    Commands.literal("spawn")
                            .executes(context -> {
                                if (context.getSource().getExecutor() instanceof Player player) {
                                    player.teleportAsync(getConfiguredSpawnLocation());
                                    player.setGameMode(GameMode.SURVIVAL);
                                    player.sendMessage(PREFIX_COMP.append(Component.text("Du wurdest zum Spawn teleportiert.", NamedTextColor.GREEN)));
                                }
                                return 1;
                            })
                            .build()
            );

            // /vorbau
            commands.registrar().register(
                    Commands.literal("vorbau")
                            .executes(context -> {
                                if (context.getSource().getExecutor() instanceof Player player) {
                                    inventoryManager.openVorbauMenu(player);
                                }
                                return 1;
                            })
                            .then(Commands.literal("create")
                                    .then(Commands.argument("type", StringArgumentType.word())
                                            .suggests((_, builder) -> {
                                                builder.suggest("void");
                                                builder.suggest("flat");
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                if (context.getSource().getExecutor() instanceof Player player) {
                                                    String type = StringArgumentType.getString(context, "type");
                                                    int freeSlot = -1;
                                                    for (int i = 1; i <= 3; i++) {
                                                        if (!worldManager.hasWorld(player, i)) {
                                                            freeSlot = i;
                                                            break;
                                                        }
                                                    }
                                                    if (freeSlot != -1) worldManager.createWorld(player, freeSlot, type);
                                                    else player.sendMessage(PREFIX_COMP.append(Component.text("Limit von 3 Welten erreicht!", NamedTextColor.RED)));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .build()
            );

            // /welt
            commands.registrar().register(
                    Commands.literal("welt")
                            .then(Commands.literal("public")
                                    .executes(context -> {
                                        if (context.getSource().getExecutor() instanceof Player player) {
                                            inventoryManager.openPublicWorldsMenu(player);
                                        }
                                        return 1;
                                    })
                            )
                            .then(Commands.literal("addbuilder")
                                    .then(Commands.argument("target", StringArgumentType.word())
                                            .executes(context -> {
                                                if (context.getSource().getExecutor() instanceof Player player) {
                                                    worldManager.addBuilder(player, StringArgumentType.getString(context, "target"));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .then(Commands.literal("removebuilder")
                                    .then(Commands.argument("target", StringArgumentType.word())
                                            .executes(context -> {
                                                if (context.getSource().getExecutor() instanceof Player player) {
                                                    worldManager.removeBuilder(player, StringArgumentType.getString(context, "target"));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .then(Commands.literal("rename")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 3))
                                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                                    .executes(context -> {
                                                        if (context.getSource().getExecutor() instanceof Player player) {
                                                            worldManager.renameWorld(player, IntegerArgumentType.getInteger(context, "slot"), StringArgumentType.getString(context, "name"));
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("finish")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, 3))
                                            .executes(context -> {
                                                if (context.getSource().getExecutor() instanceof Player player) {
                                                    int slot = IntegerArgumentType.getInteger(context, "slot");
                                                    worldManager.setWorldFinished(player, slot, true);
                                                    player.sendMessage(Main.PREFIX_COMP.append(Component.text("Welt #" + slot + " wurde als fertig markiert!", NamedTextColor.GREEN)));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            .build()
            );

            // /bewerben
            commands.registrar().register(
                    Commands.literal("bewerben")
                            .executes(context -> {
                                if (context.getSource().getExecutor() instanceof Player player) {
                                    applyManager.openApplyMenu(player);
                                }
                                return 1;
                            })
                            .build()
            );

            // /bewerbung
            commands.registrar().register(
                    Commands.literal("bewerbung")
                            .requires(source -> source.getSender().hasPermission("system.moderator"))
                            .then(Commands.literal("list")
                                    .executes(context -> {
                                        if (context.getSource().getExecutor() instanceof Player player) {
                                            applyManager.openModerationList(player);
                                        }
                                        return 1;
                                    })
                            )
                            .build()
            );

            // /admin
            commands.registrar().register(
                    Commands.literal("admin")
                            .requires(source -> source.getSender().hasPermission("system.admin"))
                            .then(Commands.literal("world")
                                    .then(Commands.literal("tp")
                                    .then(Commands.argument("worldname", StringArgumentType.word())
                                            .suggests((_, builder) -> {
                                                for (String worldName : worldManager.getKnownWorldNames()) {
                                                    builder.suggest(worldName);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                if (context.getSource().getExecutor() instanceof Player player) {
                                                    String wName = StringArgumentType.getString(context, "worldname");
                                                            World target = Bukkit.getWorld(wName);
                                                            if (target != null) player.teleportAsync(target.getSpawnLocation());
                                                            else player.sendMessage(Component.text("Welt nicht gefunden!", NamedTextColor.RED));
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("delete")
                                    .then(Commands.argument("worldname", StringArgumentType.word())
                                            .suggests((_, builder) -> {
                                                for (String worldName : worldManager.getKnownWorldNames()) {
                                                    builder.suggest(worldName);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                worldManager.adminDeleteWorld(context.getSource().getSender(), StringArgumentType.getString(context, "worldname"));
                                                return 1;
                                                    })
                                            )
                                    )
                                    .then(Commands.literal("reset")
                                    .then(Commands.argument("worldname", StringArgumentType.word())
                                            .suggests((_, builder) -> {
                                                for (String worldName : worldManager.getKnownWorldNames()) {
                                                    builder.suggest(worldName);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String wName = StringArgumentType.getString(context, "worldname");
                                                worldManager.resetWorld(context.getSource().getSender(), wName);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .build()
            );
        });

        getLogger().log(Level.INFO, ChatColor.translateAlternateColorCodes('&', PREFIX + "System erfolgreich gestartet!"));
    }

    private void loadPrefix() {
        String rawPrefix = getConfig().getString("prefix", "&8[&6BuildServer&8] &7");

        PREFIX = ChatColor.translateAlternateColorCodes('&', rawPrefix);

        PREFIX_COMP = LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrefix);
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    public void createDatabaseConfig() {
        File databaseFile = new File(getDataFolder(), "database.yml");
        if (!databaseFile.exists()) {
            databaseFile.getParentFile().mkdirs();
            databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
            databaseConfig.set("host", "localhost");
            databaseConfig.set("port", "3306");
            databaseConfig.set("database", "buildserver");
            databaseConfig.set("user", "root");
            databaseConfig.set("password", "Philipp1801#");
            try {
                databaseConfig.save(databaseFile);
            } catch (IOException e) {
                getLogger().severe("Konnte database.yml nicht speichern!");
            }
        } else {
            databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
        }
    }

    public Location getConfiguredSpawnLocation() {
        String worldName = getConfig().getString("spawn.world", Bukkit.getWorlds().getFirst().getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }

        double x = getConfig().getDouble("spawn.x", world.getSpawnLocation().getX());
        double y = getConfig().getDouble("spawn.y", world.getSpawnLocation().getY());
        double z = getConfig().getDouble("spawn.z", world.getSpawnLocation().getZ());
        float yaw = (float) getConfig().getDouble("spawn.yaw", world.getSpawnLocation().getYaw());
        float pitch = (float) getConfig().getDouble("spawn.pitch", world.getSpawnLocation().getPitch());
        return new Location(world, x, y, z, yaw, pitch);
    }

    public FileConfiguration getDatabaseConfig() { return databaseConfig; }
    public WorldManager getWorldManager() { return worldManager; }
    public InventoryManager getInventoryManager() { return inventoryManager; }
    public DatabaseManager getDbManager() { return dbManager; }
    public ApplyManager getApplyManager() { return applyManager; }
}
