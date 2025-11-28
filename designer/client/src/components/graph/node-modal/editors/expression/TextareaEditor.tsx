import i18next from "i18next";
import React from "react";

import { Textarea } from "../field/Textarea";
import type { FieldError } from "../Validators";
import type { OnValueChange } from "./Editor";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { Formatter } from "./Formatter";
import { FormatterType, typeFormatters } from "./Formatter";
import { EditorType, ExpressionLang } from "./types";

type Props = {
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

export const TextareaEditor = prepareEditor<Props>(
    ({
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
    }) => {
        const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter;

        return (
            <Textarea
                isMarked={isMarked}
                fieldErrors={fieldErrors}
                showValidation={showValidation}
                onChange={(event) =>
                    onValueChange({
                        expression: stringFormatter.encode(event.target.value),
                        language: editorsParameters[EditorType.TEXTAREA_PARAMETER_EDITOR].language,
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
    },
    {
        isSwitchableTo: (expressionObj) => parseable(expressionObj),
        notSwitchableToHint: () =>
            i18next.t(
                "editors.textarea.notSwitchableToHint",
                "Expression must be a string literal i.e. text surrounded by quotation marks to switch to {{editorName}} mode",
                { editorName: editorsParameters[EditorType.TEXTAREA_PARAMETER_EDITOR].displayName },
            ),
    },
);

//TODO handle expressions with escaped '/"
const stringPattern = /(^'.*'$)|(^".*"$)/;

const parseable = (expressionObj) => {
    const expression = expressionObj.expression;
    const language = expressionObj.language;
    return stringPattern.test(expression) && language === ExpressionLang.SpEL;
};
