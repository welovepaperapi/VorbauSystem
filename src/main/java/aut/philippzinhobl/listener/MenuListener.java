package aut.philippzinhobl.listener;

import aut.philippzinhobl.Main;
import aut.philippzinhobl.manager.ApplyManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MenuListener implements Listener {

    private final Main plugin;
    private final HashMap<UUID, Integer> confirmDelete = new HashMap<>();

    public MenuListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        boolean isOurMenu = title.equals("Vorbau-System")
                || title.equals("Bewerbung einreichen")
                || title.equals("Bauwelt waehlen")
                || title.equals("Bauwelt wählen")
                || title.equals("Offene Bewerbungen")
                || title.equals("Fertige Bauwerke")
                || title.startsWith("Welt-Typ waehlen")
                || title.startsWith("Welt-Typ wählen")
                || title.startsWith("Bewerbung: ");

        if (!isOurMenu) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ItemStack item = event.getCurrentItem();
        Material clicked = item.getType();

        if (title.equals("Vorbau-System")) {
            handleMainMenu(player, slot, clicked, item);
        } else if (title.startsWith("Welt-Typ waehlen") || title.startsWith("Welt-Typ wählen")) {
            handleTypeSelection(player, title, clicked);
        } else if (title.equals("Bewerbung einreichen")) {
            handleApplyMenu(player, slot, clicked);
        } else if (title.equals("Bauwelt waehlen") || title.equals("Bauwelt wählen")) {
            handleWorldSelection(player, item);
        } else if (title.equals("Offene Bewerbungen")) {
            handleModList(player, item, event);
        } else if (title.equals("Fertige Bauwerke")) {
            handlePublicWorlds(player, item);
        } else if (title.startsWith("Bewerbung: ")) {
            handleDetailMenu(player, slot);
        }
    }

    private void handleMainMenu(Player player, int slot, Material clicked, ItemStack item) {
        int worldSlot = (slot == 11 || slot == 20 || slot == 29) ? 1
                : (slot == 13 || slot == 22 || slot == 31) ? 2
                : (slot == 15 || slot == 24 || slot == 33) ? 3 : -1;

        if (worldSlot == -1) {
            return;
        }

        if (clicked == Material.PLAYER_HEAD) {
            player.closeInventory();
            plugin.getWorldManager().teleportToWorld(player, worldSlot);
        } else if (clicked == Material.GRASS_BLOCK) {
            player.closeInventory();
            plugin.getInventoryManager().openTypeSelection(player, worldSlot);
        } else if (clicked == Material.NAME_TAG) {
            player.closeInventory();
            ApplyListener.inputWaiting.put(player.getUniqueId(), "RENAME_" + worldSlot);
            player.sendMessage(Main.PREFIX + "§eSchreibe den neuen Namen in den Chat.");
        } else if (clicked == Material.LAVA_BUCKET || clicked == Material.TNT) {
            if (confirmDelete.getOrDefault(player.getUniqueId(), -1) == worldSlot) {
                player.closeInventory();
                plugin.getWorldManager().deleteWorld(player, worldSlot);
                confirmDelete.remove(player.getUniqueId());
            } else {
                confirmDelete.put(player.getUniqueId(), worldSlot);
                item.setType(Material.TNT);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§4Sicher?");
                    item.setItemMeta(meta);
                }
                player.sendMessage(Main.PREFIX + "§cKlicke erneut zum Löschen!");
            }
        }
    }

    private void handleTypeSelection(Player player, String title, Material clicked) {
        try {
            int worldSlot = Integer.parseInt(title.substring(title.indexOf("#") + 1, title.indexOf(")")));

            if (clicked == Material.GRASS_BLOCK) {
                player.closeInventory();
                plugin.getWorldManager().createWorld(player, worldSlot, "FLAT");
            } else if (clicked == Material.GLASS) {
                player.closeInventory();
                plugin.getWorldManager().createWorld(player, worldSlot, "VOID");
            }
        } catch (Exception e) {
            player.sendMessage(Main.PREFIX + "§cFehler beim Auslesen des Slots.");
        }
    }

    private void handleApplyMenu(Player player, int slot, Material clicked) {
        switch (slot) {
            case 10 -> plugin.getApplyManager().openWorldSelectionMenu(player);
            case 12 -> {
                player.closeInventory();
                ApplyListener.inputWaiting.put(player.getUniqueId(), "DISCORD");
                player.sendMessage(Main.PREFIX + "§eSchreibe jetzt deinen Discord-Namen in den Chat.");
                player.sendMessage(Main.PREFIX + "§7Beispiel: §fshadowstorm7171");
            }
            case 14 -> {
                player.closeInventory();
                ApplyListener.inputWaiting.put(player.getUniqueId(), "NAME");
                player.sendMessage(Main.PREFIX + "§eSchreibe jetzt deinen Namen und dein Alter in den Chat.");
                player.sendMessage(Main.PREFIX + "§7Beispiel: §fPhilipp/15");
            }
            case 16 -> {
                player.closeInventory();
                ApplyListener.inputWaiting.put(player.getUniqueId(), "COMMENT");
                player.sendMessage(Main.PREFIX + "§eSchreibe jetzt deinen Kommentar in den Chat.");
                player.sendMessage(Main.PREFIX + "§7Erzähle kurz etwas über dich oder deine Bewerbung.");
            }
            case 22 -> {
                if (clicked == Material.EMERALD_BLOCK) {
                    plugin.getApplyManager().submitApplication(player);
                }
            }
            default -> {
            }
        }
    }

    private void handleWorldSelection(Player player, ItemStack item) {
        List<String> lore = getLore(item);
        if (item.getType() != Material.PLAYER_HEAD || lore.size() < 2) {
            return;
        }

        String worldName = removePrefix(lore.get(1), "ID: ");
        if (worldName.isBlank()) {
            return;
        }
        ApplyManager.ApplicationDraft draft = plugin.getApplyManager().drafts.get(player.getUniqueId());
        if (draft != null) {
            draft.worldName = worldName;
        }
        plugin.getApplyManager().openApplyMenu(player);
    }

    private void handleModList(Player mod, ItemStack item, InventoryClickEvent event) {
        List<String> lore = getLore(item);
        if (item.getType() != Material.PLAYER_HEAD || lore.size() < 2) {
            return;
        }

        String worldName = removePrefix(lore.get(0), "Welt: ");
        String targetUUID = removePrefix(lore.get(1), "ID: ");
        if (worldName.isBlank() || targetUUID.isBlank()) {
            return;
        }
        if (event.isLeftClick()) {
            teleportSpectator(mod, worldName);
        } else {
            plugin.getApplyManager().openApplicationDetail(mod, targetUUID);
        }
    }

    private void handlePublicWorlds(Player player, ItemStack item) {
        List<String> lore = getLore(item);
        if (item.getType() != Material.PLAYER_HEAD || lore.isEmpty()) {
            return;
        }

        String worldName = removePrefix(lore.getFirst(), "ID: ");
        if (worldName.isBlank()) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldName));
        }
        if (world != null) {
            player.closeInventory();
            player.teleport(world.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void handleDetailMenu(Player mod, int slot) {
        ItemStack infoItem = mod.getOpenInventory().getItem(4);
        if (infoItem == null) {
            return;
        }

        List<String> lore = getLore(infoItem);
        if (lore.size() < 2) {
            return;
        }

        String targetUUID = removePrefix(lore.get(1), "UUID: ");
        if (targetUUID.isBlank()) {
            return;
        }

        if (slot == 11) {
            plugin.getApplyManager().acceptApplication(mod, targetUUID);
            mod.closeInventory();
        } else if (slot == 13) {
            String worldName = plugin.getApplyManager().getWorldNameFromUUID(targetUUID);
            if (worldName != null) {
                teleportSpectator(mod, worldName);
            }
        } else if (slot == 15) {
            plugin.getApplyManager().denyApplication(mod, targetUUID);
            mod.closeInventory();
        }
    }

    private void teleportSpectator(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldName));
        }
        if (world != null) {
            player.closeInventory();
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(world.getSpawnLocation());
            player.sendMessage(Main.PREFIX + "§aDu besichtigst die Welt im Spectator.");
        }
    }

    private List<String> getLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().lore() == null) {
            return List.of();
        }

        return Objects.requireNonNull(item.getItemMeta().lore()).stream()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .toList();
    }

    private String removePrefix(String input, String prefix) {
        if (input == null) {
            return "";
        }
        return input.startsWith(prefix) ? input.substring(prefix.length()).trim() : input.trim();
    }
}
