package demo.verticle;

import com.arjuna.ats.arjuna.AtomicAction;
import demo.stm.Theatre;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class TripHelper {

    static void bookTrip(HttpClient taxiClient, RoutingContext routingContext, Theatre service, String serviceName, int taxiServicePort) {
        String showName =  routingContext.request().getParam("name");
        String taxiName =  routingContext.request().getParam("taxi");

        try {
            AtomicAction A = new AtomicAction();

            A.begin();
            service.activity(); // done as a sub transaction of A since mandatory is annotated wiht @Nested
            int activityCount = service.getValue();
            // book the taxi too
            bookTaxi(taxiClient, taxiServicePort);
            A.commit();

            routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(new ServiceResult(serviceName, Thread.currentThread().getName(), activityCount)));
        } catch (Exception e) {
            routingContext.response()
                    .setStatusCode(406)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(new JsonObject().put("Status", e.getMessage()).encode());
        }
    }

    static void bookTaxi(HttpClient taxiClient, int taxiServicePort) {
        taxiClient.getNow(taxiServicePort, "localhost", "/api/taxi/book/1",
                httpClientResponse -> httpClientResponse.bodyHandler(buffer -> {
            System.out.println("bookTaxi: Response (" + buffer.length() + "): ");
            System.out.println(buffer.getString(0, buffer.length()));
        }));
    }
}
