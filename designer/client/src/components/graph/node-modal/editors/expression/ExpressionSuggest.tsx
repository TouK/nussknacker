import "ace-builds/src-noconflict/ace";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useSelector } from "react-redux";
import { getProcessDefinitionData } from "../../../../../reducers/selectors/settings";
import { getProcessingType } from "../../../../../reducers/selectors/graph";
import { BackendExpressionSuggester } from "./ExpressionSuggester";
import HttpService from "../../../../../http/HttpService";
import AceEditor from "./AceWithSettings";
import ValidationLabels from "../../../../modals/ValidationLabels";
import ReactAce from "react-ace/lib/ace";
import { EditorMode, ExpressionLang } from "./types";
import { SerializedStyles } from "@emotion/react";
import { CustomAceEditorCompleter } from "./CustomAceEditorCompleter";
import { cx } from "@emotion/css";
import { VariableTypes } from "../../../../../types";
import { FieldError } from "../Validators";
import { nodeInputWithError, rowAceEditor } from "../../NodeDetailsContent/NodeTableStyled";

interface InputProps {
    value: string;
    language: ExpressionLang | string;
    readOnly?: boolean;
    rows?: number;
    onValueChange: (value: string) => void;
    ref: React.Ref<ReactAce>;
    className?: string;
    style: SerializedStyles;
    cols: number;
    editorMode?: EditorMode;
}

interface Props {
    inputProps: InputProps;
    fieldErrors: FieldError[];
    validationLabelInfo: string;
    showValidation?: boolean;
    isMarked?: boolean;
    variableTypes: VariableTypes;
    editorMode?: EditorMode;
}

export function ExpressionSuggest(props: Props): JSX.Element {
    const { isMarked, showValidation, inputProps, fieldErrors, variableTypes, validationLabelInfo } = props;

    const definitionData = useSelector(getProcessDefinitionData);
    const dataResolved = !isEmpty(definitionData);
    const processingType = useSelector(getProcessingType);

    const { value, onValueChange, language } = inputProps;
    const [editorFocused, setEditorFocused] = useState(false);

    const expressionSuggester = useMemo(() => {
        return new BackendExpressionSuggester(language, variableTypes, processingType, HttpService);
    }, [processingType, variableTypes, language]);

    const [customAceEditorCompleter] = useState(() => new CustomAceEditorCompleter(expressionSuggester));
    useEffect(() => customAceEditorCompleter.replaceSuggester(expressionSuggester), [customAceEditorCompleter, expressionSuggester]);

    const onChange = useCallback((value: string) => onValueChange(value), [onValueChange]);
    const editorFocus = useCallback((editorFocused: boolean) => () => setEditorFocused(editorFocused), []);

    return dataResolved ? (
        <>
            <div
                className={cx([
                    rowAceEditor,
                    showValidation && !isEmpty(fieldErrors) && nodeInputWithError,
                    isMarked && "marked",
                    editorFocused && "focused",
                    inputProps.readOnly && "read-only",
                ])}
            >
                <AceEditor
                    ref={inputProps.ref}
                    value={value}
                    onChange={onChange}
                    onFocus={editorFocus(true)}
                    onBlur={editorFocus(false)}
                    inputProps={inputProps}
                    customAceEditorCompleter={customAceEditorCompleter}
                />
            </div>
            {showValidation && <ValidationLabels fieldErrors={fieldErrors} validationLabelInfo={validationLabelInfo} />}
        </>
    ) : null;
}
