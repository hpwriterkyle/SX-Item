package github.saukiya.sxitem.data.storage;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 储存器定义
 * <pre>
 * 配置示例:
 * ExampleStorage:
 *   Title: '&8储存器示例'
 *   Rows: 3                  # 箱子行数 1-6 (对应 9-54 格)
 *   Amount: 128              # 储存器最多可存放的物品总数量 (0或负数=不限量)
 *   Items:                   # 禁止放入的SX物品编号(黑名单)
 *     - Default-1
 *     - Default-2
 * </pre>
 */
@Getter
public class Storage {

    private final String id;

    /**
     * 已转义颜色符号的GUI标题
     */
    private final String title;

    /**
     * 容量, 9的倍数 (Rows * 9)
     */
    private final int size;

    /**
     * 禁止放入的物品编号
     */
    private final Set<String> blockedItems = new LinkedHashSet<>();

    /**
     * 储存器可存放的物品总数量 (小于等于0表示不限量)
     */
    private final int amount;

    private final String group;

    public Storage(String id, ConfigurationSection config, String group) {
        this.id = id;
        this.title = config.getString("Title", id).replace('&', '§');
        int rows = Math.max(1, Math.min(6, config.getInt("Rows", 3)));
        this.size = rows * 9;
        this.amount = config.getInt("Amount", config.getInt("MaxAmount", 0));
        ConfigurationSection items = config.getConfigurationSection("Items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                blockedItems.add(key);
            }
        } else {
            blockedItems.addAll(config.getStringList("Items"));
        }
        this.group = group;
    }

    /**
     * 是否允许放入该SX物品; Items 为黑名单, 未列出的SX物品默认允许
     */
    public boolean isAllowed(String itemKey) {
        return itemKey != null && !blockedItems.contains(itemKey);
    }
}
