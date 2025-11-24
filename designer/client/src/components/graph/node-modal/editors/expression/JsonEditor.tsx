import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { ParamType } from "../types";
import type { FieldError } from "../Validators";
import { setupAceEditorSnippets } from "./AceEditorJsonBasedSnippets";
import { DEFAULT_COMMANDS, DEFAULT_OPTIONS } from "./AceWrapper";
import type { OnValueChange, SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { ResetToDefaultButton } from "./ResetToDefaultButton";
import { StyledAceEditor } from "./StyledAceEditor";
import type { ExpressionObj } from "./types";
import { ExpressionLang } from "./types";
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
    param?: ParamType;
    defaultValue?: ExpressionObj | string;
};

export const JsonEditor: SimpleEditor<Props> = ({
    onValueChange,
    className,
    expressionObj,
    fieldErrors,
    showValidation,
    readOnly,
    isMarked,
    defaultValue,
}: Props) => {
    const storedExpression = useMemo(() => expressionObj.expression.replace(/^["'](.*)["']$/, ""), [expressionObj.expression]);
    const [value, setValue] = useState(storedExpression);
    useEffect(() => {
        setValue(storedExpression);
    }, [storedExpression]);

    const { annotations, markers, hasRangeText, setAnnotationsOnLoad } = useAceEditorRangeMessages(fieldErrors);

    const onChange = useCallback(
        (newValue: string) => {
            setValue(newValue);

            onValueChange({ expression: newValue, language: editorsParameters.JsonParameterEditor.language });
        },
        [onValueChange],
    );

    const THEME = "nussknacker";

    const InputAdornmentEnd = useMemo(() => {
        if (!defaultValue) return;
        if (readOnly) return;

        const defaultValueObject =
            typeof defaultValue === "string" ? { expression: defaultValue, language: ExpressionLang.JSON } : defaultValue; // defaultValue can be a string in case of Properties
        if (defaultValueObject.expression === value) return;

        return <ResetToDefaultButton defaultValue={defaultValueObject} handleChange={onChange} />;
    }, [defaultValue, onChange, readOnly, value]);

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
                    onLoad={(editor) => {
                        setAnnotationsOnLoad();
                        setupAceEditorSnippets(editor);
                    }}
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
                    commands={DEFAULT_COMMANDS}
                    annotations={annotations}
                    markers={markers}
                />
                {InputAdornmentEnd && (
                    <Box
                        aria-label={"InputAdornmentEnd"}
                        sx={{
                            position: "absolute",
                            right: "8px",
                            top: "9px",
                        }}
                    >
                        {InputAdornmentEnd}
                    </Box>
                )}
            </Box>
            {showValidation && !hasRangeText && <ValidationLabels fieldErrors={fieldErrors} />}
        </Box>
    );
};
