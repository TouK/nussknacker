package pl.touk.nussknacker.engine.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method marked with this annotation will be invoked in ESP component (source, sink, service etc)
* */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodToInvoke {

    /**
     * Partially deprecated - for precise type computation use
     * ContextTransformation.definedBy(_.withVariable(variableName, Typed[...])
     * You still need to declare that you don't return anything. See ObjectMetadata.hasNoReturn for details.
     */
    Class<?> returnType() default Object.class;

}
