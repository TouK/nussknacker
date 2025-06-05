import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useState } from "react";

import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../Validators";
import AceEditor from "./ace";
import { DEFAULT_OPTIONS } from "./AceWrapper";
import type { OnValueChange, SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { StyledAceEditor } from "./StyledAceEditor";
import type { ExpressionObj } from "./types";
import { useAceEditorRangeMessages } from "./useAceEditorRangeMessages";

type Props = {
    expressionObj: ExpressionObj;
    onValueChange: OnValueChange;
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
    const { annotations, markers, hasRangeText, setAnnotationsOnLoad } = useAceEditorRangeMessages(fieldErrors);

    const onChange = (newValue: string) => {
        setValue(newValue);

        onValueChange({ expression: newValue, language: editorsParameters.JsonParameterEditor.language });
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
                <StyledAceEditor
                    onLoad={setAnnotationsOnLoad}
                    readOnly={readOnly}
                    mode={"json"}
                    width={"100%"}
                    minLines={5}
                    maxLines={50}
                    codeTheme={THEME}
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
                        // We don't want to check syntax correctness with ace
                        useWorker: false,
                    }}
                    annotations={annotations}
                    markers={markers}
                />
            </Box>
            {showValidation && !hasRangeText && <ValidationLabels fieldErrors={fieldErrors} />}
        </Box>
    );
};

export default JsonEditor;
