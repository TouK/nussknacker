import i18next from "i18next";
import { isEmpty } from "lodash";
import moment from "moment";
import React from "react";

import { prepareEditor } from "../Editor";
import { editorsParameters } from "../editorsParameters";
import { FormatterType, spelFormatters, typeFormatters } from "../Formatter";
import type { ExpressionObj } from "../types";
import { EditorType } from "../types";
import type { DatepickerEditorProps } from "./DatepickerEditor";
import { DatepickerEditor } from "./DatepickerEditor";

const timeFormat = "HH:mm:ss";
const isParseable = (expression: ExpressionObj): boolean => {
    const date = spelFormatters[FormatterType.Time].decode(expression.expression);
    return date && moment(date, timeFormat).isValid();
};
type TimeEditorProps = Omit<DatepickerEditorProps, "dateFormat" | "expressionType" | "language">;

export const TimeEditor = prepareEditor<TimeEditorProps>(
    (props) => {
        const { formatter } = props;
        const dateFormatter = formatter == null ? typeFormatters[FormatterType.Time] : formatter;

        return (
            <DatepickerEditor
                {...props}
                momentFormat={timeFormat}
                dateFormat={null}
                timeFormat={timeFormat}
                formatter={dateFormatter}
                language={editorsParameters[EditorType.TIME].language}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t("editors.LocalTime.notSwitchableToHint", "Expression must be valid time to switch to {{editorName}} mode", {
                editorName: editorsParameters[EditorType.TIME].displayName,
            }),
    },
);
