import i18next from "i18next";
import { isEmpty } from "lodash";
import moment from "moment";
import React, { useCallback, useMemo } from "react";

import { prepareEditor } from "../Editor";
import { editorsParameters } from "../editorsParameters";
import type { Formatter } from "../Formatter";
import { FormatterType, typeFormatters } from "../Formatter";
import type { ExpressionObj } from "../types";
import { EditorType } from "../types";
import TimeRangeEditor from "./TimeRangeEditor";

export type Period = {
    years: number;
    months: number;
    days: number;
};

type Props = {
    isMarked: boolean;
    editorConfig: $TodoType;
    formatter: Formatter;
};

const SPEL_PERIOD_SWITCHABLE_TO_REGEX =
    /^T\(java\.time\.Period\)\.parse\('(-)?P([0-9]{1,}Y)?((-)?[0-9]{1,}M)?((-)?[0-9]{1,}W)?((-)?[0-9]{1,}D)?'\)$/;
const NONE_PERIOD = {
    years: () => null,
    months: () => null,
    days: () => null,
};

export const PeriodEditor = prepareEditor<Props>(
    (props) => {
        const { expressionObj, onValueChange, fieldErrors, showValidation, readOnly, isMarked, editorConfig, formatter } = props;

        const periodFormatter = useMemo(() => (formatter == null ? typeFormatters[FormatterType.Period] : formatter), [formatter]);

        const isValueNotNullAndNotZero = useCallback((value: number) => value != null && value != 0, []);

        const isPeriodDefined = useCallback(
            (period: Period): boolean =>
                isValueNotNullAndNotZero(period.years) || isValueNotNullAndNotZero(period.months) || isValueNotNullAndNotZero(period.days),
            [isValueNotNullAndNotZero],
        );
        const encode = useCallback(
            (period: Period): string => (isPeriodDefined(period) ? periodFormatter.encode(period) : ""),
            [isPeriodDefined, periodFormatter],
        );

        const decode = useCallback(
            (expression: string): Period => {
                const result = periodFormatter.decode(expression);
                const period = result == null || typeof result !== "string" ? NONE_PERIOD : moment.duration(result);
                return {
                    years: period.years(),
                    months: period.months(),
                    days: period.days(),
                };
            },
            [periodFormatter],
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
                language={editorsParameters[EditorType.PERIOD_EDITOR].language}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj: ExpressionObj) =>
            SPEL_PERIOD_SWITCHABLE_TO_REGEX.test(expressionObj.expression) || isEmpty(expressionObj.expression),

        notSwitchableToHint: () =>
            i18next.t(
                "editors.period.notSwitchableToHint",
                "Expression must match pattern T(java.time.Period).parse('P(n)Y(n)M(n)W(n)D') to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.PERIOD_EDITOR].displayName },
            ),
    },
);
