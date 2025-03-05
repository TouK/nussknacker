import React, { useCallback, useEffect, useState } from "react";
import { Period } from "./PeriodEditor";
import TimeRangeSection from "./TimeRangeSection";
import { TimeRange } from "./TimeRangeComponent";
import { Duration } from "./DurationEditor";
import { FieldError } from "../../Validators";

type Props = {
    encode: (value: Duration | Period) => string;
    decode: ((exp: string) => Duration) | ((exp: string) => Period);
    onValueChange: (value: string) => void;
    editorConfig: $TodoType;
    readOnly: boolean;
    showValidation: boolean;
    fieldErrors: FieldError[];
    expression: string;
    isMarked: boolean;
};

export default function TimeRangeEditor(props: Props): JSX.Element {
    const { encode, decode, onValueChange, editorConfig, readOnly, showValidation, fieldErrors, expression, isMarked } = props;

    const components = editorConfig.timeRangeComponents as Array<TimeRange>;
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
            onValueChange(encoded);
        }
    }, [encode, expression, onValueChange, value]);

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
