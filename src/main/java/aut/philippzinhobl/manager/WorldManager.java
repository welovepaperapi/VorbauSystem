package aut.philippzinhobl.manager;

import aut.philippzinhobl.Main;
import aut.philippzinhobl.generator.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public record WorldManager(Main plugin) {

    private static final Map<UUID, Set<String>> builderCache = new ConcurrentHashMap<>();
    private static final String FLAT_SETTINGS = "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\",\"structures\":{}}";

    public void loadBuilderCache(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> worlds = Collections.newSetFromMap(new ConcurrentHashMap<>());
            try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                    "SELECT world_name FROM world_builders WHERE builder_uuid = ?")) {
                ps.setString(1, player.getUniqueId().toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    worlds.add(rs.getString("world_name"));
                }

                builderCache.put(player.getUniqueId(), worlds);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden des Builder-Caches", e);
            }
        });
    }

    public void unloadBuilderCache(Player player) {
        builderCache.remove(player.getUniqueId());
    }

    public void createWorld(Player player, int slot, String type) {
        if (hasWorld(player, slot)) {
            player.sendMessage(Main.PREFIX + "§cDieser Slot ist bereits belegt!");
            return;
        }

        String normalizedType = normalizeType(type);
        String worldName = "world_" + player.getName().toLowerCase() + "_" + slot;

        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "INSERT INTO vorbau_worlds (uuid, world_name, slot, type) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, worldName);
            ps.setInt(3, slot);
            ps.setString(4, normalizedType);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern der Welt in der DB", e);
            player.sendMessage(Main.PREFIX + "§cDie Welt konnte nicht gespeichert werden.");
            return;
        }

        player.sendMessage(Main.PREFIX + "§7Welt wird als §e" + normalizedType + " §7generiert...");

        World world = createConfiguredWorldCreator(worldName, normalizedType).createWorld();
        if (world == null) {
            player.sendMessage(Main.PREFIX + "§cDie Welt konnte nicht erstellt werden.");
            return;
        }

        setupWorld(world, normalizedType);
        player.teleport(world.getSpawnLocation());
        player.setGameMode(GameMode.CREATIVE);
        player.sendMessage(Main.PREFIX + "§aViel Spaß beim Bauen!");
    }

    public void teleportToWorld(Player player, int slot) {
        String worldName = "world_" + player.getName().toLowerCase() + "_" + slot;
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists()) {
                world = Bukkit.createWorld(createConfiguredWorldCreator(worldName, getWorldType(worldName)));
            }
        }

        if (world != null) {
            player.teleport(world.getSpawnLocation());
            player.sendMessage(Main.PREFIX + "§aTeleportiert zu Welt #" + slot);
			player.setGameMode(GameMode.CREATIVE);
		} else {
            player.sendMessage(Main.PREFIX + "§cDiese Welt existiert nicht!");
        }
    }

    public void deleteWorld(Player player, int slot) {
        String worldName = "world_" + player.getName().toLowerCase() + "_" + slot;
        adminDeleteWorld(player, worldName);
        player.sendMessage(Main.PREFIX + "§aWelt #" + slot + " wurde gelöscht.");
    }

    public void adminDeleteWorld(CommandSender sender, String worldName) {
        try {
            try (PreparedStatement ps1 = plugin.getDbManager().getConnection().prepareStatement(
                    "DELETE FROM vorbau_worlds WHERE world_name = ?")) {
                ps1.setString(1, worldName);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = plugin.getDbManager().getConnection().prepareStatement(
                    "DELETE FROM world_builders WHERE world_name = ?")) {
                ps2.setString(1, worldName);
                ps2.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Löschen aus der DB", e);
        }

        World world = Bukkit.getWorld(worldName);
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (world != null) {
            Location spawnLocation = plugin.getConfiguredSpawnLocation();
            for (Player p : world.getPlayers()) {
                p.teleport(spawnLocation);
                p.sendMessage(Main.PREFIX + "§cDie Welt, auf der du warst, wurde gelöscht.");
            }

            Bukkit.unloadWorld(world, false);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (worldFolder.exists()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }

                if (deleteFolder(worldFolder)) {
                    sender.sendMessage(Main.PREFIX + "§7Welt §e" + worldName + " §7wurde physisch gelöscht.");
                } else {
                    sender.sendMessage(Main.PREFIX + "§cDer Ordner konnte nicht vollständig gelöscht werden (Datei blockiert).");
                    sender.sendMessage(Main.PREFIX + "§7Die Welt wurde aber aus der Datenbank entfernt.");
                }
            } else {
                sender.sendMessage(Main.PREFIX + "§7Welt §e" + worldName + " §7existierte nicht als Datei.");
            }
        });
    }

    public void resetWorld(CommandSender sender, String worldName) {
        String type = getWorldType(worldName);
        sender.sendMessage(Main.PREFIX + "§7Reset wird durchgeführt...");

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location spawn = plugin.getConfiguredSpawnLocation();
            world.getPlayers().forEach(p -> p.teleport(spawn));
            Bukkit.unloadWorld(world, false);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File folder = new File(Bukkit.getWorldContainer(), worldName);
            deleteFolder(folder);

            Bukkit.getScheduler().runTask(plugin, () -> {
                World newWorld = createConfiguredWorldCreator(worldName, type).createWorld();
                if (newWorld != null) {
                    setupWorld(newWorld, type);
                    sender.sendMessage(Main.PREFIX + "§aWelt §e" + worldName + " §awurde erfolgreich resettet.");
                } else {
                    sender.sendMessage(Main.PREFIX + "§cFehler beim Neuerstellen der Welt.");
                }
            });
        });
    }

    public void addBuilder(Player owner, String targetName) {
        World world = owner.getWorld();
        if (!world.getName().startsWith("world_" + owner.getName().toLowerCase())) {
            owner.sendMessage(Main.PREFIX + "§cDu musst in deiner eigenen Welt sein!");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            owner.sendMessage(Main.PREFIX + "§cSpieler war nie online.");
            return;
        }

        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "INSERT IGNORE INTO world_builders (world_name, builder_uuid) VALUES (?, ?)")) {
            ps.setString(1, world.getName());
            ps.setString(2, target.getUniqueId().toString());
            ps.executeUpdate();
            owner.sendMessage(Main.PREFIX + "§a" + targetName + " wurde als Builder hinzugefügt.");

            builderCache.computeIfAbsent(target.getUniqueId(), _ -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(world.getName());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Hinzufügen eines Builders", e);
        }
    }

    public void removeBuilder(Player owner, String targetName) {
        World world = owner.getWorld();
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "DELETE FROM world_builders WHERE world_name = ? AND builder_uuid = ?")) {
            ps.setString(1, world.getName());
            ps.setString(2, target.getUniqueId().toString());
            ps.executeUpdate();
            owner.sendMessage(Main.PREFIX + "§aRechte für " + targetName + " entzogen.");

            Set<String> worlds = builderCache.get(target.getUniqueId());
            if (worlds != null) {
                worlds.remove(world.getName());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Entfernen eines Builders", e);
        }
    }

    public boolean isBuilder(Player player, World world) {
        String worldName = world.getName();
        if (worldName.startsWith("world_" + player.getName().toLowerCase())) {
            return true;
        }

        Set<String> allowedWorlds = builderCache.get(player.getUniqueId());
        return allowedWorlds != null && allowedWorlds.contains(worldName);
    }

    public void renameWorld(Player player, int slot, String newName) {
        String worldName = "world_" + player.getName().toLowerCase() + "_" + slot;
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "UPDATE vorbau_worlds SET display_name = ? WHERE world_name = ?")) {
            ps.setString(1, newName);
            ps.setString(2, worldName);
            ps.executeUpdate();
            player.sendMessage(Main.PREFIX + "§7Welt #" + slot + " heißt nun §e" + newName);
        } catch (SQLException e) {
            player.sendMessage(Main.PREFIX + "§cFehler beim Umbenennen.");
        }
    }

    public void setWorldFinished(Player player, int slot, boolean finished) {
        String worldName = "world_" + player.getName().toLowerCase() + "_" + slot;
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "UPDATE vorbau_worlds SET is_finished = ? WHERE world_name = ?")) {
            ps.setBoolean(1, finished);
            ps.setString(2, worldName);
            ps.executeUpdate();
            player.sendMessage(Main.PREFIX + "§7Status aktualisiert.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren des Fertig-Status", e);
        }
    }

    private void setupWorld(World world, String type) {
        world.getWorldBorder().setCenter(0.5, 0.5);
        world.getWorldBorder().setSize(250);
        world.setTime(1000);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setStorm(false);

        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);

        if ("VOID".equalsIgnoreCase(type)) {
            world.getBlockAt(0, 63, 0).setType(Material.BEDROCK);
            world.setSpawnLocation(new Location(world, 0.5, 64, 0.5));
        }
    }

    public boolean hasWorld(Player player, int slot) {
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT slot FROM vorbau_worlds WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setInt(2, slot);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public Set<String> getKnownWorldNames() {
        Set<String> worldNames = new TreeSet<>();
        for (World world : Bukkit.getWorlds()) {
            worldNames.add(world.getName());
        }

        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT world_name FROM vorbau_worlds")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                worldNames.add(rs.getString("world_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Weltnamen", e);
        }

        return worldNames;
    }

    private String getWorldType(String worldName) {
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT type FROM vorbau_worlds WHERE world_name = ?")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return normalizeType(rs.getString("type"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Welt-Typ konnte nicht geladen werden für " + worldName, e);
        }
        return "VOID";
    }

    private String normalizeType(String type) {
        return "FLAT".equalsIgnoreCase(type) ? "FLAT" : "VOID";
    }

    private WorldCreator createConfiguredWorldCreator(String worldName, String type) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);

        if ("FLAT".equalsIgnoreCase(type)) {
            creator.type(WorldType.FLAT);
            creator.generatorSettings(FLAT_SETTINGS);
        } else {
            creator.generator(new VoidGenerator());
        }

        return creator;
    }

    private boolean deleteFolder(File file) {
        if (!file.exists()) {
            return true;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteFolder(child);
                }
            }
        }
        return file.delete();
    }
}
