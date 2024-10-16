package pl.touk.nussknacker.engine.spel.internal;

import pl.touk.nussknacker.engine.extension.ExtensionMethodsInvoker;
import scala.PartialFunction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ExtensionsAwareMethodInvoker {
    private final ExtensionMethodsInvoker extensionMethodsInvoker;

    public ExtensionsAwareMethodInvoker(ExtensionMethodsInvoker extensionMethodsInvoker) {
        this.extensionMethodsInvoker = extensionMethodsInvoker;
    }

    public Object invoke(Method method,
                         Object target,
                         Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        PartialFunction<Method, Object> extMethod = extensionMethodsInvoker.invoke(target, arguments);
        if (extMethod.isDefinedAt(method)) {
            return extMethod.apply(method);
        } else {
            return method.invoke(target, arguments);
        }
    }
}
