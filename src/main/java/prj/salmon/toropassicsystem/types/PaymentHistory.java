package prj.salmon.toropassicsystem.types;

public class PaymentHistory {
    public String from;
    public String to;
    public int amount;
    public int afterBalance;
    public long time;

    public PaymentHistory() {}

    public static PaymentHistory build(String from, String to, int amount, int afterBalance, long time) {
        PaymentHistory history = new PaymentHistory();
        history.from = from;
        history.to = to;
        history.amount = amount;
        history.afterBalance = afterBalance;
        history.time = time;
        return history;
    }
}
