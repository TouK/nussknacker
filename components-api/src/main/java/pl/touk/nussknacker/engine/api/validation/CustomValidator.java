package pl.touk.nussknacker.engine.api.validation;

import pl.touk.nussknacker.engine.api.definition.CustomParameterValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomValidator {
    Class<? extends CustomParameterValidator> value();
}
