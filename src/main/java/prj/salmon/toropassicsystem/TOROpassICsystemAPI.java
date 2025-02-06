package prj.salmon.toropassicsystem;

import org.bukkit.entity.Player;
import prj.salmon.toropassicsystem.types.PaymentHistory;

public class TOROpassICsystemAPI {

    private final TOROpassICsystem plugin;

    public TOROpassICsystemAPI(TOROpassICsystem plugin) {
        this.plugin = plugin;
    }

    public boolean deductBalance(Player player, int amount) {
        if (amount < 0) {
            return false;
        }
        TOROpassICsystem.StationData data = plugin.playerData.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        if (data.balance >= amount) {
            data.balance -= amount;
            data.paymentHistory.add(PaymentHistory.build("Other::deduct", "他のプラグインから引き去り", -amount, data.balance, System.currentTimeMillis() / 1000L));
            plugin.save();
            return true;
        } else {
            return false;
        }
    }
}
