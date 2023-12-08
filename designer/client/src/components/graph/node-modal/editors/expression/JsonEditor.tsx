import React, { useState } from "react";

import AceEditor from "./ace";
import { ExpressionObj } from "./types";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { SimpleEditor } from "./Editor";
import { FieldError } from "../Validators";

type Props = {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    className: string;
    showValidation: boolean;
    fieldError: FieldError;
    fieldName: string;
};

export const JsonEditor: SimpleEditor<Props> = ({ onValueChange, className, expressionObj, fieldError, showValidation }: Props) => {
    const [value, setValue] = useState(expressionObj.expression.replace(/^["'](.*)["']$/, ""));

    const onChange = (newValue: string) => {
        setValue(newValue);

        onValueChange(newValue);
    };

    const THEME = "nussknacker";

    return (
        <div className={className}>
            <AceEditor
                mode={"json"}
                width={"100%"}
                minLines={5}
                maxLines={50}
                theme={THEME}
                onChange={onChange}
                value={value}
                showPrintMargin={false}
                cursorStart={-1} //line start
                showGutter={true}
                highlightActiveLine={true}
                wrapEnabled={true}
                setOptions={{
                    indentedSoftWrap: false, //removes weird spaces for multiline strings when wrapEnabled=true
                    enableLiveAutocompletion: false,
                    enableSnippets: false,
                    showLineNumbers: true,
                    fontSize: 16,
                    enableBasicAutocompletion: false,
                    tabSize: 2,
                }}
            />
            {showValidation && <ValidationLabels fieldError={fieldError} />}
        </div>
    );
};

export default JsonEditor;
