package original;

import com.arjuna.ats.arjuna.AtomicAction;
import com.arjuna.ats.arjuna.common.Uid;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.shareddata.LocalMap;
import org.jboss.stm.Container;

public class SampleVerticle1 extends AbstractVerticle {
    private static Container.TYPE CONTAINER_TYPE = Container.TYPE.PERSISTENT;
    private static Container.MODEL CONTAINER_MODEL = Container.MODEL.SHARED;

    public void start()
    {
        LocalMap<String, String> map = vertx.sharedData().getLocalMap("demo1.mymap");
        Container<Sample> theContainer = new Container<>(CONTAINER_TYPE, CONTAINER_MODEL);

        String uidName = map.get(ServerVerticle.LEADER);

        Sample obj3 = theContainer.clone(new SampleImple(), new Uid(uidName));

        int cnt;
        StringBuffer msg = new StringBuffer();

        assert(obj3 != null);

        for (cnt = 0; cnt < 10; cnt++)
            if (tryIncrement(obj3, msg))
                break;

        if (cnt == 10)
            System.out.printf("SSSSSSSSSSSSS: original.SampleVerticle1 could not write after %d attempts%n", cnt);

        System.out.printf("XXXXXXXXXXX: original.SampleVerticle1 write result in %d attempts:%n%s%n", cnt, msg.toString());

        for (cnt = 0; cnt < 10; cnt++)
            if (tryRead(obj3, msg))
                break;

        if (cnt == 10)
            System.out.printf("SSSSSSSSSSSSS: original.SampleVerticle1 could not read after %d attempts%n", cnt);

        System.out.printf("XXXXXXXXXXX: original.SampleVerticle1 read result in %d attempts:%n%s%n", cnt, msg.toString());
    }

    boolean tryIncrement(Sample obj, StringBuffer info) {
        try {
            info.setLength(0);
            AtomicAction A = new AtomicAction();

            A.begin();

            obj.increment();
            A.commit();
//            info.append("    SSSSSSSSSSSSS: original.SampleVerticle1 write successful.");
            return true;
        } catch (Exception e) {
            info.append("    SSSSSSSSSSSSS: original.SampleVerticle1 write unsuccessful: %s").append(e.getMessage());
//            A.abort();
        }

        return false;
    }

    boolean tryRead(Sample obj, StringBuffer info) {
        try {
            info.setLength(0);

            AtomicAction A = new AtomicAction();
            A.begin();

//        assert(obj1.value() == obj2.value()); the second verticle may have updated the value
            int value = obj.value();
            A.commit();
            info.append("    SSSSSSSSSSSSS: original.SampleVerticle1 successful read. value=").append(value);
            return true;
        } catch (Exception e) {
            info.append("    SSSSSSSSSSSSS: original.SampleVerticle1 unsuccessful read: ").append(e.getMessage());
        }

        return false;
    }
}
