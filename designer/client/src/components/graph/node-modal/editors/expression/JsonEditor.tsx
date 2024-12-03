import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useState } from "react";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import { FieldError } from "../Validators";
import AceEditor from "./ace";
import { DEFAULT_OPTIONS } from "./AceWrapper";
import { SimpleEditor } from "./Editor";
import { ExpressionObj } from "./types";

type Props = {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    className: string;
    showValidation?: boolean;
    fieldErrors: FieldError[];
    fieldName: string;
    readOnly?: boolean;
    isMarked?: boolean;
};

export const JsonEditor: SimpleEditor<Props> = ({
    onValueChange,
    className,
    expressionObj,
    fieldErrors,
    showValidation,
    readOnly,
    isMarked,
}: Props) => {
    const [value, setValue] = useState(expressionObj.expression.replace(/^["'](.*)["']$/, ""));

    const onChange = (newValue: string) => {
        setValue(newValue);

        onValueChange(newValue);
    };

    const THEME = "nussknacker";

    return (
        <Box className={cx(nodeValue, className)} sx={{ width: "100%" }}>
            <Box
                className={cx([
                    rowAceEditor,
                    showValidation && !isEmpty(fieldErrors) && nodeInputWithError,
                    isMarked && "marked",
                    readOnly && "read-only",
                ])}
                sx={{ position: "relative" }}
            >
                <AceEditor
                    readOnly={readOnly}
                    mode={"json"}
                    width={"100%"}
                    minLines={5}
                    maxLines={50}
                    theme={THEME}
                    onChange={onChange}
                    value={value}
                    showPrintMargin={false}
                    cursorStart={-1} //line start
                    wrapEnabled={true}
                    showGutter={true}
                    setOptions={{
                        ...DEFAULT_OPTIONS,
                        enableLiveAutocompletion: false,
                        enableBasicAutocompletion: false,
                        showLineNumbers: true,
                        tabSize: 2,
                    }}
                />
            </Box>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
        </Box>
    );
};

export default JsonEditor;
