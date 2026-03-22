package aut.philippzinhobl.manager;

import aut.philippzinhobl.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class FloodgateHook {

    private static final String FLOODGATE_PLUGIN = "floodgate";
    private static final String FLOODGATE_API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";
    private static final String SIMPLE_FORM_CLASS = "org.geysermc.cumulus.form.SimpleForm";

    public static boolean isFloodgateAvailable() {
        if (!Bukkit.getPluginManager().isPluginEnabled(FLOODGATE_PLUGIN)) {
            return true;
        }

        return hasClass(FLOODGATE_API_CLASS) || hasClass(SIMPLE_FORM_CLASS);
    }

    public static boolean isBedrock(Player player) {
        if (isFloodgateAvailable()) {
            return false;
        }
        return ApiAccessor.checkBedrock(player);
    }

    public static void openBedrockMainMenu(Player player, InventoryManager invManager) {
        if (isFloodgateAvailable()) {
            return;
        }
        ApiAccessor.showMainMenu(player, invManager);
    }

    public static void openTypeSelection(Player player, int slot, Main plugin) {
        if (isFloodgateAvailable()) {
            return;
        }
        ApiAccessor.showTypeSelection(player, slot, plugin);
    }

    public static void handleBedrockDeleteConfirm(Player player, int slot, InventoryManager invManager) {
        if (isFloodgateAvailable()) {
            return;
        }
        ApiAccessor.showDeleteConfirm(player, slot, invManager);
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className, false, FloodgateHook.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            return true;
        }
    }

    private static class ApiAccessor {

        private static boolean checkBedrock(Player player) {
            try {
                Object api = getFloodgateApi();
                Method isFloodgatePlayer = api.getClass().getMethod("isFloodgatePlayer", UUID.class);
                return (boolean) isFloodgatePlayer.invoke(api, player.getUniqueId());
            } catch (ReflectiveOperationException ex) {
                Bukkit.getLogger().warning("Floodgate konnte nicht abgefragt werden, Bedrock-Check wird uebersprungen.");
                return false;
            }
        }

        private static void showMainMenu(Player player, InventoryManager invManager) {
            org.geysermc.cumulus.form.SimpleForm.Builder form = org.geysermc.cumulus.form.SimpleForm.builder()
                    .title("Vorbau-System")
                    .content("Waehle eine Aktion fuer deine Slots:");

            for (int slot = 1; slot <= 3; slot++) {
                if (invManager.hasWorld(player, slot)) {
                    form.button("§2[Slot " + slot + "] Teleport\n§7Betrete deine Welt");
                    form.button("§6[Slot " + slot + "] Umbenennen\n§7Name im Chat");
                    form.button("§4[Slot " + slot + "] Loeschen\n§7Welt entfernen");
                } else {
                    form.button("§a[Slot " + slot + "] Erstellen\n§7Neue Welt");
                }
            }

            form.validResultHandler(res -> invManager.handleBedrockMainSelection(player, res.clickedButtonId()));
            sendForm(player, form.build());
        }

        private static void showTypeSelection(Player player, int slot, Main plugin) {
            org.geysermc.cumulus.form.SimpleForm.Builder form = org.geysermc.cumulus.form.SimpleForm.builder()
                    .title("Welt-Typ waehlen (#" + slot + ")")
                    .content("Welche Welt-Art?")
                    .button("§aFlat-Welt")
                    .button("§bVoid-Welt");

            form.validResultHandler(res -> {
                String type = (res.clickedButtonId() == 0) ? "FLAT" : "VOID";
                plugin.getWorldManager().createWorld(player, slot, type);
            });
            sendForm(player, form.build());
        }

        private static void showDeleteConfirm(Player player, int slot, InventoryManager invManager) {
            org.geysermc.cumulus.form.SimpleForm.Builder form = org.geysermc.cumulus.form.SimpleForm.builder()
                    .title("§4Loeschen?")
                    .content("Welt #" + slot + " wirklich loeschen?")
                    .button("§cJA, LOESCHEN")
                    .button("§7Abbrechen");

            form.validResultHandler(res -> {
                if (res.clickedButtonId() == 0) {
                    invManager.getPlugin().getWorldManager().deleteWorld(player, slot);
                } else {
                    invManager.openVorbauMenu(player);
                }
            });
            sendForm(player, form.build());
        }

        private static Object getFloodgateApi() throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName(FLOODGATE_API_CLASS);
            Method getInstance = apiClass.getMethod("getInstance");
            return getInstance.invoke(null);
        }

        private static void sendForm(Player player, Object form) {
            try {
                Object api = getFloodgateApi();

                for (Method method : api.getClass().getMethods()) {
                    if (!method.getName().equals("sendForm") || method.getParameterCount() != 2) {
                        continue;
                    }

                    Class<?>[] params = method.getParameterTypes();
                    if (UUID.class.equals(params[0]) && params[1].isInstance(form)) {
                        method.invoke(api, player.getUniqueId(), form);
                        return;
                    }
                }

                throw new IllegalStateException("Keine passende Floodgate#sendForm-Methode gefunden.");
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Floodgate-Formular konnte nicht gesendet werden.", ex);
            }
        }
    }
}
