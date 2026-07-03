package github.saukiya.sxitem.data.storage;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * 储存器GUI的InventoryHolder, 用于在事件中识别储存器界面及其归属
 */
@Getter
public class StorageHolder implements InventoryHolder {

    private final Storage storage;

    /**
     * 数据归属玩家 (个人储存器)
     */
    private final UUID owner;

    @Setter
    private Inventory inventory;

    public StorageHolder(Storage storage, UUID owner) {
        this.storage = storage;
        this.owner = owner;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
