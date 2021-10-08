package pl.touk.nussknacker.engine.functionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CollectionUtils {

    public static Set<Object> distinct(Collection<Object> collection) {
        return new HashSet<>(collection);
    }

    public static Number sum(Collection<Number> collection) {
        return collection.stream()
            .map(Number::longValue).reduce(0L, (a, b) -> a + b);
    }

}
