package me.homes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Simple homes plugin.
 *  /myhomes -> opens a menu with 6 home slots.
 *  /sethome -> sets a home in the first free slot.
 *  Bed (top row)  : click to teleport (blue bed) or to set a home here (gray bed).
 *  Dye (bottom row): click the blue dye to delete that home.
 */
public class HomesPlugin extends JavaPlugin implements Listener {

    // Where the 6 beds are in the menu (same layout as the screenshot).
    // The dye for each home is always exactly one row below (+9).
    private static final int[] BED_SLOTS = {10, 12, 13, 14, 15, 16};

    private File dataFile;
    private YamlConfiguration data;

    // Marker so we know a menu is OUR menu.
    private static class HomesMenu implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "homes.yml");
        getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sethome")) {
            // Put the home in the first free slot
            for (int n = 1; n <= BED_SLOTS.length; n++) {
                if (getHome(player, n) == null) {
                    setHome(player, n, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Home " + n + " has been set at your location!");
                    return true;
                }
            }
            // All 6 slots are full -> tell the player what to do
            player.sendMessage(ChatColor.RED + "You have no free home slots (6/6)!");
            player.sendMessage(ChatColor.YELLOW + "Open /myhomes and click the blue dye to delete a home first.");
            return true;
        }

        openMenu(player); // /myhomes
        return true;
    }

    // ---------- Menu ----------

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new HomesMenu(), 36, "HOMES");

        for (int i = 0; i < BED_SLOTS.length; i++) {
            boolean isSet = getHome(player, i + 1) != null;
            int bedSlot = BED_SLOTS[i];

            if (isSet) {
                Location h = getHome(player, i + 1);
                String where = ChatColor.GRAY + "Location: " + ChatColor.WHITE
                        + h.getBlockX() + ", " + h.getBlockY() + ", " + h.getBlockZ();
                inv.setItem(bedSlot, item(Material.BLUE_BED, ChatColor.AQUA + "" + ChatColor.BOLD + "Home " + (i + 1),
                        where,
                        "",
                        ChatColor.YELLOW + "» Click to teleport to this home"));
                inv.setItem(bedSlot + 9, item(Material.BLUE_DYE, ChatColor.RED + "" + ChatColor.BOLD + "Delete home " + (i + 1),
                        ChatColor.GRAY + "Removes this home for good.",
                        "",
                        ChatColor.YELLOW + "» Click to delete"));
            } else {
                inv.setItem(bedSlot, item(Material.GRAY_BED, ChatColor.GRAY + "" + ChatColor.BOLD + "Home " + (i + 1) + " (empty)",
                        ChatColor.GRAY + "No home saved here yet.",
                        "",
                        ChatColor.GREEN + "» Click to set this home",
                        ChatColor.GREEN + "  at your current location"));
                inv.setItem(bedSlot + 9, item(Material.GRAY_DYE, ChatColor.DARK_GRAY + "Nothing to delete",
                        ChatColor.GRAY + "Set home " + (i + 1) + " first (click the bed above)."));
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof HomesMenu)) return;
        e.setCancelled(true); // players can't take items out
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();
        for (int i = 0; i < BED_SLOTS.length; i++) {
            int number = i + 1;

            if (slot == BED_SLOTS[i]) {                 // clicked a bed
                Location home = getHome(player, number);
                if (home == null) {
                    setHome(player, number, player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Home " + number + " has been set at your location!");
                } else {
                    player.closeInventory();
                    player.teleport(home);
                    player.sendMessage(ChatColor.AQUA + "You were teleported to home " + number + ".");
                    return;
                }
                openMenu(player);                       // refresh

            } else if (slot == BED_SLOTS[i] + 9) {      // clicked a dye
                if (getHome(player, number) != null) {
                    deleteHome(player, number);
                    player.sendMessage(ChatColor.RED + "Home " + number + " has been deleted.");
                    openMenu(player);                   // refresh
                }
            }
        }
    }

    // ---------- Saving / loading ----------

    private String key(Player p, int n) {
        return p.getUniqueId() + "." + n;
    }

    private Location getHome(Player p, int n) {
        return data.getLocation(key(p, n));
    }

    private void setHome(Player p, int n, Location loc) {
        data.set(key(p, n), loc);
        save();
    }

    private void deleteHome(Player p, int n) {
        data.set(key(p, n), null);
        save();
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save homes.yml: " + ex.getMessage());
        }
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }
}
