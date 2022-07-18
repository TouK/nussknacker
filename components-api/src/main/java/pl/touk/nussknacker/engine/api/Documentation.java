package pl.touk.nussknacker.engine.api;

import cats.data.NonEmptyList;
import cats.data.Validated;
import pl.touk.nussknacker.engine.api.typed.typing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Documentation {
    String description();
}
