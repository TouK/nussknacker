package pl.touk.nussknacker.engine.spel;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectionHelper;
import org.springframework.expression.spel.support.ReflectiveMethodExecutor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

//this basically changed org.springframework.expression.spel.support.ReflectiveMethodExecutor
//we want to create TypeDescriptor using SpelEspReflectionHelper.convertArguments
//which should work faster
public class OmitAnnotationsMethodExecutor extends ReflectiveMethodExecutor {

    private final Method method;

    private final Integer varargsPosition;

    private boolean computedPublicDeclaringClass = false;

    private Class<?> publicDeclaringClass;

    private boolean argumentConversionOccurred = false;

    public OmitAnnotationsMethodExecutor(ReflectiveMethodExecutor original) {
        super(original.getMethod());
        this.method = original.getMethod();
        if (method.isVarArgs()) {
            Class<?>[] paramTypes = method.getParameterTypes();
            this.varargsPosition = paramTypes.length - 1;
        }
        else {
            this.varargsPosition = null;
        }
    }

    /**
     * Find the first public class in the methods declaring class hierarchy that declares this method.
     * Sometimes the reflective method discovery logic finds a suitable method that can easily be
     * called via reflection but cannot be called from generated code when compiling the expression
     * because of visibility restrictions. For example if a non public class overrides toString(), this
     * helper method will walk up the type hierarchy to find the first public type that declares the
     * method (if there is one!). For toString() it may walk as far as Object.
     */
    public Class<?> getPublicDeclaringClass() {
        if (!computedPublicDeclaringClass) {
            this.publicDeclaringClass = discoverPublicClass(method, method.getDeclaringClass());
            this.computedPublicDeclaringClass = true;
        }
        return this.publicDeclaringClass;
    }

    private Class<?> discoverPublicClass(Method method, Class<?> clazz) {
        if (Modifier.isPublic(clazz.getModifiers())) {
            try {
                clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
                return clazz;
            }
            catch (NoSuchMethodException ex) {
                // Continue below...
            }
        }
        Class<?>[] ifcs = clazz.getInterfaces();
        for (Class<?> ifc: ifcs) {
            discoverPublicClass(method, ifc);
        }
        if (clazz.getSuperclass() != null) {
            return discoverPublicClass(method, clazz.getSuperclass());
        }
        return null;
    }

    public boolean didArgumentConversionOccur() {
        return this.argumentConversionOccurred;
    }


    @Override
    public TypedValue execute(EvaluationContext context, Object target, Object... arguments) throws AccessException {
        try {
            if (arguments != null) {
                this.argumentConversionOccurred =
                        //Nussknacker: here we invoke different implementation
                        SpelEspReflectionHelper.convertArguments(context.getTypeConverter(), arguments, this.method, this.varargsPosition);
            }
            if (this.method.isVarArgs()) {
                arguments = ReflectionHelper.setupArgumentsForVarargsInvocation(this.method.getParameterTypes(), arguments);
            }
            ReflectionUtils.makeAccessible(this.method);
            Object value = this.method.invoke(target, arguments);
            return new TypedValue(value, new TypeDescriptor(new MethodParameter(this.method, -1)).narrow(value));
        }
        catch (Exception ex) {
            throw new AccessException("Problem invoking method: " + this.method, ex);
        }
    }

}