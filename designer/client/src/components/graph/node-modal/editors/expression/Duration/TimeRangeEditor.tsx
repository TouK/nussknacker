import React, { useCallback, useEffect, useState } from "react";

import type { FieldError } from "../../Validators";
import type { OnValueChange } from "../Editor";
import type { EditorConfigForType } from "../EditorConfig";
import type { EditorType, ExpressionLang } from "../types";
import type { Duration } from "./DurationEditor";
import type { Period } from "./PeriodEditor";
import TimeRangeSection from "./TimeRangeSection";

type Props = {
    encode: (value: Duration | Period) => string;
    decode: ((exp: string) => Duration) | ((exp: string) => Period);
    onValueChange: OnValueChange;
    editorConfig: EditorConfigForType<EditorType.DURATION_EDITOR> | EditorConfigForType<EditorType.PERIOD_EDITOR>;
    readOnly: boolean;
    showValidation: boolean;
    fieldErrors: FieldError[];
    expression: string;
    isMarked: boolean;
    language: ExpressionLang;
};

export default function TimeRangeEditor(props: Props): React.JSX.Element {
    const { encode, decode, onValueChange, editorConfig, readOnly, showValidation, fieldErrors, expression, isMarked, language } = props;

    const components = editorConfig.timeRangeComponents;
    const [value, setValue] = useState(() => decode(expression));

    const onComponentChange = useCallback(
        (fieldName: string, fieldValue: string) => {
            // treating empty string as null to allow deleting single digit
            const parsedValue = fieldValue === "" ? null : parseInt(fieldValue);
            if (parsedValue === null || !isNaN(parsedValue)) {
                setValue({
                    ...value,
                    [fieldName]: parsedValue,
                });
            }
        },
        [value],
    );

    useEffect(() => {
        const encoded = encode(value);
        if (encoded !== expression) {
            onValueChange({ expression: encoded, language });
        }
    }, [encode, expression, language, onValueChange, value]);

    return (
        <TimeRangeSection
            components={components}
            onComponentValueChange={onComponentChange}
            readOnly={readOnly}
            showValidation={showValidation}
            fieldErrors={fieldErrors}
            value={value}
            expression={expression}
            isMarked={isMarked}
        />
    );
}
