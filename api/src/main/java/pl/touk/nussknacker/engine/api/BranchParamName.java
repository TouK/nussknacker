package pl.touk.nussknacker.engine.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation means that parameter will be specified on branch. It could be applied only for params of types
 * Map[String, T] where T can by eagerly evaluated type e.g. String, Int or LazyParameter[_]
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface BranchParamName {
    String value();
}