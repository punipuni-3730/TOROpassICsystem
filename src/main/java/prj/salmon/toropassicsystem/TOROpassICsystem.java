package prj.salmon.toropassicsystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import prj.salmon.toropassicsystem.types.PaymentHistory;
import prj.salmon.toropassicsystem.types.SavingData;
import prj.salmon.toropassicsystem.types.SavingDataJson;

import java.io.IOException;
import java.util.*;

public class TOROpassICsystem extends JavaPlugin implements Listener, CommandExecutor {
    // 乗車中データ・残高
    public final HashMap<UUID, StationData> playerData = new HashMap<>();
    // データの保存
    private final JSONControler jsonControler = new JSONControler("toropass.json", getDataFolder());

    private HTTPServer httpserver;

    private final NamespacedKey customModelDataKey = new NamespacedKey(this, "custom_model_data");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getCommand("charge").setExecutor(this);
        this.getCommand("autocharge").setExecutor(this);

        try {
            httpserver = new HTTPServer(5744, this);

            jsonControler.initialiseIfNotExists();

            SavingDataJson lastdata = jsonControler.load();

            for (SavingData data : lastdata.data) {
                StationData sdata = new StationData();
                sdata.balance = data.balance;
                sdata.paymentHistory.addAll(data.paymentHistory);
                sdata.autoChargeThreshold = data.autoChargeThreshold;
                sdata.autoChargeAmount = data.autoChargeAmount;
                playerData.put(data.player, sdata);
            }
        } catch (IOException e) {
            getLogger().warning(e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        save();
        httpserver.stop();
    }

    void save() {
        SavingDataJson data = new SavingDataJson();

        data.data = new ArrayList<>();

        for (Map.Entry<UUID, StationData> entry : playerData.entrySet()) {
            SavingData sdata = new SavingData();
            sdata.player = entry.getKey();
            sdata.balance = entry.getValue().balance;
            sdata.paymentHistory = new ArrayList<>();
            sdata.paymentHistory.addAll(entry.getValue().paymentHistory);
            sdata.autoChargeThreshold = entry.getValue().autoChargeThreshold;
            sdata.autoChargeAmount = entry.getValue().autoChargeAmount;
            data.data.add(sdata);
        }

        data.lastupdate = System.currentTimeMillis() / 1000L;

        try {
            jsonControler.save(data);
        } catch (IOException e) {
            getLogger().warning(e.getMessage());
        }
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
                data.paymentHistory.add(PaymentHistory.build("Special::charge", "", amount, data.balance, System.currentTimeMillis() / 1000L));
                save();
                player.sendMessage(ChatColor.GREEN + String.valueOf(amount) + "トロポをチャージしました。現在の残高: " + data.balance + "トロポ");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "有効な数値を入力してください。");
            }
            return true;
        }
        if (command.getName().equalsIgnoreCase("autocharge")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
                return true;
            }

            Player player = (Player) sender;
            if (args.length == 2) {
                try {
                    int threshold = Integer.parseInt(args[0]);
                    int amount = Integer.parseInt(args[1]);

                    // thresholdとamountが負の数でないか確認
                    if (threshold < 0 || amount < 0) {
                        player.sendMessage(ChatColor.RED + "不正な値です");
                        return true;
                    }

                    // オートチャージ額が1万ポイントを超えないように制限
                    if (amount > 10000) {
                        player.sendMessage(ChatColor.RED + "1万トロポを超えるオートチャージはできません");
                        return true;
                    }

                    // 残高が1万ポイントを下回った場合の設定も制限
                    if (threshold > 10000) {
                        player.sendMessage(ChatColor.RED + "1万トロポを超えるときはオートチャージ設定はできません");
                        return true;
                    }

                    StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());
                    data.autoChargeThreshold = threshold;
                    data.autoChargeAmount = amount;
                    player.sendMessage(ChatColor.GREEN + "残高が " + threshold + "トロポを下回った場合に " + amount + "トロポをチャージします。");

                    save();
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "無効な数値が入力されました。");
                }
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
                StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());
                data.autoChargeThreshold = null;
                data.autoChargeAmount = null;
                player.sendMessage(ChatColor.GREEN + "オートチャージが停止されました。");

                save();
                return true;
            }

            player.sendMessage(ChatColor.RED + "使用方法: /autocharge <いくらを下回ったとき> <チャージする額> または /autocharge stopで停止");
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
                    if (data.checkAutoCharge()) {
                        player.sendMessage(ChatColor.GREEN + "オートチャージが実行されました。新しい残高: " + data.balance + "トロポ");
                    }
                    if (data.balance < fare) {
                        int shortSF = fare - data.balance;
                        player.sendMessage(ChatColor.RED + String.valueOf(shortSF) + "トロポ不足しています。チャージしてください。");
                    } else {
                        data.balance -= fare;
                        data.paymentHistory.add(PaymentHistory.build(data.stationName, line2, fare * -1, data.balance, System.currentTimeMillis() / 1000L));
                        save();
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
                    if (data.balance + chargeAmount > 20000) {
                        player.sendMessage(ChatColor.RED + "最大チャージ額は20000トロポまでです");
                        return;
                    }
                    data.balance += chargeAmount;
                    player.sendMessage(ChatColor.GREEN + "チャージ額: " + chargeAmount + "トロポ");
                    player.sendMessage(ChatColor.GREEN + "現在の残高: " + data.balance + "トロポ");
                    data.paymentHistory.add(PaymentHistory.build("Special::charge", "", chargeAmount, data.balance, System.currentTimeMillis() / 1000L));
                    save();
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
                    player.sendMessage(ChatColor.RED + "入場記録がありません。");
                }
                return;
            }
            if ("[残額調整]".equals(line1)) {
                try {
                    int newBalance = Integer.parseInt(line2);
                    StationData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new StationData());

                    if (newBalance < 0) {
                        player.sendMessage(ChatColor.RED + "値が不正です。");
                        return;
                    }
                    data.balance = newBalance; // 残高を設定
                    player.sendMessage(ChatColor.GREEN + "新しい残高: " + data.balance + "トロポ");
                    data.paymentHistory.add(PaymentHistory.build("Special::balanceAdjustment", "", newBalance, data.balance, System.currentTimeMillis() / 1000L));
                    save();
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "無効な残高値です");
                }
                return;
            }
            return;
        }
    }


    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line1 = ChatColor.stripColor(event.getLine(0));
        if ("[入場]".equals(line1) || "[出場]".equals(line1) || "[チャージ]".equals(line1)) {
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
    private boolean isValidICCard(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;
        return meta.getCustomModelData() == 1 || meta.getCustomModelData() == 2 || meta.getCustomModelData() == 3|| meta.getCustomModelData() == 4|| meta.getCustomModelData() == 5|| meta.getCustomModelData() == 6|| meta.getCustomModelData() == 7|| meta.getCustomModelData() == 8|| meta.getCustomModelData() == 9;
    }

    public class StationData {
        public boolean isInStation = false;
        public int balance = 0; // 残高はint型
        public String stationName = "";
        private Location rideStartLocation;
        private double travelDistance = 0;
        public ArrayList<PaymentHistory> paymentHistory = new ArrayList<>();

        public Integer autoChargeThreshold = null; // 残高がこれを下回った場合にオートチャージ
        public Integer autoChargeAmount = null;    // オートチャージの金額

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
            return (int) (travelDistance * .2); // 仮の運賃計算式 (int型)
        }

        public boolean checkAutoCharge() {
            if (autoChargeThreshold != null && autoChargeAmount != null && balance < autoChargeThreshold) {
                int chargeAmount = autoChargeAmount;
                balance += chargeAmount;
                paymentHistory.add(PaymentHistory.build("Special::autocharge", "", chargeAmount, balance, System.currentTimeMillis() / 1000L));
                return true;
            }
            return false;
        }
    }

    public static class HTTPServer extends NanoHTTPD {
        private final TOROpassICsystem mainclass;

        public HTTPServer(int port, TOROpassICsystem mainclass) throws IOException {
            super(port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            this.mainclass = mainclass;
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            ObjectMapper mapper = new ObjectMapper();
            if (uri.equals("/")) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"OK\"}");
            } else if (uri.startsWith("/api/balance/")) {
                Player player = mainclass.getServer().getPlayer(session.getUri().substring("/api/balance/".length()));
                if (Objects.isNull(player)) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found (Player Not Found)");
                }
                StationData data = mainclass.playerData.get(player.getUniqueId());
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"balance\": " + data.balance + "}");
            } else if (uri.startsWith("/api/history/")) {
                Player player = mainclass.getServer().getPlayer(session.getUri().substring("/api/history/".length()));
                if (Objects.isNull(player)) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found (Player Not Found)");
                }
                StationData data = mainclass.playerData.get(player.getUniqueId());
                try {
                    return newFixedLengthResponse(Response.Status.OK, "application/json", mapper.writeValueAsString(data.paymentHistory));
                } catch (JsonProcessingException e) {
                    mainclass.getLogger().warning(e.getMessage());
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found (JSON Error)");
                }
            } else {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found (URI Error)");
            }
        }
    }

}
