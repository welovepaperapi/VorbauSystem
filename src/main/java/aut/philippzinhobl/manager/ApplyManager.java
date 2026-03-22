package aut.philippzinhobl.manager;

import aut.philippzinhobl.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class ApplyManager {
    private final Main plugin;
    public final HashMap<UUID, ApplicationDraft> drafts = new HashMap<>();
    private static final String COLOR_YELLOW = "\u00A7e";
    private static final String COLOR_GRAY = "\u00A77";
    private static final String COLOR_WHITE = "\u00A7f";
    private static final String COLOR_DARK_GRAY = "\u00A78";
    private static final String COLOR_GREEN_BOLD = "\u00A7a\u00A7l";

    public ApplyManager(Main plugin) {
        this.plugin = plugin;
    }

    public void openApplyMenu(Player player) {
        ApplicationDraft draft = drafts.computeIfAbsent(player.getUniqueId(), _ -> new ApplicationDraft());
        Inventory inv = Bukkit.createInventory(null, 9 * 3, Component.text("Bewerbung einreichen", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(10, createItem(
                draft.worldName == null ? Material.RED_WOOL : Material.GREEN_WOOL,
                COLOR_YELLOW + "Bauwelt auswaehlen",
                COLOR_GRAY + "Aktuell: " + COLOR_WHITE + (draft.worldName == null ? "Nicht gewaehlt" : draft.worldName)
        ));
        inv.setItem(12, createItem(
                draft.discord == null ? Material.RED_WOOL : Material.GREEN_WOOL,
                COLOR_YELLOW + "Discord Account",
                COLOR_GRAY + "Aktuell: " + COLOR_WHITE + (draft.discord == null ? "Nicht angegeben" : draft.discord)
        ));
        inv.setItem(14, createItem(
                Material.PAPER,
                COLOR_YELLOW + "Persoenliche Infos",
                COLOR_GRAY + "Name: " + COLOR_WHITE + draft.name,
                COLOR_GRAY + "Alter: " + COLOR_WHITE + (draft.age == 0 ? "Nicht gesetzt" : draft.age)
        ));
        inv.setItem(16, createItem(Material.BOOK, COLOR_YELLOW + "Kommentar", COLOR_GRAY + "Klicke zum Bearbeiten"));

        boolean ready = draft.worldName != null && draft.discord != null && draft.age > 0;
        inv.setItem(22, createItem(
                ready ? Material.EMERALD_BLOCK : Material.BARRIER,
                COLOR_GREEN_BOLD + "Bewerbung absenden",
                COLOR_GRAY + "Alle Pflichtfelder muessen gruen sein!"
        ));

        player.openInventory(inv);
    }

    public void openWorldSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9 * 3, Component.text("Bauwelt waehlen", NamedTextColor.GOLD));
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT world_name, slot FROM vorbau_worlds WHERE uuid = ?")) {
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String worldName = rs.getString("world_name");
                int slot = rs.getInt("slot");
                int invSlot = (slot == 1) ? 11 : (slot == 2) ? 13 : 15;

                inv.setItem(invSlot, createItem(
                        Material.PLAYER_HEAD,
                        "\u00A76Welt #" + slot,
                        COLOR_GRAY + "Klicke zum Auswaehlen",
                        COLOR_DARK_GRAY + "ID: " + worldName
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler in openWorldSelectionMenu", e);
        }
        player.openInventory(inv);
    }

    public void acceptApplication(Player mod, String uuid) {
        if (updateStatus(uuid, "ACCEPTED")) {
            notifyApplicant(uuid, NamedTextColor.GREEN, "Deine Bewerbung wurde angenommen!");
            mod.sendMessage(Main.PREFIX_COMP.append(Component.text("Bewerbung angenommen!", NamedTextColor.GREEN)));
        } else {
            mod.sendMessage(Main.PREFIX_COMP.append(Component.text("Bewerbung konnte nicht angenommen werden.", NamedTextColor.RED)));
        }
    }

    public void denyApplication(Player mod, String uuid) {
        if (updateStatus(uuid, "DENIED")) {
            notifyApplicant(uuid, NamedTextColor.RED, "Deine Bewerbung wurde abgelehnt.");
            mod.sendMessage(Main.PREFIX_COMP.append(Component.text("Bewerbung abgelehnt!", NamedTextColor.RED)));
        } else {
            mod.sendMessage(Main.PREFIX_COMP.append(Component.text("Bewerbung konnte nicht abgelehnt werden.", NamedTextColor.RED)));
        }
    }

    public String getWorldNameFromUUID(String uuid) {
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "SELECT world_name FROM vorbau_applications WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("world_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler in getWorldNameFromUUID", e);
        }
        return null;
    }

    private boolean updateStatus(String uuid, String status) {
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "UPDATE vorbau_applications SET status = ?, notified = false WHERE uuid = ?")) {
            ps.setString(1, status);
            ps.setString(2, uuid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Update des Status", e);
            return false;
        }
    }

    private void notifyApplicant(String uuid, NamedTextColor color, String message) {
        Player applicant = Bukkit.getPlayer(UUID.fromString(uuid));
        if (applicant != null) {
            applicant.sendMessage(Main.PREFIX_COMP.append(Component.text(message, color)));
        }
    }

    public void openModerationList(Player moderator) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Offene Bewerbungen", NamedTextColor.GOLD));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemStack> items = new ArrayList<>();
            try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                    "SELECT uuid, world_name FROM vorbau_applications WHERE status = 'PENDING' LIMIT 54")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String worldName = rs.getString("world_name");
                    OfflinePlayer applicant = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                    String displayName = applicant.getName() != null ? applicant.getName() : uuid;

                    items.add(createItem(
                            Material.PLAYER_HEAD,
                            COLOR_YELLOW + displayName,
                            COLOR_GRAY + "Welt: " + COLOR_WHITE + worldName,
                            COLOR_GRAY + "ID: " + COLOR_DARK_GRAY + uuid
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler in openModerationList", e);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < items.size(); i++) {
                    inv.setItem(i, items.get(i));
                }
                moderator.openInventory(inv);
            });
        });
    }

    public void openApplicationDetail(Player moderator, String targetUUID) {
        UUID parsedUuid;
        try {
            parsedUuid = UUID.fromString(targetUUID);
        } catch (IllegalArgumentException ex) {
            moderator.sendMessage(Main.PREFIX_COMP.append(Component.text("Ungueltige Bewerbungs-ID.", NamedTextColor.RED)));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(parsedUuid);
        String title = "Bewerbung: " + (target.getName() != null ? target.getName() : "Unbekannt");
        Inventory inv = Bukkit.createInventory(null, 9 * 3, Component.text(title, NamedTextColor.DARK_GRAY));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(4, createItem(
                Material.BOOK,
                COLOR_YELLOW + "Details",
                COLOR_GRAY + "Spieler: " + COLOR_WHITE + target.getName(),
                COLOR_GRAY + "UUID: " + COLOR_DARK_GRAY + targetUUID
        ));
        inv.setItem(11, createItem(Material.EMERALD_BLOCK, COLOR_GREEN_BOLD + "ANNEHMEN"));
        inv.setItem(13, createItem(Material.ENDER_PEARL, "\u00A7b\u00A7lBESICHTIGEN"));
        inv.setItem(15, createItem(Material.REDSTONE_BLOCK, "\u00A7c\u00A7lABLEHNEN"));

        moderator.openInventory(inv);
    }

    public void submitApplication(Player player) {
        ApplicationDraft draft = drafts.get(player.getUniqueId());
        if (draft == null || draft.worldName == null || draft.discord == null || draft.age == 0) {
            player.sendMessage(Main.PREFIX_COMP.append(Component.text("Bitte alle Pflichtfelder ausfuellen!", NamedTextColor.RED)));
            return;
        }
        try (PreparedStatement ps = plugin.getDbManager().getConnection().prepareStatement(
                "INSERT INTO vorbau_applications (uuid, world_name, discord, real_name, age, comment) VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE status='PENDING', discord=VALUES(discord), comment=VALUES(comment)")) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, draft.worldName);
            ps.setString(3, draft.discord);
            ps.setString(4, draft.name);
            ps.setInt(5, draft.age);
            ps.setString(6, draft.comment);
            ps.executeUpdate();

            player.sendMessage(Main.PREFIX_COMP.append(Component.text("Bewerbung erfolgreich abgesendet!", NamedTextColor.GREEN)));
            drafts.remove(player.getUniqueId());
            player.closeInventory();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Absenden", e);
        }
    }

    private static ItemStack createItem(Material mat, String name, String... loreStrings) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String s : loreStrings) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(s).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class ApplicationDraft {
        public String worldName;
        public String discord;
        public String name = "Unbekannt";
        public String comment = "Kein Kommentar";
        public int age = 0;
    }
}
