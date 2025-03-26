import React from "react";
import Input from "../field/Input";
import { OnValueChange, SimpleEditor } from "./Editor";
import { Formatter, FormatterType, typeFormatters } from "./Formatter";
import { ExpressionObj } from "./types";
import { FieldError } from "../Validators";
import { editorsParameters } from "./editorsParameters";

type Props = {
    expressionObj: ExpressionObj;
    onValueChange: OnValueChange;
    className: string;
    formatter: Formatter;
    fieldErrors: FieldError[];
    showValidation: boolean;
};

export const StaticStringEditor: SimpleEditor<Props> = (props: Props) => {
    const { expressionObj, onValueChange, formatter, ...passProps } = props;
    const stringFormatter = formatter == null ? typeFormatters[FormatterType.String] : formatter;

    return (
        <Input
            {...passProps}
            onChange={(event) =>
                onValueChange({
                    expression: stringFormatter.encode(event.target.value),
                    language: editorsParameters.StaticStringParameterEditor.language,
                })
            }
            value={stringFormatter.decode(expressionObj.expression) as string}
        />
    );
};
