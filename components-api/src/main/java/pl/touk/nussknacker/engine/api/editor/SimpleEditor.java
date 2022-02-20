package pl.touk.nussknacker.engine.api.editor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SimpleEditor {

    SimpleEditorType type();

    /**
     * This field should be used only with {@link SimpleEditorType#FIXED_VALUES_EDITOR}
     */
    LabeledExpression[] possibleValues() default {};

    /**
     * This field should be used only with {@link SimpleEditorType#DURATION_EDITOR} or {@link SimpleEditorType#PERIOD_EDITOR}
     */
    ChronoUnit[] timeRangeComponents() default {};
}
