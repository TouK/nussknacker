package pl.touk.nussknacker.engine.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
    If annotated Parameter is LazyParameter, the value should be provided by implementation of component,
    otherwise `clazz` should have no-arg public constructor, so that it can be created and injected by the engine.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdditionalVariable {

    String name();

    Class<?> clazz();

}
