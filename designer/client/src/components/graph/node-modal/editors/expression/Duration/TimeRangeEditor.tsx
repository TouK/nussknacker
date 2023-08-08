import React, { useCallback, useEffect, useState } from "react";
import { Period } from "./PeriodEditor";
import TimeRangeSection from "./TimeRangeSection";
import { TimeRange } from "./TimeRangeComponent";
import { Duration } from "./DurationEditor";
import { NodeValidationError } from "../../../../../../types";

type Props = {
    encode: (value: Duration | Period) => string;
    decode: ((exp: string) => Duration) | ((exp: string) => Period);
    onValueChange: (value: string) => void;
    editorConfig: $TodoType;
    readOnly: boolean;
    showValidation: boolean;
    fieldErrors: NodeValidationError[];
    expression: string;
    isMarked: boolean;
};

export default function TimeRangeEditor(props: Props): JSX.Element {
    const { encode, decode, onValueChange, editorConfig, readOnly, showValidation, fieldErrors, expression, isMarked } = props;

    const components = editorConfig.timeRangeComponents as Array<TimeRange>;
    const [value, setValue] = useState(() => decode(expression));

    const onComponentChange = useCallback(
        (fieldName: string, fieldValue: number) => {
            setValue({
                ...value,
                [fieldName]: isNaN(fieldValue) ? null : fieldValue,
            });
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
