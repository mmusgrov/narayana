package demo.stm;

import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Transactional;

@Transactional
@NestedTopLevel
public interface TaxiService extends Activity {
    void failingActivity() throws Exception;
}
