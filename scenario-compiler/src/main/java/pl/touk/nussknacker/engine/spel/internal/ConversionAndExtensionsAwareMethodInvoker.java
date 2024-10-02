package pl.touk.nussknacker.engine.spel.internal;

import pl.touk.nussknacker.engine.extension.ExtensionMethods;
import scala.PartialFunction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class ConversionAndExtensionsAwareMethodInvoker {
    private final ExtensionMethods extensionMethods;

    public ConversionAndExtensionsAwareMethodInvoker(ExtensionMethods extensionMethods) {
        this.extensionMethods = extensionMethods;
    }

    public Object invoke(Method method,
                         Object target,
                         Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        if (target != null && target.getClass().isArray() && methodDeclaringClass.isAssignableFrom(List.class)) {
            return method.invoke(RuntimeConversionHandler.convert(target), arguments);
        }
        PartialFunction<Class<?>, Object> extMethod =
            extensionMethods.invoke(method, target, arguments);
        if (extMethod.isDefinedAt(methodDeclaringClass)) {
            return extMethod.apply(methodDeclaringClass);
        } else {
            return method.invoke(target, arguments);
        }
    }
}
