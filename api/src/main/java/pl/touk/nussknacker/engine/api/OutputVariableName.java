package pl.touk.nussknacker.engine.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * In parameter added by this annotation will be injected variable name
 * todo: in some situations like ending custom node we may want to make the annotated parameter optional
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface OutputVariableName {
}
