package pl.touk.nussknacker.engine.api.editor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterEditor {

    ParameterEditorType type();

    /**
     * This field should be used only with {@link ParameterEditorType#FIXED_VALUES_EDITOR}
     */
    LabeledExpression[] possibleValues() default {};

    /**
     * This field should be used only with {@link ParameterEditorType#DURATION_EDITOR} or {@link ParameterEditorType#PERIOD_EDITOR}
     */
    ChronoUnit[] timeRangeComponents() default {};

    /**
     * This field should be used only with {@link ParameterEditorType#DICT_EDITOR}
     */
    String dictId() default "";

    // FIXME lbg remove
    boolean isMainEditor() default true;
}
