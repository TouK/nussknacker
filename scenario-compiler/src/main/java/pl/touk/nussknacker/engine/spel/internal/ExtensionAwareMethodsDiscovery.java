package pl.touk.nussknacker.engine.spel.internal;

import pl.touk.nussknacker.engine.extension.ExtensionMethodsHandler;
import pl.touk.nussknacker.engine.extension.ExtensionMethods;
import scala.collection.JavaConverters;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExtensionAwareMethodsDiscovery {
    private static final List<Map.Entry<ExtensionMethodsHandler, Method[]>> EXTENSIONS_METHODS = JavaConverters
        .seqAsJavaListConverter(ExtensionMethods.extensionMethodsHandlers())
        .asJava()
        .stream()
        .map(ExtensionAwareMethodsDiscovery::getNonStaticMethods)
        .collect(Collectors.toList());
    private static final Method[] EMPTY_ARRAY = new Method[]{};

    // Calculating methods should not be cached because it's calculated only once at the first execution of
    // parsed expression (org.springframework.expression.spel.ast.MethodReference.getCachedExecutor).
    public Method[] discover(Class<?> type) {
        return concatArrays(type.getMethods(), extensionMethods(type));
    }

    private static Map.Entry<ExtensionMethodsHandler, Method[]> getNonStaticMethods(ExtensionMethodsHandler e) {
        Method[] methods = Arrays.stream(e.invocationTargetClass().getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
            .toArray(Method[]::new);
        return Map.entry(e, methods);
    }

    private static Method[] extensionMethods(Class<?> clazz) {
        return EXTENSIONS_METHODS
            .stream()
            .filter(e -> e.getKey().applies(clazz))
            .map(Map.Entry::getValue)
            .reduce(ExtensionAwareMethodsDiscovery::concatArrays)
            .orElse(EMPTY_ARRAY);
    }

    private static Method[] concatArrays(Method[] a, Method[] b) {
        return Stream
            .concat(Arrays.stream(a), Arrays.stream(b))
            .toArray(Method[]::new);
    }
}
