package pl.touk.nussknacker.engine.spel.internal;

import pl.touk.nussknacker.engine.extension.Cast;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ConversionAndExtensionAwareMethodsDiscovery {
    private static final Method[] CAST_METHODS = Cast.class.getMethods();
    private static final Method[] LIST_AND_CAST_METHODS = concatArrays(List.class.getMethods(), CAST_METHODS);

    public Method[] discover(Class<?> type) {
        // todo: lbg I'm planning to remove below branch for the sake of implementing array auto conversion to list as
        //  an extension methods added to array.
        //  Additionally appropriate extension methods should be added to classes automatically.
        if (type.isArray()) {
            return LIST_AND_CAST_METHODS;
        }
        return concatArrays(type.getMethods(), CAST_METHODS);
    }

    private static Method[] concatArrays(Method[] a, Method[] b) {
        return Stream
            .concat(Arrays.stream(a), Arrays.stream(b))
            .toArray(Method[]::new);
    }
}
