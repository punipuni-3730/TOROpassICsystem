package prj.salmon.toropassicsystem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TOROpassICsystem extends JavaPlugin implements Listener, CommandExecutor {

    private final HashMap<UUID, StationData> playerData = new HashMap<>();
    private final NamespacedKey customModelDataKey = new NamespacedKey(this, "custom_model_data");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("charge").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("charge")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
                return true;
            }

            Player player = (Player) sender;
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "使用方法: /charge <金額>");
                return true;
            }

            try {
                int amount = Integer.parseInt(args[0]);
                if (amount < 0) {
                    player.sendMessage(ChatColor.RED + "チャージ額が不正です");
                    return true;
                }
                StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());

                // 残高が20000を超えないようにする
                if (data.balance + amount > 20000) {
                    player.sendMessage(ChatColor.RED + "最大チャージ額は20000トロポまでです");
                    return true;
                }

                data.balance += amount;
                player.sendMessage(ChatColor.GREEN + String.valueOf(amount) + "トロポをチャージしました。現在の残高: " + data.balance + "トロポ");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "有効な数値を入力してください。");
            }
            return true;
        }
        return false;
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign)) return;

        Sign sign = (Sign) event.getClickedBlock().getState();
        String line1 = ChatColor.stripColor(sign.getLine(0));
        String line2 = ChatColor.stripColor(sign.getLine(1));

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // ICカードを持っている場合
        if (isValidICCard(item)) {
            // ICカードを持っている場合、看板の編集を無効にして入出場処理を行う
            event.setCancelled(true); // 看板の編集を無効化

            if ("[入場]".equals(line1) || "[出場]".equals(line1)) {
                StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());

                if ("[入場]".equals(line1)) {
                    if (data.isInStation) {
                        player.sendMessage(ChatColor.RED + "すでに入場しています。出場してから再度入場してください。");
                        return;
                    }
                    data.enterStation(line2);
                    player.sendMessage(ChatColor.GREEN + "入場: " + line2);
                    player.sendMessage(ChatColor.GREEN + "残高: " + data.balance + "トロポ");
                    // 乗車中の座標を保存
                    data.setRideStartLocation(player.getLocation());
                    player.playSound(player.getLocation(), "custom.kaisatsu", 1.0F, 1.0F);
                } else if ("[出場]".equals(line1)) {
                    if (!data.isInStation) {
                        player.sendMessage(ChatColor.RED + "入場記録がありません。");
                        return;
                    }
                    int fare = data.calculateFare();
                    if (data.balance < fare) {
                        player.sendMessage(ChatColor.RED + "残高不足です。チャージしてください。");
                    } else {
                        data.balance -= fare;
                        player.sendMessage(ChatColor.GREEN + "出場: " + line2 + " 引去: " + fare + "トロポ");
                        player.sendMessage(ChatColor.GREEN + "残高: " + data.balance + "トロポ");
                        player.playSound(player.getLocation(), "custom.kaisatsu", 1.0F, 1.0F);
                        data.exitStation();
                    }
                }
            }

            // [チャージ] 看板
            if ("[チャージ]".equals(line1)) {
                try {
                    int chargeAmount = Integer.parseInt(line2); // 2行目をチャージ額として処理
                    if (chargeAmount < 0) {
                        player.sendMessage(ChatColor.RED + "チャージ額が不正です");
                        return;
                    }
                    StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());
                    data.balance += chargeAmount;
                    player.sendMessage(ChatColor.GREEN + "チャージ額: " + chargeAmount + "トロポ");
                    player.sendMessage(ChatColor.GREEN + "現在の残高: " + data.balance + "トロポ");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "チャージ額が不正です");
                }
                return;
            }

            // [残高確認] 看板
            if ("[残高確認]".equals(line1)) {
                StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());
                player.sendMessage(ChatColor.GREEN + "現在の残高: " + data.balance + "トロポ");
                return;
            }

            // [強制出場] 看板
            if ("[強制出場]".equals(line1)) {
                StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());
                if (data.isInStation) {
                    // 強制出場のため、運賃を引かずに出場
                    player.sendMessage(ChatColor.GREEN + "強制出場しました。");
                    data.exitStation();
                } else {
                    player.sendMessage(ChatColor.RED + "入場記録がありません。まず入場してください。");
                }
                return;
            }
            return;
        }
    }


    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line1 = ChatColor.stripColor(event.getLine(0));
        if ("[入場]".equals(line1) || "[出場]".equals(line1) || "[チャージ]".equals(line1) ){
            String line2 = ChatColor.stripColor(event.getLine(1));
            if (line2 == null || line2.isEmpty()) {
                event.getPlayer().sendMessage(ChatColor.RED + "必要な情報を2行目に記載してください。");
                event.setCancelled(true);
            } else {
                event.getPlayer().sendMessage(ChatColor.GREEN + "看板が正常に設定されました。");
            }
        }
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player) {
            Player player = (Player) event.getEntered();
            StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());
            if (data.isInStation) {
                // 乗車中の座標を保存
                data.setRideStartLocation(player.getLocation());
            }
        }
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle() instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) event.getVehicle();
            if (vehicle.getPassengers().isEmpty()) return;
            Player player = (Player) vehicle.getPassengers().get(0);
            StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());

            if (data.isInStation) {
                // 乗車中の移動距離を加算
                data.addTravelDistance(event.getTo());
            }
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        // トロッコから下車時の出場処理を削除
    }

    private boolean isValidICCard(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;
        return meta.getCustomModelData() == 2 || meta.getCustomModelData() == 3;
    }

    public static class StationData {
        public boolean isInStation = false;
        public int balance = 0; // 残高はint型
        public String stationName = "";
        private Location rideStartLocation;
        private double travelDistance = 0;

        public void enterStation(String stationName) {
            this.isInStation = true;
            this.stationName = stationName;
            this.travelDistance = 0;
        }

        public void exitStation() {
            this.isInStation = false;
            this.stationName = "";
            this.travelDistance = 0;
        }

        public void setRideStartLocation(Location location) {
            this.rideStartLocation = location;
        }

        public void addTravelDistance(Location newLocation) {
            if (rideStartLocation != null) {
                travelDistance += rideStartLocation.distance(newLocation);
                rideStartLocation = newLocation;
            }
        }

        public int calculateFare() {
            return (int) (travelDistance * 1); // 仮の運賃計算式 (int型)
        }
    }
}
