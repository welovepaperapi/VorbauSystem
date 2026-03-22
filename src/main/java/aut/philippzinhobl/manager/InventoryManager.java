package aut.philippzinhobl.manager;

import aut.philippzinhobl.Main;
import aut.philippzinhobl.listener.ApplyListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class InventoryManager {

    private final Main plugin;
    private final Map<UUID, Integer> bedrockConfirmDelete = new HashMap<>();

    public InventoryManager(Main plugin) {
        this.plugin = plugin;
    }

    public Main getPlugin() {
        return plugin;
    }

    // mainmenu öffnen
    public void openVorbauMenu(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            if (FloodgateHook.isBedrock(player)) {
                FloodgateHook.openBedrockMainMenu(player, this);
                return;
            }
        }

        // Java GUI
        Inventory inv = Bukkit.createInventory(null, 9 * 5, "Vorbau-System");
        ItemStack gray = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, gray);

        for (int slot = 1; slot <= 3; slot++) {
            int headPos = 11 + (slot - 1) * 2;
            int renamePos = headPos + 9;
            int deletePos = renamePos + 9;

            if (hasWorld(player, slot)) {
                inv.setItem(headPos, createWorldHead(player, slot));
                inv.setItem(renamePos, createItem(Material.NAME_TAG, "§eWelt #" + slot + " umbenennen", "§7Klicke, um den Anzeigenamen", "§7zu ändern."));
                inv.setItem(deletePos, createItem(Material.LAVA_BUCKET, "§cWelt #" + slot + " löschen", "§7Klicke zum Löschen."));
            } else {
                inv.setItem(headPos, createItem(Material.BARRIER, "§cSlot #" + slot + " leer", "§7Noch keine Welt erstellt."));
                inv.setItem(renamePos, createItem(Material.GRASS_BLOCK, "§aWelt erstellen (Slot #" + slot + ")", "§7Typ: §fVOID"));
            }
        }

        inv.setItem(40, createItem(Material.BEACON, "§6Informationen", "§7Verwalte hier deine Bauwelten.", "§7Limit: 3/3 Welten"));
        player.openInventory(inv);
    }

    public void handleBedrockMainSelection(Player player, int id) {
        int currentId = 0;
        for (int slot = 1; slot <= 3; slot++) {
            if (hasWorld(player, slot)) {
                if (id == currentId) { plugin.getWorldManager().teleportToWorld(player, slot); return; }
                if (id == currentId + 1) {
                    ApplyListener.inputWaiting.put(player.getUniqueId(), "RENAME_" + slot);
                    player.sendMessage(Main.PREFIX + "§eSchreibe den neuen Namen in den Chat.");
                    return;
                }
                if (id == currentId + 2) {
                    FloodgateHook.handleBedrockDeleteConfirm(player, slot, this);
                    return;
                }
                currentId += 3;
            } else {
                if (id == currentId) { openTypeSelection(player, slot); return; }
                currentId += 1;
            }
        }
    }


    // welt typ selection

    public void openTypeSelection(Player player, int slot) {
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            if (FloodgateHook.isBedrock(player)) {
                FloodgateHook.openTypeSelection(player, slot, plugin);
                return;
            }
        }

        // Java GUI
        Inventory inv = Bukkit.createInventory(null, 9 * 3, Component.text("Welt-Typ wählen (#" + slot + ")", NamedTextColor.GOLD));
        ItemStack gray = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, gray);
        inv.setItem(11, createItem(Material.GRASS_BLOCK, "§aFlat-Welt", "§7Klassische flache Erde", "§7ideal für Gebäude."));
        inv.setItem(15, createItem(Material.GLASS, "§bVoid-Welt", "§7Komplette Leere", "§7ideal für Inseln/Organics."));
        player.openInventory(inv);
    }

    public void openPublicWorldsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6Fertige Bauwerke");
        ItemStack black = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, black);

        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT * FROM vorbau_worlds WHERE is_finished = true LIMIT 45")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String worldName = rs.getString("world_name");
                String displayName = rs.getString("display_name");
                String ownerName = worldName.split("_")[1];

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
                    meta.setDisplayName("§e" + (displayName != null ? displayName : "Bauwerk von " + ownerName));
                    meta.setLore(Arrays.asList("§7ID: §f" + worldName, "", "§a-> Klick: Besichtigen"));
                    head.setItemMeta(meta);
                }
                inv.addItem(head);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        player.openInventory(inv);
    }

    public boolean hasWorld(Player player, int slot) {
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT slot FROM vorbau_worlds WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setInt(2, slot);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    private ItemStack createWorldHead(Player player, int slot) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§aDeine Bauwelt #" + slot);
            meta.setLore(List.of("§7Klicke zum §eTeleportieren§7."));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

}