import React from "react";
import { Formatter, FormatterType, typeFormatters } from "./Formatter";
import { editors, EditorType, ExtendedEditor, OnValueChange } from "./Editor";
import i18next from "i18next";
import { Textarea } from "../field/Textarea";
import { ExpressionLang } from "./types";
import { FieldError } from "../Validators";

type Props = {
    expressionObj: $TodoType;
    onValueChange: OnValueChange;
    onFocus?: () => void;
    className?: string;
    inputClassName?: string;
    formatter?: Formatter;
    fieldErrors: FieldError[];
    showValidation: boolean;
    isMarked?: boolean;
    autoFocus?: boolean;
    placeholder?: string;
    readOnly?: boolean;
    type?: EditorType;
};

export const TextareaEditor: ExtendedEditor<Props> = ({
    expressionObj,
    onValueChange,
    onFocus,
    className,
    inputClassName,
    formatter,
    isMarked,
    autoFocus,
    fieldErrors,
    showValidation,
    placeholder,
    readOnly,
    type,
}: Props) => {
    const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter;

    return (
        <Textarea
            isMarked={isMarked}
            fieldErrors={fieldErrors}
            showValidation={showValidation}
            onChange={(event) =>
                onValueChange({
                    expression: stringFormatter.encode(event.target.value),
                    language: editors.TextareaParameterEditor.language,
                })
            }
            value={stringFormatter.decode(expressionObj.expression) as string}
            formattedValue={expressionObj.expression}
            className={className}
            autoFocus={autoFocus}
            inputClassName={inputClassName}
            onFocus={onFocus}
            placeholder={placeholder}
            readOnly={readOnly}
            type={type}
        />
    );
};

//TODO handle expressions with escaped '/"
const stringPattern = /(^'.*'$)|(^".*"$)/;

const parseable = (expressionObj) => {
    const expression = expressionObj.expression;
    const language = expressionObj.language;
    return stringPattern.test(expression) && language === ExpressionLang.SpEL;
};

TextareaEditor.isSwitchableTo = (expressionObj) => parseable(expressionObj);
TextareaEditor.switchableToHint = () => i18next.t("editors.textarea.switchableToHint", "Switch to basic mode");
TextareaEditor.notSwitchableToHint = () =>
    i18next.t(
        "editors.textarea.notSwitchableToHint",
        "Expression must be a string literal i.e. text surrounded by quotation marks to switch to basic mode",
    );
