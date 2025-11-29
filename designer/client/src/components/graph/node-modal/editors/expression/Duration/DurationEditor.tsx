import i18next from "i18next";
import { isEmpty } from "lodash";
import moment from "moment";
import React, { useCallback, useMemo } from "react";

import type { FieldError } from "../../Validators";
import type { OnValueChange } from "../Editor";
import { prepareEditor } from "../Editor";
import { editorsParameters } from "../editorsParameters";
import type { Formatter } from "../Formatter";
import { FormatterType, typeFormatters } from "../Formatter";
import type { ExpressionObj } from "../types";
import { EditorType } from "../types";
import TimeRangeEditor from "./TimeRangeEditor";

export type Duration = {
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
};

type Props = {
    onValueChange: OnValueChange;
    fieldErrors: FieldError[];
    showValidation: boolean;
    readOnly: boolean;
    isMarked: boolean;
    editorConfig: $TodoType;
    formatter: Formatter;
};

const SPEL_DURATION_SWITCHABLE_TO_REGEX =
    /^T\(java\.time\.Duration\)\.parse\('(-)?P([0-9]{1,}D)?(T((-)?[0-9]{1,}H)?((-)?[0-9]{1,}M)?((-)?[0-9]{1,}S)?)?'\)$/;
const NONE_DURATION = {
    asDays: () => null,
    hours: () => null,
    minutes: () => null,
    seconds: () => null,
};

export const DurationEditor = prepareEditor<Props>(
    (props) => {
        const { expressionObj, onValueChange, fieldErrors, showValidation, readOnly, isMarked, editorConfig, formatter } = props;

        const durationFormatter = useMemo(() => (formatter == null ? typeFormatters[FormatterType.Duration] : formatter), [formatter]);

        const isValueNotNullAndNotZero = useCallback((value: number) => value != null && value != 0, []);

        const isDurationDefined = useCallback(
            (value: Duration) =>
                isValueNotNullAndNotZero(value.days) ||
                isValueNotNullAndNotZero(value.hours) ||
                isValueNotNullAndNotZero(value.minutes) ||
                isValueNotNullAndNotZero(value.seconds),
            [isValueNotNullAndNotZero],
        );

        const encode = useCallback(
            (value: Duration): string => (isDurationDefined(value) ? durationFormatter.encode(value) : ""),
            [durationFormatter, isDurationDefined],
        );

        const decode = useCallback(
            (expression: string): Duration => {
                const decodeExecResult = durationFormatter.decode(expression);

                return duration(decodeExecResult);
            },
            [durationFormatter],
        );

        return (
            <TimeRangeEditor
                encode={encode}
                decode={decode}
                onValueChange={onValueChange}
                editorConfig={editorConfig}
                readOnly={readOnly}
                showValidation={showValidation}
                fieldErrors={fieldErrors}
                expression={expressionObj.expression}
                isMarked={isMarked}
                language={editorsParameters[EditorType.DURATION_EDITOR].language}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj: ExpressionObj) =>
            SPEL_DURATION_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || isEmpty(expressionObj.expression),

        notSwitchableToHint: () =>
            i18next.t(
                "editors.duration.noSwitchableToHint",
                "Expression must match pattern T(java.time.Duration).parse('P(n)DT(n)H(n)M') to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.DURATION_EDITOR].displayName },
            ),
    },
);

export function duration(decodeExecResult) {
    const duration = decodeExecResult == null || typeof decodeExecResult !== "string" ? NONE_DURATION : moment.duration(decodeExecResult);
    return {
        days: Math.floor(duration.asDays()),
        hours: duration.hours(),
        minutes: duration.minutes(),
        seconds: duration.seconds(),
    };
}
