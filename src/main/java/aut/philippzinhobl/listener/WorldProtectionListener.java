package aut.philippzinhobl.listener;

import aut.philippzinhobl.Main;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldProtectionListener implements Listener {

    private final Main plugin;
    private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Set<String> frozenWorlds = ConcurrentHashMap.newKeySet();

    public WorldProtectionListener(Main plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Set<String> pending = new HashSet<>();
            try (var ps = plugin.getDbManager().getConnection().prepareStatement(
                    "SELECT world_name FROM vorbau_applications WHERE status = 'PENDING'")) {
                var rs = ps.executeQuery();
                while (rs.next()) pending.add(rs.getString("world_name"));
            } catch (SQLException e) { e.printStackTrace(); }

            frozenWorlds.clear();
            frozenWorlds.addAll(pending);
        }, 20L, 20L * 30);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updatePermissions(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePermissions(event.getPlayer());
        plugin.getWorldManager().unloadBuilderCache(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldRestrict(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldRestrict(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldRestrict(Player player) {
        World world = player.getWorld();
        if (world.equals(Bukkit.getWorlds().getFirst())) return true;

        if (frozenWorlds.contains(world.getName())) return true;

        return !plugin.getWorldManager().isBuilder(player, world);
    }

    private void updatePermissions(Player player) {
        removePermissions(player);

        boolean isBuilder = plugin.getWorldManager().isBuilder(player, player.getWorld());
        boolean isFrozen = frozenWorlds.contains(player.getWorld().getName());

        if (isBuilder && !isFrozen) {
            addPermissions(player);
        }
    }

    /*
    private void checkAndCacheWorldStatus(String worldName) {
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT status FROM vorbau_applications WHERE world_name = ? AND status = 'PENDING'")) {
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            worldFrozenCache.put(worldName, rs.next());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim prüfen des Welt-Status", e);
        }
    }


    private boolean isFrozen(String worldName) {
        return worldFrozenCache.getOrDefault(worldName, false);
    }
     */

    private void addPermissions(Player player) {
        PermissionAttachment att = player.addAttachment(plugin);
        att.setPermission("fawe.permpack.basic", true);
        att.setPermission("fawe.voxelbrush", true);
        att.setPermission("axiom.default", true);
        att.setPermission("headdb.open", true);
        att.setPermission("headdb.phead", true);
        att.setPermission("minecraft.command.setworldspawn", true);

        attachments.put(player.getUniqueId(), att);
        player.sendMessage(Main.PREFIX + "§aBuilder-Tools für diese Welt aktiviert!");
    }

    private void removePermissions(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment att = attachments.remove(uuid);
        if (att != null) {
            player.removeAttachment(att);
            player.sendMessage(Main.PREFIX + "§cBuilder-Tools deaktiviert.");
        }
    }
}