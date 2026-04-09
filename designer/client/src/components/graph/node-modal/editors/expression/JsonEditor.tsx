import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { useUserSettings } from "../../../../../common/useUserSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { ParamType } from "../types";
import { setupAceEditorSnippets } from "./AceEditorJsonBasedSnippets";
import AceWithSettings from "./AceWithSettings";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import type { ExpressionObj } from "./types";
import { EditorType, ExpressionLang } from "./types";
import { useAceEditorAdornment } from "./useAceEditorAdornment";
import { useAceEditorRangeMessages } from "./useAceEditorRangeMessages";

type JsonEditorProps = {
    fieldName: string;
    isMarked?: boolean;
    param?: ParamType;
    defaultValue?: ExpressionObj | string;
};

export const JsonEditor = prepareEditor<JsonEditorProps>(
    ({ onValueChange, className, expressionObj, fieldErrors, showValidation, readOnly, isMarked, defaultValue }) => {
        const storedExpression = useMemo(() => expressionObj.expression.replace(/^["'](.*)["']$/, ""), [expressionObj.expression]);
        const [value, setValue] = useState(storedExpression);
        useEffect(() => {
            setValue(storedExpression);
        }, [storedExpression]);

        const language = ExpressionLang.JSON;
        const [showLines] = useUserSettings(`editor.${language}.showLines`);
        const { annotations, markers, hasRangeText } = useAceEditorRangeMessages(fieldErrors, showLines);

        const onChange = useCallback(
            (newValue: string) => {
                setValue(newValue);
                onValueChange({ expression: newValue, language: editorsParameters[EditorType.JSON_PARAMETER_EDITOR].language });
            },
            [onValueChange],
        );

        const { editorRef, maxLines, resetToDefaultButton, fullscreenButton } = useAceEditorAdornment({
            value,
            defaultValue,
            readOnly,
            onChange,
        });
        const InputAdornmentEnd =
            resetToDefaultButton || fullscreenButton ? (
                <>
                    {resetToDefaultButton}
                    {fullscreenButton}
                </>
            ) : undefined;

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
                    <AceWithSettings
                        ref={editorRef}
                        onLoad={(editor) => {
                            setupAceEditorSnippets(editor);
                        }}
                        onChange={onChange}
                        value={value}
                        inputProps={{
                            language,
                            readOnly,
                            InputAdornmentEnd,
                            rows: 5,
                            maxLines,
                        }}
                        enableLiveAutocompletion={false}
                        annotations={annotations}
                        markers={markers}
                        fieldErrors={fieldErrors}
                    />
                </Box>
                {showValidation && !hasRangeText && <ValidationLabels fieldErrors={fieldErrors} />}
            </Box>
        );
    },
);
