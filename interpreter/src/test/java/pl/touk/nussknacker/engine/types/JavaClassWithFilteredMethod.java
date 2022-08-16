package pl.touk.nussknacker.engine.types;

import java.time.temporal.TemporalUnit;
import java.util.List;

public class JavaClassWithFilteredMethod {
    public final int notVisible(List<TemporalUnit> t) {
        return 0;
    }
}
