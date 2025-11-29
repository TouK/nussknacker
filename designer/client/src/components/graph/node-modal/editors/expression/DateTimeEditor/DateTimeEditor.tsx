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

const dateFormat = "YYYY-MM-DD";
const timeFormat = "HH:mm";
const dateTimeFormat = `${dateFormat} ${timeFormat}`;
const isParseable = (expression: ExpressionObj): boolean => {
    const date = spelFormatters[FormatterType.DateTime].decode(expression.expression);
    return date && moment(date, dateTimeFormat).isValid();
};

type DateTimeEditorProps = Omit<DatepickerEditorProps, "dateFormat" | "expressionType" | "expressionObj">;

export const DateTimeEditor = prepareEditor<DateTimeEditorProps>(
    (props) => {
        const { formatter } = props;
        const dateFormatter = formatter == null ? typeFormatters[FormatterType.DateTime] : formatter;

        return (
            <DatepickerEditor
                {...props}
                momentFormat={dateTimeFormat}
                dateFormat={dateFormat}
                timeFormat={timeFormat}
                formatter={dateFormatter}
                language={editorsParameters[EditorType.DATE_TIME].language}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t("editors.LocalDateTime.notSwitchableToHint", "Expression must be valid dateTime to switch to {{editorName}} mode", {
                editorName: editorsParameters[EditorType.DATE_TIME].displayName,
            }),
    },
);
