package prj.salmon.toropassicsystem.types;

import java.util.ArrayList;
import java.util.UUID;

public class SavingData {
    public UUID player;
    public int balance;
    public ArrayList<PaymentHistory> paymentHistory;

    public SavingData() {}
}
