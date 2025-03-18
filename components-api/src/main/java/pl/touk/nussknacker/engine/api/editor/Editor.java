package pl.touk.nussknacker.engine.api.editor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Editors.class)
public @interface Editor {

    EditorType type();

    /**
     * This field should be used only with {@link EditorType#FIXED_VALUES_EDITOR}
     */
    LabeledExpression[] possibleValues() default {};

    /**
     * This field should be used only with {@link EditorType#DURATION_EDITOR} or {@link EditorType#PERIOD_EDITOR}
     */
    ChronoUnit[] timeRangeComponents() default {};

    /**
     * This field should be used only with {@link EditorType#DICT_EDITOR}
     */
    String dictId() default "";
}
