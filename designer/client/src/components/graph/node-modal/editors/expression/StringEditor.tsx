import React from "react";
import Input from "../field/Input";
import { ExtendedEditor } from "./Editor";
import { Formatter, FormatterType, typeFormatters } from "./Formatter";
import i18next from "i18next";
import { ExpressionLang, ExpressionObj } from "./types";
import { isQuoted } from "./SpelQuotesUtils";
import { Validator } from "../Validators";

type Props = {
    expressionObj: $TodoType;
    onValueChange: (value: string) => void;
    className: string;
    formatter: Formatter;
    validators: Validator[];
    showValidation: boolean;
};
const splitConcats = (value: string) => {
    return value.split(/\s*\+\s*/gm);
};
export const StringEditor: ExtendedEditor<Props> = (props: Props) => {
    const { expressionObj, onValueChange, formatter, ...passProps } = props;
    const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter;

    return (
        <Input
            {...passProps}
            onChange={(event) => onValueChange(stringFormatter.encode(event.target.value))}
            value={stringFormatter.decode(expressionObj.expression) as string}
            formattedValue={expressionObj.expression}
        />
    );
};

export const isSwitchableTo = ({ expression, language }: ExpressionObj): boolean => {
    return language === ExpressionLang.SpEL && splitConcats(expression.trim()).some(isQuoted);
};

StringEditor.isSwitchableTo = isSwitchableTo;
StringEditor.switchableToHint = () => i18next.t("editors.string.switchableToHint", "Switch to basic mode");
StringEditor.notSwitchableToHint = () =>
    i18next.t(
        "editors.string.notSwitchableToHint",
        "Expression must be a string literal i.e. text surrounded by quotation marks to switch to basic mode",
    );
