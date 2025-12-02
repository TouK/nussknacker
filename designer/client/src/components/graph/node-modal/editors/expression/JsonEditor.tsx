import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { ParamType } from "../types";
import { setupAceEditorSnippets } from "./AceEditorJsonBasedSnippets";
import AceWithSettings from "./AceWithSettings";
import { prepareEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { ResetToDefaultButton } from "./ResetToDefaultButton";
import type { ExpressionObj } from "./types";
import { EditorType, ExpressionLang } from "./types";
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
        const settings = useAppSelector(getUserSettings);
        const showLines = Boolean(settings[`editor.${language}.showLines`]);
        const { annotations, markers, hasRangeText } = useAceEditorRangeMessages(fieldErrors, showLines);

        const onChange = useCallback(
            (newValue: string) => {
                setValue(newValue);
                onValueChange({ expression: newValue, language: editorsParameters[EditorType.JSON_PARAMETER_EDITOR].language });
            },
            [onValueChange],
        );

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
                    sx={{
                        position: "relative",
                    }}
                >
                    <AceWithSettings
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
