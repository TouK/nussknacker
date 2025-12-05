package pl.touk.nussknacker.engine.api.editor;

import javax.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code MultiSelectLabeledValue} is used for {@link EditorType#MULTI_SELECT_EDITOR}. This editor allows selecting
 * multiple values out of a set of possible labeled JSON values. Values from this annotation will always be treated as
 * JSON strings without parentheses.
 * <p>
 * For reference, see the example implementation:
 * {@code pl.touk.nussknacker.devmodel.MultiSelectAnnotationBasedComponent}.
 * <p>
 * If non-string types such as integers, lists or records are needed, then they can be added within
 * {@link pl.touk.nussknacker.engine.api.context.transformation.DynamicComponent} API instead.
 * <p>
 * For reference, see the example implementation:
 * {@code pl.touk.nussknacker.devmodel.MultiSelectDynamicParametersBasedComponentTest}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MultiSelectLabeledValue {

    String value();

    String label();

    String description() default "";

    String icon() default "";

}
