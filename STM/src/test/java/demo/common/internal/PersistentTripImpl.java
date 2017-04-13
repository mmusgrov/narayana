package demo.common.internal;

import demo.common.actor.TaxiFirm;
import demo.common.actor.Theatre;
import demo.trip1.internal.RecoverableTripImpl;
import org.jboss.stm.internal.PersistentContainer;

public class PersistentTripImpl extends RecoverableTripImpl {
    public PersistentTripImpl(int capacity) {
        super(capacity);

        PersistentContainer<Theatre> theatreContainer = new PersistentContainer<>();
        PersistentContainer<TaxiFirm> taxiContainer = new PersistentContainer<>();

        super.setTheatre(theatreContainer.enlist(new TheatreImpl("Cats",20)));
        super.setPreferred(taxiContainer.enlist(new TaxiFirmImpl("favorite", 10)));
        super.setAltTaxi(taxiContainer.enlist(new TaxiFirmImpl("rival", 10)));
    }
}
