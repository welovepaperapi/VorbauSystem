package aut.philippzinhobl.listener;

import aut.philippzinhobl.Main;
import aut.philippzinhobl.manager.ApplyManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.UUID;

public record ApplyListener(Main plugin) implements Listener {

    public static final HashMap<UUID, String> inputWaiting = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!inputWaiting.containsKey(uuid)) return;

        event.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(event.message());
        String type = inputWaiting.remove(uuid);

        if (type.startsWith("RENAME_")) {
            int worldSlot = Integer.parseInt(type.split("_")[1]);
            plugin.getWorldManager().renameWorld(player, worldSlot, msg);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getInventoryManager().openVorbauMenu(player));
            return;
        }

        ApplyManager.ApplicationDraft draft = plugin.getApplyManager().drafts.get(uuid);
        if (draft == null) return;

        switch (type) {
            case "NAME" -> {
                if (msg.contains(" ") || msg.contains("/")) {
                    String[] parts = msg.split("[ /]");
                    draft.name = parts[0];
                    try {
                        draft.age = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                        player.sendMessage(Main.PREFIX + "§7Name: §e" + draft.name + " §7- Alter: §e" + draft.age);
                    } catch (Exception e) {
                        player.sendMessage(Main.PREFIX + "§cAlter konnte nicht erkannt werden. Bitte gib es separat an.");
                    }
                } else {
                    draft.name = msg;
                    player.sendMessage(Main.PREFIX + "§7Name gespeichert: §e" + msg);
                }
            }
            case "AGE" -> {
                try {
                    draft.age = Integer.parseInt(msg.replaceAll("[^0-9]", ""));
                    player.sendMessage(Main.PREFIX + "§7Alter gespeichert.");
                } catch (NumberFormatException e) {
                    player.sendMessage(Main.PREFIX + "§cBitte gib eine Zahl ein!");
                }
            }
            case "DISCORD" -> {
                draft.discord = msg;
                player.sendMessage(Main.PREFIX + "§7Discord gespeichert.");
            }
            case "COMMENT" -> {
                draft.comment = msg;
                player.sendMessage(Main.PREFIX + "§7Kommentar gespeichert.");
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> plugin.getApplyManager().openApplyMenu(player));
    }
}