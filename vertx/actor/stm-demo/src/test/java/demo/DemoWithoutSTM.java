package demo;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.ActionStatus;
import com.arjuna.ats.arjuna.coordinator.SynchronizationRecord;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DemoWithoutSTM {
    private boolean theatreBooked;

    private boolean restaurantBooked;
    private boolean taxiBooked;
    private String taxiFirm = "";

    @Test
    public void testNested() throws Exception {
        AtomicAction a = new AtomicAction();

        a.begin(); // outer tx

        a.addSynchronization(new TestSynch("txA"));
        bookTheatreTickets();
        AtomicAction b = new AtomicAction();
        b.begin(); // inner tx

        try {
            bookWithFavoriteTaxiCompany();
            b.commit(); // inner tx
            taxiFirm = "favorite";
        } catch(Exception e) {
            AtomicAction c = new AtomicAction();
            c.begin(); // inner tx
            bookWithRivalTaxiFirm();
            c.commit(); // inner tx
            taxiFirm = "rival";
        }
        taxiBooked = true;

        bookRestaurantTable();
        a.commit(); // outer tx
        theatreBooked = true;

        System.out.printf("Taxi Firm: %s%n", taxiFirm);
    }

    @Test
    public void testTopLevel() throws Exception {
        AtomicAction a = new AtomicAction();

        a.begin(); // tx-A

        a.addSynchronization(new TestSynch("txA"));

        doUnauditedStuff();
        AtomicAction txA = AtomicAction.suspend();
        AtomicAction b = new AtomicAction();

        b.begin(); // new top level tx-B

        a.addSynchronization(new TestSynch("txB"));

        try {
            writeAuditLogForProcessingAttempt("attempting update, assume it failed");
            b.commit(); //  tx-B
        } catch(Exception e) {
            AtomicAction.resume(txA);
            a.abort(); // tx-A
            return;
        }
        AtomicAction.resume(txA);
        doSecureBusinessSystemUpdate();
        writeAuditLogForProcessingAttempt("processing attempt completed successfully");
        a.commit(); // tx-A
    }

    private void bookTheatreTickets() {}
    private void bookWithFavoriteTaxiCompany() {}
    private void bookWithRivalTaxiFirm() {}
    private void bookRestaurantTable() {}

    private void doSecureBusinessSystemUpdate() {

    }

    private void writeAuditLogForProcessingAttempt(String s) throws Exception {
//        throw new Exception("write audit log failed");
    }

    private void doUnauditedStuff() {

    }

    class TestSynch implements SynchronizationRecord {
        private String txName;

        public TestSynch(String txName) {
            this.txName = txName;
        }

        @Override
        public Uid get_uid() {
            return null;
        }

        @Override
        public boolean beforeCompletion() {
            return true;
        }

        @Override
        public boolean afterCompletion(int status) {
            System.out.printf("%s status: %s%n", txName, ActionStatus.stringForm(status));
            return true;
        }

        @Override
        public boolean isInterposed() {
            return false;
        }

        @Override
        public int compareTo(Object o) {
            return this.equals(o) ? 0 : 1;
        }
    }
}
