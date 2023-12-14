import i18next from "i18next";
import { ExpressionObj } from "../types";
import React from "react";
import { isEmpty } from "lodash";
import { DatepickerEditor, DatepickerEditorProps } from "./DatepickerEditor";
import { FormatterType, spelFormatters, typeFormatters } from "../Formatter";
import moment from "moment";
import { ExtendedEditor } from "../Editor";

const dateFormat = "YYYY-MM-DD";
const isParseable = (expression: ExpressionObj): boolean => {
    const date = spelFormatters[FormatterType.Date].decode(expression.expression);
    return date && moment(date, dateFormat).isValid();
};

type DateEditorProps = Omit<DatepickerEditorProps, "dateFormat" | "expressionType">;

export const DateEditor: ExtendedEditor<DateEditorProps> = (props: DateEditorProps) => {
    const { formatter } = props;
    const dateFormatter = formatter == null ? typeFormatters[FormatterType.Date] : formatter;

    return <DatepickerEditor {...props} momentFormat={dateFormat} dateFormat={dateFormat} timeFormat={null} formatter={dateFormatter} />;
};

DateEditor.switchableToHint = () => i18next.t("editors.LocalDate.switchableToHint", "Switch to basic mode");
DateEditor.notSwitchableToHint = () =>
    i18next.t("editors.LocalDate.notSwitchableToHint", "Expression must be valid date to switch to basic mode");
DateEditor.isSwitchableTo = (expressionObj: ExpressionObj) => isParseable(expressionObj) || isEmpty(expressionObj.expression);
