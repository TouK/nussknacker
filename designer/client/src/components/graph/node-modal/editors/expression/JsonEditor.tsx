import { cx } from "@emotion/css";
import { Box } from "@mui/material";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../../store/storeHelpers";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { nodeInputWithError, nodeValue, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";
import type { ParamType } from "../types";
import type { FieldError } from "../Validators";
import { setupAceEditorSnippets } from "./AceEditorJsonBasedSnippets";
import AceWithSettings from "./AceWithSettings";
import type { OnValueChange, SimpleEditor } from "./Editor";
import { editorsParameters } from "./editorsParameters";
import { ResetToDefaultButton } from "./ResetToDefaultButton";
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

    const language = ExpressionLang.JSON;
    const settings = useAppSelector(getUserSettings);
    const showLines = Boolean(settings[`editor.${language}.showLines`]);
    const { annotations, markers, hasRangeText } = useAceEditorRangeMessages(fieldErrors, showLines);

    const onChange = useCallback(
        (newValue: string) => {
            setValue(newValue);
            onValueChange({ expression: newValue, language: editorsParameters.JsonParameterEditor.language });
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
};
