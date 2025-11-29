import React from "react";

import Input from "../field/Input";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { Formatter } from "./Formatter";
import { FormatterType, typeFormatters } from "./Formatter";
import { EditorType } from "./types";

type Props = {
    className: string;
    formatter: Formatter;
};

export const StaticStringEditor = prepareEditor<Props>((props) => {
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
