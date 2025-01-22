import React, { useCallback, useMemo } from "react";
import { ExpressionObj } from "../types";
import { FieldError } from "../../Validators";
import TimeRangeEditor from "./TimeRangeEditor";
import i18next from "i18next";
import { Formatter, FormatterType, typeFormatters } from "../Formatter";
import moment from "moment";
import { isEmpty } from "lodash";
import { ExtendedEditor } from "../Editor";

export type Duration = {
    months: number;
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
};

type Props = {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
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
    months: () => null,
    days: () => null,
    hours: () => null,
    minutes: () => null,
    seconds: () => null,
};

export const DurationEditor: ExtendedEditor<Props> = (props: Props) => {
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

            const duration =
                decodeExecResult == null || typeof decodeExecResult !== "string" ? NONE_DURATION : moment.duration(decodeExecResult);
            return {
                months: 0,
                days: duration.days() + duration.months() * 31,
                hours: duration.hours(),
                minutes: duration.minutes(),
                seconds: duration.seconds(),
            };
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
        />
    );
};

DurationEditor.isSwitchableTo = (expressionObj: ExpressionObj) =>
    SPEL_DURATION_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || isEmpty(expressionObj.expression);

DurationEditor.switchableToHint = () => i18next.t("editors.duration.switchableToHint", "Switch to basic mode");

DurationEditor.notSwitchableToHint = () =>
    i18next.t(
        "editors.duration.noSwitchableToHint",
        "Expression must match pattern T(java.time.Duration).parse('P(n)DT(n)H(n)M') to switch to basic mode",
    );
