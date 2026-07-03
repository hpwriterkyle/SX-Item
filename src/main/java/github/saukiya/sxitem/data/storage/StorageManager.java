package github.saukiya.sxitem.data.storage;

import github.saukiya.sxitem.SXItem;
import github.saukiya.sxitem.data.item.ItemManager;
import github.saukiya.sxitem.util.Message;
import github.saukiya.tools.nms.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 储存器管理器
 * <p>
 * 负责加载储存器配置、打开GUI、持久化玩家数据以及处理放入限制
 */
public class StorageManager implements Listener {

    /**
     * 玩家数据子目录名 (与储存器配置同目录, 加载配置时跳过)
     */
    private static final String DATA_FOLDER = "PlayerData";

    private final JavaPlugin plugin;

    private final File rootDirectory;

    private final File dataDirectory;

    private final Map<String, Storage> storageMap = new HashMap<>();

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rootDirectory = new File(plugin.getDataFolder(), "Storage");
        this.dataDirectory = new File(rootDirectory, DATA_FOLDER);
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
        loadStorage();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 加载储存器配置
     */
    public void loadStorage() {
        storageMap.clear();
        if (!rootDirectory.exists()) {
            plugin.getLogger().warning("Directory is not exists: " + rootDirectory.getName());
            return;
        }
        loadConfigs(plugin.getName(), rootDirectory);
        plugin.getLogger().info("Loaded " + storageMap.size() + " Storages");
    }

