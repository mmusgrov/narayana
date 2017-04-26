package demo.verticle;

import demo.stm.Theatre;

import org.jboss.stm.Container;

public class NonVolatileTripVerticle extends BaseVerticle {
    public static void main(String[] args) {
        Container<Theatre> container = new Container<>(Container.TYPE.PERSISTENT, Container.MODEL.SHARED);

        deployVerticle(NonVolatileTripVerticle.class.getName(), container, args);
    }
}
