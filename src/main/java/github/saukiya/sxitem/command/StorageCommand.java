package github.saukiya.sxitem.command;

import github.saukiya.sxitem.SXItem;
import github.saukiya.sxitem.data.storage.Storage;
import github.saukiya.sxitem.data.storage.StorageManager;
import github.saukiya.sxitem.util.Message;
import github.saukiya.tools.command.SenderType;
import github.saukiya.tools.command.SubCommand;
import github.saukiya.tools.nms.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 储存器指令
 * <pre>
 *  <code>/si storage</code> - 查看储存器列表
 *  <code>/si storage Example</code> - 打开自己的 Example 储存器
 *  <code>/si storage Example claim [count]</code> - 从自己的 Example 储存器领取物品
 *  <code>/si storage Example player</code> - 查看/编辑 player 的 Example 储存器 (需权限 sx-item.storage)
 * </pre>
 */
public class StorageCommand extends SubCommand {

    public StorageCommand() {
        super("storage", 10);
        setArg("[id] <player>");
        setType(SenderType.PLAYER);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCommand(CommandSender sender, String[] args) {
        Player viewer = (Player) sender;
        StorageManager storageManager = SXItem.getStorageManager();
        if (args.length < 2) {
            sendStorageList(viewer);
            return;
        }
        Storage storage = storageManager.getStorage(args[1]);
        if (storage == null) {
            MessageUtil.send(viewer, Message.STORAGE__NOT_FOUND.get(args[1]));
            sendStorageList(viewer);
            return;
        }
        if (args.length > 2 && isClaimArg(args[2])) {
            int amount = args.length > 3 ? parseAmount(args[3]) : 0;
            int claimed = storageManager.claim(viewer, storage, amount);
            if (claimed <= 0) {
                MessageUtil.send(viewer, Message.STORAGE__CLAIM_EMPTY.get());
            } else {
                MessageUtil.send(viewer, Message.STORAGE__CLAIM_SUCCESS.get(storage.getId(), String.valueOf(claimed)));
            }
            return;
        }
        UUID owner = viewer.getUniqueId();
        if (args.length > 2) {
            Player target = Bukkit.getPlayerExact(args[2]);
            owner = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[2]).getUniqueId();
        }
        storageManager.open(viewer, storage, owner);
    }

    private boolean isClaimArg(String arg) {
        return "claim".equalsIgnoreCase(arg) || "take".equalsIgnoreCase(arg) || "get".equalsIgnoreCase(arg);
    }

    private int parseAmount(String arg) {
        try {
            return Math.max(0, Integer.parseInt(arg));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void sendStorageList(Player player) {
        player.sendMessage("");
        MessageUtil.getInst().builder()
                .add("§eStorageList§8 - §7ClickOpen")
                .show("§8§o§lSX-Item Storage")
                .send(player);
        for (String id : SXItem.getStorageManager().getStorageList()) {
            MessageUtil.getInst().builder()
                    .add(" §8- §a" + id)
                    .show("§7点击打开 §a" + id)
                    .runCommand("/sxitem storage " + id)
                    .send(player);
        }
        player.sendMessage("§7Find §c" + SXItem.getStorageManager().getStorageList().size() + "§7 Storages");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return SXItem.getStorageManager().getStorageList().stream()
                    .filter(id -> id.toLowerCase().contains(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            List<String> list = new ArrayList<>();
            if ("claim".contains(args[2].toLowerCase())) list.add("claim");
            if ("take".contains(args[2].toLowerCase())) list.add("take");
            if ("get".contains(args[2].toLowerCase())) list.add("get");
            if (!list.isEmpty()) return list;
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().contains(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