    private void loadConfigs(String group, File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith("NoLoad")) continue;
            if (file.isDirectory()) {
                if (file.getName().equals(DATA_FOLDER)) continue;
                loadConfigs(group, file);
            } else if (file.getName().endsWith(".yml")) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String id : yaml.getKeys(false)) {
                    if (!yaml.isConfigurationSection(id)) continue;
                    if (storageMap.containsKey(id)) {
                        plugin.getLogger().warning("Don't Repeat Storage Id: " + id + " !");
                        continue;
                    }
                    storageMap.put(id, new Storage(id, yaml.getConfigurationSection(id), group));
                }
            }
        }
    }

    @Nullable
    public Storage getStorage(String id) {
        return storageMap.get(id);
    }

    public Set<String> getStorageList() {
        return storageMap.keySet();
    }

    /**
     * 为玩家打开其个人储存器
     *
     * @param player    查看并归属的玩家
     * @param storageId 储存器编号
     * @return 是否成功打开 (储存器不存在时返回false)
     */
    public boolean open(Player player, String storageId) {
        return open(player, storageId, player.getUniqueId());
    }

    /**
     * 打开指定归属玩家的储存器 (管理员可查看他人储存器)
     *
     * @param viewer    实际查看的玩家
     * @param storageId 储存器编号
     * @param owner     数据归属玩家UUID
     * @return 是否成功打开 (储存器不存在时返回false)
     */
    public boolean open(Player viewer, String storageId, UUID owner) {
        Storage storage = storageMap.get(storageId);
        if (storage == null) return false;
        open(viewer, storage, owner);
        return true;
    }

    /**
     * 打开储存器
     *
     * @param viewer  实际查看的玩家
     * @param storage 储存器定义
     * @param owner   数据归属玩家UUID
     */
    public void open(Player viewer, @Nonnull Storage storage, UUID owner) {
        StorageHolder holder = new StorageHolder(storage, owner);
        Inventory inventory = Bukkit.createInventory(holder, storage.getSize(), storage.getTitle());
        holder.setInventory(inventory);

        ItemStack[] items = loadItems(viewer, owner, storage);
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                inventory.setItem(i, items[i]);
            }
        }
        viewer.openInventory(inventory);
    }

    /**
     * 读取玩家储存器中的物品
     */
    private ItemStack[] loadItems(Player viewer, UUID owner, Storage storage) {
        ItemStack[] items = new ItemStack[storage.getSize()];
        File file = new File(dataDirectory, owner + ".yml");
        if (!file.exists()) return items;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection(storage.getId());
        if (section == null) return items;
        int[] usedAmount = new int[]{0};
        for (String slot : section.getKeys(false)) {
            try {
                int index = Integer.parseInt(slot);
                if (index < 0 || index >= items.length) continue;
                ItemStack item = loadItem(viewer, storage, section, slot, usedAmount);
                if (item != null) {
                    items[index] = item;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    /**
     * 从玩家数据读取物品。新版仅保存SX物品编号与数量, 打开时重新生成物品以同步最新配置。
     * 兼容旧版直接保存ItemStack的数据, 读取后会按其SX编号重新生成。
     */
    @Nullable
    private ItemStack loadItem(Player viewer, Storage storage, ConfigurationSection section, String slot, int[] usedAmount) {
        String key = section.getString(slot + ".Item");
        int amount = section.getInt(slot + ".Amount", 0);
        if (key == null) {
            ItemStack legacy = section.getItemStack(slot);
            key = SXItem.getItemManager().resolveItemKey(legacy);
            amount = legacy == null ? 0 : legacy.getAmount();
        }
        if (key == null || amount <= 0 || !storage.isAllowed(key)) return null;
        int max = storage.getAmount();
        if (max > 0) {
            int remaining = max - usedAmount[0];
            if (remaining <= 0) return null;
            if (amount > remaining) amount = remaining;
        }
        ItemStack item = SXItem.getItemManager().getItem(key, viewer);
        if (item == null || item.getType() == Material.AIR) return null;
        item.setAmount(amount);
        usedAmount[0] += amount;
        return item;
    }

    /**
     * 保存储存器数据到玩家文件
     */
    public void save(StorageHolder holder) {
        Storage storage = holder.getStorage();
        File file = new File(dataDirectory, holder.getOwner() + ".yml");
        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yaml.set(storage.getId(), null);
        ConfigurationSection section = yaml.createSection(storage.getId());
        Inventory inventory = holder.getInventory();
        int saveSlot = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                String key = SXItem.getItemManager().resolveItemKey(item);
                if (key == null) continue;
                section.set(saveSlot + ".Item", key);
                section.set(saveSlot + ".Amount", item.getAmount());
                saveSlot++;
            }
        }
        saveYaml(file, yaml, holder.getOwner(), storage.getId());
    }

    private void saveYaml(File file, YamlConfiguration yaml, UUID owner, String storageId) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Save Storage Failed: " + owner + " / " + storageId);
            e.printStackTrace();
        }
    }

    /**
     * 从储存器领取物品。领取后会把剩余数据按0开始连续重排, 避免slot键跳格。
     *
     * @param player 领取到背包的玩家
     * @param storage 储存器定义
     * @param maxAmount 最多领取数量, 小于等于0表示全部领取
     * @return 实际领取数量
     */
    public int claim(Player player, Storage storage, int maxAmount) {
        UUID owner = player.getUniqueId();
        File file = new File(dataDirectory, owner + ".yml");
        if (!file.exists()) return 0;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection(storage.getId());
        if (section == null) return 0;

        List<StoredItem> storedItems = readStoredItems(section);
        if (storedItems.isEmpty()) return 0;

        int claimed = 0;
        int remainingClaim = maxAmount <= 0 ? Integer.MAX_VALUE : maxAmount;
        List<StoredItem> remainingItems = new ArrayList<>();
        for (StoredItem storedItem : storedItems) {
            if (remainingClaim <= 0) {
                remainingItems.add(storedItem);
                continue;
            }
            ItemStack item = SXItem.getItemManager().getItem(storedItem.key, player);
            if (item == null || item.getType() == Material.AIR) {
                remainingItems.add(storedItem);
                continue;
            }
            int take = Math.min(storedItem.amount, remainingClaim);
            int given = giveItem(player, item, take);
            claimed += given;
            remainingClaim -= given;
            int left = storedItem.amount - given;
            if (left > 0) {
                remainingItems.add(new StoredItem(storedItem.key, left));
            }
            if (given < take) {
                remainingClaim = 0;
            }
        }

        if (claimed > 0) {
            rewriteStorage(yaml, storage.getId(), remainingItems);
            saveYaml(file, yaml, owner, storage.getId());
        }
        return claimed;
    }

    private List<StoredItem> readStoredItems(ConfigurationSection section) {
        List<String> slots = new ArrayList<>(section.getKeys(false));
        Collections.sort(slots, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.compare(parseSlot(o1), parseSlot(o2));
            }
        });
        List<StoredItem> storedItems = new ArrayList<>();
        for (String slot : slots) {
            String key = section.getString(slot + ".Item");
            int amount = section.getInt(slot + ".Amount", 0);
            if (key == null) {
                ItemStack legacy = section.getItemStack(slot);
                key = SXItem.getItemManager().resolveItemKey(legacy);
                amount = legacy == null ? 0 : legacy.getAmount();
            }
            if (key != null && amount > 0) {
                storedItems.add(new StoredItem(key, amount));
            }
        }
        return storedItems;
    }

    private int parseSlot(String slot) {
        try {
            return Integer.parseInt(slot);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private int giveItem(Player player, ItemStack template, int amount) {
        int given = 0;
        int maxStackSize = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack item = template.clone();
            item.setAmount(stackAmount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            int notGiven = 0;
            for (ItemStack value : leftover.values()) {
                notGiven += getAmount(value);
            }
            given += stackAmount - notGiven;
            if (notGiven > 0) break;
            remaining -= stackAmount;
        }
        return given;
    }

    private void rewriteStorage(YamlConfiguration yaml, String storageId, List<StoredItem> storedItems) {
        yaml.set(storageId, null);
        ConfigurationSection section = yaml.createSection(storageId);
        for (int i = 0; i < storedItems.size(); i++) {
            StoredItem item = storedItems.get(i);
            section.set(i + ".Item", item.key);
            section.set(i + ".Amount", item.amount);
        }
    }

    @EventHandler
    public void on(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StorageHolder) {
            save((StorageHolder) holder);
        }
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        StorageHolder holder = getHolder(event.getView().getTopInventory());
        if (holder == null) return;
        Storage storage = holder.getStorage();
        Inventory top = event.getView().getTopInventory();
        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(top);

        ItemStack insert = null;
        int amount = 0;

        switch (event.getAction()) {
            case PLACE_ALL:
                if (!clickedTop) return;
                insert = event.getCursor();
                amount = insert == null ? 0 : insert.getAmount();
                break;
            case PLACE_SOME:
                if (!clickedTop) return;
                insert = event.getCursor();
                amount = insert == null ? 0 : Math.max(0, insert.getMaxStackSize() - getAmount(event.getCurrentItem()));
                break;
            case PLACE_ONE:
                if (!clickedTop) return;
                insert = event.getCursor();
                amount = 1;
                break;
            case SWAP_WITH_CURSOR:
                if (!clickedTop) return;
                if (getAmount(event.getCurrentItem()) > 0) {
                    event.setCancelled(true);
                    return;
                }
                insert = event.getCursor();
                amount = getAmount(insert) - getAmount(event.getCurrentItem());
                break;
            case MOVE_TO_OTHER_INVENTORY:
                if (clickedTop) {
                    event.setCancelled(true);
                    return;
                }
                insert = event.getCurrentItem();
                amount = getAmount(insert);
                break;
            case HOTBAR_SWAP:
            case HOTBAR_MOVE_AND_READD:
                if (!clickedTop) return;
                if (getAmount(event.getCurrentItem()) > 0) {
                    event.setCancelled(true);
                    return;
                }
                ItemStack hotbar = event.getHotbarButton() >= 0
                        ? event.getView().getBottomInventory().getItem(event.getHotbarButton()) : null;
                if (hotbar == null || hotbar.getType() == Material.AIR) return; // 取出, 放行
                insert = hotbar;
                amount = hotbar.getAmount() - getAmount(event.getCurrentItem());
                break;
            default:
                if (clickedTop || event.getAction().name().equals("COLLECT_TO_CURSOR")) {
                    event.setCancelled(true);
                }
                return;
        }

        if (insert == null || insert.getType() == Material.AIR) return;
        if (!checkInsert(storage, top, (Player) event.getWhoClicked(), insert, amount)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void on(InventoryDragEvent event) {
        StorageHolder holder = getHolder(event.getView().getTopInventory());
        if (holder == null) return;
        Storage storage = holder.getStorage();
        Inventory top = event.getView().getTopInventory();
        int topSize = top.getSize();

        ItemStack insert = event.getOldCursor();
        int amount = 0;
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (entry.getKey() >= topSize) continue;
            ItemStack old = top.getItem(entry.getKey());
            if (entry.getValue() != null) {
                amount += Math.max(0, entry.getValue().getAmount() - getAmount(old));
                insert = entry.getValue();
            }
        }
        if (amount <= 0) return;
        if (insert == null || insert.getType() == Material.AIR) return;

        if (!checkInsert(storage, top, (Player) event.getWhoClicked(), insert, amount)) {
            event.setCancelled(true);
        }
    }

    /**
     * 校验是否允许向储存器放入物品, 不允许时向玩家发送提示
     *
     * @return 是否允许
     */
    private boolean checkInsert(Storage storage, Inventory top, Player who, ItemStack insert, int amount) {
        ItemManager itemManager = SXItem.getItemManager();
        String key = itemManager.resolveItemKey(insert);
        if (key == null || !storage.isAllowed(key)) {
            MessageUtil.send(who, Message.STORAGE__NOT_ALLOWED.get());
            return false;
        }
        int max = storage.getAmount();
        if (max > 0 && countTotalAmount(top) + Math.max(0, amount) > max) {
            MessageUtil.send(who, Message.STORAGE__LIMIT.get(key, String.valueOf(max)));
            return false;
        }
        return true;
    }

    /**
     * 统计储存器中已存放的物品总数量
     */
    private int countTotalAmount(Inventory inventory) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            total += getAmount(item);
        }
        return total;
    }

    /**
     * 获取非空气物品数量
     */
    private int getAmount(ItemStack item) {
        return item == null || item.getType() == Material.AIR ? 0 : item.getAmount();
    }

    @Nullable
    private StorageHolder getHolder(Inventory topInventory) {
        InventoryHolder holder = topInventory.getHolder();
        return holder instanceof StorageHolder ? (StorageHolder) holder : null;
    }

    private static class StoredItem {

        private final String key;

        private final int amount;

        private StoredItem(String key, int amount) {
            this.key = key;
            this.amount = amount;
        }
    }
}
