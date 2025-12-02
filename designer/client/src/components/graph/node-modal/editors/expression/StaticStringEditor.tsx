import React from "react";

import Input from "../field/Input";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { FormatterType, typeFormatters } from "./Formatter";
import { EditorType } from "./types";

export const StaticStringEditor = prepareEditor<unknown>((props) => {
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
});
