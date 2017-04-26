package demo.verticle;

import demo.stm.Activity;
import demo.stm.Theatre;

import demo.stm.TheatreImpl;
import org.jboss.stm.Container;

public class VolatileTripVerticle extends BaseVerticle {
    public static void main(String[] args) {
        Container<Theatre> container = new Container<>(Container.TYPE.RECOVERABLE, Container.MODEL.EXCLUSIVE);

        deployVerticle(VolatileTripVerticle.class.getName(), container, args);
    }
}
