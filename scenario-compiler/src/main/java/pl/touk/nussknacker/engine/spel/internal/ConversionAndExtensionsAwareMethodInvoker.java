package pl.touk.nussknacker.engine.spel.internal;

import pl.touk.nussknacker.engine.extension.ExtensionMethodsInvoker;
import scala.PartialFunction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class ConversionAndExtensionsAwareMethodInvoker {
    private final ExtensionMethodsInvoker extensionMethodsInvoker;

    public ConversionAndExtensionsAwareMethodInvoker(ExtensionMethodsInvoker extensionMethodsInvoker) {
        this.extensionMethodsInvoker = extensionMethodsInvoker;
    }

    public Object invoke(Method method,
                         Object target,
                         Object[] arguments) throws IllegalAccessException, InvocationTargetException {
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        // todo: lbg I'm planning to remove below branch for the sake of implementing array auto conversion to list as
        //  an extension methods added to array
        if (target != null && target.getClass().isArray() && methodDeclaringClass.isAssignableFrom(List.class)) {
            return method.invoke(RuntimeConversionHandler.convert(target), arguments);
        }
        PartialFunction<Method, Object> extMethod = extensionMethodsInvoker.invoke(target, arguments);
        if (extMethod.isDefinedAt(method)) {
            return extMethod.apply(method);
        } else {
            return method.invoke(target, arguments);
        }
    }
}
