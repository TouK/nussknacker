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
const isParseable = (expression: ExpressionObj): boolean => {
    const date = spelFormatters[FormatterType.Date].decode(expression.expression);
    return date && moment(date, dateFormat).isValid();
};

type DateEditorProps = Omit<DatepickerEditorProps, "dateFormat" | "expressionType" | "expressionObj">;

export const DateEditor = prepareEditor<DateEditorProps>(
    (props) => {
        const { formatter } = props;
        const dateFormatter = formatter == null ? typeFormatters[FormatterType.Date] : formatter;

        return (
            <DatepickerEditor
                {...props}
                momentFormat={dateFormat}
                dateFormat={dateFormat}
                timeFormat={null}
                formatter={dateFormatter}
                language={editorsParameters[EditorType.DATE].language}
            />
        );
    },
    {
        isSwitchableTo: (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression),
        notSwitchableToHint: () =>
            i18next.t("editors.LocalDate.notSwitchableToHint", "Expression must be valid date to switch to {{editorName}} mode", {
                editorName: editorsParameters[EditorType.DATE].displayName,
            }),
    },
);
