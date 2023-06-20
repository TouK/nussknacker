import React from "react";
import Input from "../field/Input";
import { SimpleEditor } from "./Editor";
import { Formatter, FormatterType, typeFormatters } from "./Formatter";
import i18next from "i18next";
import { ExpressionLang, ExpressionObj } from "./types";
import { isQuoted } from "./SpelQuotesUtils";
import { splitConcats } from "./TemplatesUtils";

type Props = {
    expressionObj: $TodoType;
    onValueChange: (value: string) => void;
    className: string;
    formatter: Formatter;
};

const StringEditor: SimpleEditor<Props> = (props: Props) => {
    const { expressionObj, onValueChange, formatter, ...passProps } = props;
    const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter;

    return (
        <Input
            {...passProps}
            onChange={(event) => onValueChange(stringFormatter.encode(event.target.value))}
            value={stringFormatter.decode(expressionObj.expression)}
            formattedValue={expressionObj.expression}
        />
    );
};

export const switchableTo = ({ expression, language }: ExpressionObj) => {
    return language === ExpressionLang.SpEL && splitConcats(expression.trim()).some(isQuoted);
};

StringEditor.switchableTo = switchableTo;
StringEditor.switchableToHint = () => i18next.t("editors.string.switchableToHint", "Switch to basic mode");
StringEditor.notSwitchableToHint = () =>
    i18next.t(
        "editors.string.notSwitchableToHint",
        "Expression must be a string literal i.e. text surrounded by quotation marks to switch to basic mode",
    );

export default StringEditor;
