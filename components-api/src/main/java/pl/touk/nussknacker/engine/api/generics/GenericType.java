package pl.touk.nussknacker.engine.api.generics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows adding logic to calculate methods result type based on
 * types of its arguments.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GenericType {
    Class<? extends TypingFunction> typingFunction();
}
