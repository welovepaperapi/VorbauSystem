package aut.philippzinhobl.listener;

import aut.philippzinhobl.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class JoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Main plugin = Main.getPlugin(Main.class);

        plugin.getWorldManager().loadBuilderCache(player);

		event.setJoinMessage("§8[§a+§8] §f" + player.getDisplayName());

        player.teleportAsync(plugin.getConfiguredSpawnLocation());
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);

        player.sendMessage(" ");
        player.sendMessage(Main.PREFIX + "§7Willkommen auf §6GlowingParadise§7!");
        player.sendMessage(Main.PREFIX + "§7Nutze §e/vorbau§7, um deine Welten zu verwalten.");
        player.sendMessage(" ");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (var ps = plugin.getDbManager().getConnection().prepareStatement(
                    "SELECT status FROM vorbau_applications WHERE uuid = ? AND notified = false AND status != 'PENDING'")) {
                ps.setString(1, player.getUniqueId().toString());
                var rs = ps.executeQuery();

                if (rs.next()) {
                    String status = rs.getString("status");

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (status.equalsIgnoreCase("ACCEPTED")) {
                            player.sendMessage(" ");
                            player.sendMessage(Main.PREFIX + "§a§lGlückwunsch!");
                            player.sendMessage(Main.PREFIX + "§7Deine Bewerbung wurde §aangenommen§7.");
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                        } else {
                            player.sendMessage(" ");
                            player.sendMessage(Main.PREFIX + "§c§lInformation:");
                            player.sendMessage(Main.PREFIX + "§7Deine Bewerbung wurde leider §cablehnt§7.");
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.5f, 1);
                        }
                        player.sendMessage(" ");

                        markAsNotified(player.getUniqueId());
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void markAsNotified(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getPlugin(Main.class), () -> {
            try (var ps = Main.getPlugin(Main.class).getDbManager().getConnection().prepareStatement(
                    "UPDATE vorbau_applications SET notified = true WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
