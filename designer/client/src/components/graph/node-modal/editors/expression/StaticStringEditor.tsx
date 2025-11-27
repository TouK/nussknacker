import React from "react";

import Input from "../field/Input";
import type { FieldError } from "../Validators";
import type { OnValueChange, SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { Formatter } from "./Formatter";
import { FormatterType, typeFormatters } from "./Formatter";
import type { ExpressionObj } from "./types";
import { EditorType } from "./types";

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
                    language: editorsParameters[EditorType.STATIC_STRING_PARAMETER_EDITOR].language,
                })
            }
            value={stringFormatter.decode(expressionObj.expression) as string}
        />
    );
};
