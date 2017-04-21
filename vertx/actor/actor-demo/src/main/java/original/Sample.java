package original;

import org.jboss.stm.annotations.NestedTopLevel;
import org.jboss.stm.annotations.Transactional;

@Transactional
@NestedTopLevel
public interface Sample
{
    void increment();
    void decrement();

    int value();
}